/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ConcurrentSoftHashMap;
import com.intellij.util.containers.ConcurrentSoftValueHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#" + BaseExternalAnnotationsManager.class.getName());
  @NotNull private static final List<PsiFile> NULL = new ArrayList<PsiFile>();
  @NotNull protected final ConcurrentMap<String, List<PsiFile>> myExternalAnnotations = new ConcurrentSoftValueHashMap<String, List<PsiFile>>();
  @NotNull private volatile ThreeState myHasAnyAnnotationsRoots = ThreeState.UNSURE;
  protected final PsiManager myPsiManager;

  public BaseExternalAnnotationsManager(final PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  @Nullable
  protected static String getExternalName(@NotNull PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }

  @Nullable
  protected static String getFQN(@NotNull String packageName, @NotNull PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    return StringUtil.getQualifiedName(packageName, virtualFile.getNameWithoutExtension());
  }

  @Nullable
  protected static String getNormalizedExternalName(@NotNull PsiModifierListOwner owner) {
    String externalName = getExternalName(owner, true);
    if (externalName != null) {
      if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(owner, PsiMethod.class);
        if (method != null) {
          externalName =
            externalName.substring(0, externalName.lastIndexOf(' ') + 1) + method.getParameterList().getParameterIndex((PsiParameter)owner);
        }
      }
      final int idx = externalName.indexOf('(');
      if (idx == -1) return externalName;
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        final int rightIdx = externalName.indexOf(')');
        final String[] params = externalName.substring(idx + 1, rightIdx).split(",");
        buf.append(externalName.substring(0, idx + 1));
        for (String param : params) {
          param = param.trim();
          final int spaceIdx = param.indexOf(' ');
          buf.append(spaceIdx > -1 ? param.substring(0, spaceIdx) : param).append(", ");
        }
        return StringUtil.trimEnd(buf.toString(), ", ") + externalName.substring(rightIdx);
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }
    return externalName;
  }

  protected boolean hasAnyAnnotationsRoots() {
    if (myHasAnyAnnotationsRoots == ThreeState.UNSURE) {
      final Module[] modules = ModuleManager.getInstance(myPsiManager.getProject()).getModules();
      for (Module module : modules) {
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          final String[] urls = AnnotationOrderRootType.getUrls(entry);
          if (urls.length > 0) {
            myHasAnyAnnotationsRoots = ThreeState.YES;
            return true;
          }
        }
      }
      myHasAnyAnnotationsRoots = ThreeState.NO;
    }
    return myHasAnyAnnotationsRoots == ThreeState.YES;
  }

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    return collectExternalAnnotations(listOwner).get(annotationFQN);
  }

  @Override
  public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    // note that this method doesn't cache it's result
    Map<String, PsiAnnotation> map = doCollect(listOwner, true);
    return map.containsKey(annotationFQN);
  }

  @Override
  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final Map<String, PsiAnnotation> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : result.values().toArray(new PsiAnnotation[result.size()]);
  }

  private final ConcurrentMap<PsiModifierListOwner, Map<String, PsiAnnotation>> cache = new ConcurrentWeakHashMap<PsiModifierListOwner, Map<String, PsiAnnotation>>();
  @NotNull
  private Map<String, PsiAnnotation> collectExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    if (!hasAnyAnnotationsRoots()) return Collections.emptyMap();

    Map<String, PsiAnnotation> map = cache.get(listOwner);
    if (map == null) {
      map = doCollect(listOwner, false);
      map = ConcurrencyUtil.cacheOrGet(cache, listOwner, map);
    }
    return map;
  }

  private static final MultiMap<String, AnnotationData> EMPTY = new MultiMap<String, AnnotationData>();
  private ConcurrentMap<PsiFile, Pair<MultiMap<String, AnnotationData>, Long>> annotationsFileToDataAndModificationStamp = new ConcurrentSoftHashMap<PsiFile, Pair<MultiMap<String, AnnotationData>, Long>>();
  @NotNull
  private MultiMap<String, AnnotationData> getDataFromFile(@NotNull PsiFile file) {
    Pair<MultiMap<String, AnnotationData>, Long> cached = annotationsFileToDataAndModificationStamp.get(file);
    if (cached != null && cached.getSecond() == file.getModificationStamp()) {
      return cached.getFirst();
    }
    Document document;
    try {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return EMPTY;
      document = JDOMUtil.loadDocument(escapeAttributes(StreamUtil.readText(virtualFile.getInputStream())));
    }
    catch (IOException e) {
      LOG.error(e);
      return EMPTY;
    }
    catch (JDOMException e) {
      LOG.error(e);
      return EMPTY;
    }
    Element rootElement = document.getRootElement();
    if (rootElement == null) return EMPTY;

    MultiMap<String, AnnotationData> data = new MultiMap<String, AnnotationData>();

    //noinspection unchecked
    for (Element element : (List<Element>) rootElement.getChildren()) {
      String ownerName = element.getAttributeValue("name");
      if (ownerName == null) continue;
      //noinspection unchecked
      for (Element annotationElement : (List<Element>) element.getChildren()) {
        String annotationFQN = annotationElement.getAttributeValue("name");
        if (StringUtil.isEmpty(annotationFQN)) continue;
        StringBuilder buf = new StringBuilder();
        //noinspection unchecked
        for (Element annotationParameter : (List<Element>) annotationElement.getChildren()) {
          buf.append(",");
          String nameValue = annotationParameter.getAttributeValue("name");
          if (nameValue != null) {
            buf.append(nameValue).append("=");
          }
          buf.append(annotationParameter.getAttributeValue("val"));
        }
        String annotationText = "@" + annotationFQN + (buf.length() > 0 ? "(" + StringUtil.trimStart(buf.toString(), ",") + ")" : "");
        data.putValue(ownerName, new AnnotationData(annotationFQN, annotationText));
      }
    }

    Pair<MultiMap<String, AnnotationData>, Long> pair = Pair.create(data, file.getModificationStamp());
    pair = ConcurrencyUtil.cacheOrGet(annotationsFileToDataAndModificationStamp, file, pair);
    data = pair.first;

    return data;
  }

  @NotNull
  private Map<String, PsiAnnotation> doCollect(@NotNull PsiModifierListOwner listOwner, boolean onlyWritable) {
    final List<PsiFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) {
      return Collections.emptyMap();
    }
    Map<String, PsiAnnotation> result = new THashMap<String, PsiAnnotation>();
    String externalName = getExternalName(listOwner, false);
    String oldExternalName = getNormalizedExternalName(listOwner);

    final PsiElementFactory factory = JavaPsiFacade.getInstance(myPsiManager.getProject()).getElementFactory();
    for (PsiFile file : files) {
      if (!file.isValid()) continue;
      if (onlyWritable && !file.isWritable()) continue;

      final MultiMap<String, AnnotationData> fileData = getDataFromFile(file);
      
      collectAnnotations(result, fileData.get(externalName), factory);
      collectAnnotations(result, fileData.get(oldExternalName), factory);
    }
    return result;
  }

  private static void collectAnnotations(Map<String, PsiAnnotation> result,
                                         Collection<AnnotationData> dataCollection,
                                         PsiElementFactory factory) {
    for (AnnotationData annotationData : dataCollection) {
      // don't add annotation, if there already is one with this FQ name
      if (result.containsKey(annotationData.annotationClassFqName)) continue;

      try {
        PsiAnnotation annotation = factory.createAnnotationFromText(annotationData.annotationText, null);
        result.put(annotationData.annotationClassFqName, annotation);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  protected List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile) {
    final List<OrderEntry> entries = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex().getOrderEntriesForFile(
      libraryFile);
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) {
        continue;
      }
      final String[] externalUrls = AnnotationOrderRootType.getUrls(entry);
      for (String url : externalUrls) {
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
        if (root != null) {
          result.add(root);
        }
      }
    }
    return result;
  }

  @Override
  @Nullable
  public List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner) {
    final PsiFile containingFile = listOwner.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return null;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
    final String packageName = javaFile.getPackageName();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    String fqn = getFQN(packageName, containingFile);
    if (fqn == null) return null;
    final List<PsiFile> files = myExternalAnnotations.get(fqn);
    if (files == NULL) return null;
    if (files != null) {
      boolean allValid = true;
      for (PsiFile file : files) {
        allValid &= file.isValid();
      }
      if (allValid) {
        return files;
      }
    }

    if (virtualFile == null) {
      return null;
    }

    ArrayList<PsiFile> possibleAnnotationsXmls = new ArrayList<PsiFile>();
    for (VirtualFile root : getExternalAnnotationsRoots(virtualFile)) {
      final VirtualFile ext = root.findFileByRelativePath(packageName.replace(".", "/") + "/" + ANNOTATIONS_XML);
      if (ext == null) continue;
      final PsiFile psiFile = myPsiManager.findFile(ext);
      if (psiFile == null) continue;
      possibleAnnotationsXmls.add(psiFile);
    }
    possibleAnnotationsXmls.trimToSize();
    if (!possibleAnnotationsXmls.isEmpty()) {
      // sorting by writability: writable go first
      Collections.sort(possibleAnnotationsXmls, new Comparator<PsiFile>() {
        @Override
        public int compare(PsiFile f1, PsiFile f2) {
          boolean w1 = f1.isWritable();
          boolean w2 = f2.isWritable();
          if (w1 == w2) {
            return 0;
          }
          return w1 ? -1 : 1;
        }
      });

      myExternalAnnotations.put(fqn, possibleAnnotationsXmls);
      return possibleAnnotationsXmls;
    }
    myExternalAnnotations.put(fqn, NULL);
    return null;
  }

  protected void dropCache() {
    myExternalAnnotations.clear();
    annotationsFileToDataAndModificationStamp.clear();
    myHasAnyAnnotationsRoots = ThreeState.UNSURE;
    cache.clear();
  }

  // This method is used for legacy reasons.
  // Old external annotations sometimes are bad XML: they have "<" and ">" characters in attributes values. To prevent SAX parser from
  // failing, we escape attributes values.
  @NotNull
  private static String escapeAttributes(@NotNull String invalidXml) {
    // We assume that XML has single- and double-quote characters only for attribute values, therefore we don't any complex parsing,
    // just have binary inAttribute state
    StringBuilder buf = new StringBuilder();
    boolean inAttribute = false;
    for (int i = 0; i < invalidXml.length(); i++) {
      char c = invalidXml.charAt(i);
      if (inAttribute && c == '<') {
        buf.append("&lt;");
      }
      else if (inAttribute && c == '>') {
        buf.append("&gt;");
      }
      else if (c == '\"' || c == '\'') {
        buf.append('\"');
        inAttribute = !inAttribute;
      }
      else {
        buf.append(c);
      }
    }
    return buf.toString();
  }

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  private static class AnnotationData {
    @NotNull public String annotationClassFqName;
    @NotNull public String annotationText;

    private AnnotationData(@NotNull String annotationClassFqName, @NotNull String annotationText) {
      this.annotationClassFqName = annotationClassFqName;
      this.annotationText = annotationText;
    }
  }
}
