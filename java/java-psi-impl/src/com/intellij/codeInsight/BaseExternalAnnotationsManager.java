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

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.*;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public abstract class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.BaseExternalAnnotationsManager");
  @NotNull private static final List<PsiFile> NULL_LIST = new ArrayList<PsiFile>(0);
  @NotNull
  private final ConcurrentMap<String, List<PsiFile>> myExternalAnnotations = new ConcurrentSoftValueHashMap<String, List<PsiFile>>(10, 0.75f, 2);
  protected final PsiManager myPsiManager;

  public BaseExternalAnnotationsManager(final PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  @Nullable
  protected static String getExternalName(@NotNull PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }

  @Nullable
  private static String getFQN(@NotNull String packageName, @NotNull PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    return StringUtil.getQualifiedName(packageName, virtualFile.getNameWithoutExtension());
  }

  @Nullable
  protected static String getNormalizedExternalName(@NotNull PsiModifierListOwner owner) {
    String externalName = getExternalName(owner, true);
    if (externalName == null) {
      return null;
    }
    if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(owner, PsiMethod.class);
      if (method != null) {
        externalName =
          externalName.substring(0, externalName.lastIndexOf(' ') + 1) + method.getParameterList().getParameterIndex((PsiParameter)owner);
      }
    }
    final int idx = externalName.indexOf('(');
    if (idx == -1) return externalName;
    StringBuilder buf = new StringBuilder(externalName.length());
    int rightIdx = externalName.indexOf(')');
    String[] params = externalName.substring(idx + 1, rightIdx).split(",");
    buf.append(externalName, 0, idx + 1);
    for (String param : params) {
      param = param.trim();
      int spaceIdx = param.indexOf(' ');
      if (spaceIdx > -1) {
        buf.append(param, 0, spaceIdx);
      }
      else {
        buf.append(param);
      }
      buf.append(", ");
    }
    if (StringUtil.endsWith(buf, ", ")) {
      buf.delete(buf.length() - ", ".length(), buf.length());
    }
    buf.append(externalName, rightIdx, externalName.length());
    return buf.toString();
  }

  protected abstract boolean hasAnyAnnotationsRoots();

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    List<AnnotationData> list = collectExternalAnnotations(listOwner);
    AnnotationData data = findByFQN(list, annotationFQN);
    return data == null ? null : data.getAnnotation();
  }

  @Override
  public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    // note that this method doesn't cache it's result
    List<AnnotationData> map = doCollect(listOwner, true);
    return findByFQN(map, annotationFQN) != null;
  }

  private static AnnotationData findByFQN(@NotNull List<AnnotationData> map, @NotNull final String annotationFQN) {
    return ContainerUtil.find(map, new Condition<AnnotationData>() {
      @Override
      public boolean value(AnnotationData data) {
        return data.annotationClassFqName.equals(annotationFQN);
      }
    });
  }

  @Override
  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final List<AnnotationData> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY, new Function<AnnotationData, PsiAnnotation>() {
      @Override
      public PsiAnnotation fun(AnnotationData data) {
        return data.getAnnotation();
      }
    });
  }

  private static final List<AnnotationData> NO_DATA = new ArrayList<AnnotationData>(1);
  private final ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData> cache = new ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData>();
  private final CharTableImpl charTable = new CharTableImpl();
  @NotNull
  private List<AnnotationData> collectExternalAnnotations(@NotNull PsiModifierListOwner listOwner) {
    if (!hasAnyAnnotationsRoots()) return Collections.emptyList();

    List<AnnotationData> cached;
    while (true) {
      cached = (List<AnnotationData>)cache.get(listOwner);
      if (cached == NO_DATA || !cached.isEmpty()) return cached;
      List<AnnotationData> computed = doCollect(listOwner, false);
      if (cache.replace(listOwner, cached, computed)) {
        cached = computed;
        break;
      }
    }
    return cached;
  }

  private final Map<AnnotationData, AnnotationData> annotationDataCache = new WeakKeyWeakValueHashMap<AnnotationData, AnnotationData>();
  @NotNull
  private AnnotationData internAnnotationData(@NotNull AnnotationData data) {
    synchronized (annotationDataCache) {
      AnnotationData interned = annotationDataCache.get(data);
      if (interned == null) {
        annotationDataCache.put(data, data);
        interned = data;
      }
      return interned;
    }
  }


  private final ConcurrentMap<PsiFile, Pair<MostlySingularMultiMap<String, AnnotationData>, Long>> annotationFileToDataAndModStamp = new ConcurrentSoftHashMap<PsiFile, Pair<MostlySingularMultiMap<String, AnnotationData>, Long>>();

  @NotNull
  private MostlySingularMultiMap<String, AnnotationData> getDataFromFile(@NotNull PsiFile file) {
    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> cached = annotationFileToDataAndModStamp.get(file);
    if (cached != null && cached.getSecond() == file.getModificationStamp()) {
      return cached.getFirst();
    }
    MostlySingularMultiMap<String, AnnotationData> data = new MostlySingularMultiMap<String, AnnotationData>();
    try {
      Document document = JDOMUtil.loadDocument(escapeAttributes(file.getText()));
      Element rootElement = document.getRootElement();
      if (rootElement != null) {
        boolean sorted = true;
        boolean modified = false;
        String prevItemName = null;
        //noinspection unchecked
        for (Element element : (List<Element>) rootElement.getChildren("item")) {
          String externalName = element.getAttributeValue("name");
          if (externalName == null) {
            element.detach();
            modified = true;
            continue;
          }
          if (prevItemName != null && prevItemName.compareTo(externalName) > 0) {
            sorted = false;
          }
          prevItemName = externalName;

          //noinspection unchecked
          for (Element annotationElement : (List<Element>) element.getChildren("annotation")) {
            String annotationFQN = annotationElement.getAttributeValue("name");
            if (StringUtil.isEmpty(annotationFQN)) continue;
            annotationFQN = intern(annotationFQN);
            //noinspection unchecked
            List<Element> children = (List<Element>)annotationElement.getChildren();
            StringBuilder buf = new StringBuilder(children.size() * "name=value,".length()); // just guess
            for (Element annotationParameter : children) {
              if (buf.length() != 0) {
                buf.append(",");
              }
              String nameValue = annotationParameter.getAttributeValue("name");
              if (nameValue != null) {
                buf.append(nameValue);
                buf.append("=");
              }
              buf.append(annotationParameter.getAttributeValue("val"));
            }
            String annotationParameters = buf.length() == 0 ? "" : intern(buf.toString());
            for (AnnotationData existingData : data.get(externalName)) {
              if (existingData.annotationClassFqName.equals(annotationFQN)) {
                LOG.error("Duplicate annotation '" + annotationFQN+"' for signature: '" + externalName + "' in the file " + file.getVirtualFile().getPresentableUrl());
              }
            }
            AnnotationData annData = internAnnotationData(new AnnotationData(annotationFQN, annotationParameters));

            data.add(externalName, annData);
          }
        }
        if (!sorted) {
          modified = true;
          List<Element> items = new ArrayList<Element>(rootElement.getChildren("item"));
          rootElement.removeChildren("item");
          Collections.sort(items, new Comparator<Element>() {
            @Override
            public int compare(Element item1, Element item2) {
              String externalName1 = item1.getAttributeValue("name");
              String externalName2 = item2.getAttributeValue("name");
              return externalName1.compareTo(externalName2);
            }
          });
          for (Element item : items) {
            rootElement.addContent(item);
          }
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (modified && virtualFile.isInLocalFileSystem() && virtualFile.isWritable()) {
          String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, file.getProject());
          JDOMUtil.writeDocument(document, virtualFile.getPath(), lineSeparator);
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
    if (data.isEmpty()) {
      data = MostlySingularMultiMap.emptyMap();
    }
    data.compact();
    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> pair = Pair.create(data, file.getModificationStamp());
    annotationFileToDataAndModStamp.put(file, pair);

    return data;
  }

  @NotNull
  private String intern(@NotNull String annotationFQN) {
    return charTable.doIntern(annotationFQN).toString();
  }

  @NotNull
  private List<AnnotationData> doCollect(@NotNull PsiModifierListOwner listOwner, boolean onlyWritable) {
    final List<PsiFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) {
      return NO_DATA;
    }
    SmartList<AnnotationData> result = new SmartList<AnnotationData>();
    String externalName = getExternalName(listOwner, false);
    if (externalName == null) return NO_DATA;
    String oldExternalName = getNormalizedExternalName(listOwner);

    for (PsiFile file : files) {
      if (!file.isValid()) continue;
      if (onlyWritable && !file.isWritable()) continue;

      MostlySingularMultiMap<String, AnnotationData> fileData = getDataFromFile(file);

      Collection<AnnotationData> data = (Collection<AnnotationData>)fileData.get(externalName);
      for (AnnotationData ad : data) {
        if (result.contains(ad)) {
          LOG.error("Duplicate signature:\n" + externalName + "; in  " + toVirtualFiles(files));
        }
        else {
          result.add(ad);
        }
      }
      if (oldExternalName != null && !externalName.equals(oldExternalName)) {
        Collection<AnnotationData> oldCollection = (Collection<AnnotationData>)fileData.get(oldExternalName);
        for (AnnotationData ad : oldCollection) {
          if (result.contains(ad)) {
            LOG.error("Duplicate signature o:\n" + oldExternalName + "; in  " + toVirtualFiles(files));
          }
          else {
            result.add(ad);
          }
        }
      }
    }
    if (result.isEmpty()) {
      return NO_DATA;
    }
    result.trimToSize();
    return result;
  }

  static List<VirtualFile> toVirtualFiles(List<PsiFile> files) {
    return ContainerUtil.map(files, new Function<PsiFile, VirtualFile>() {
      @Override
      public VirtualFile fun(PsiFile file) {
        return file.getVirtualFile();
      }
    });
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
    if (files == NULL_LIST) return null;
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

    Set<PsiFile> possibleAnnotationsXmls = new THashSet<PsiFile>();
    for (VirtualFile root : getExternalAnnotationsRoots(virtualFile)) {
      final VirtualFile ext = root.findFileByRelativePath(packageName.replace(".", "/") + "/" + ANNOTATIONS_XML);
      if (ext == null) continue;
      final PsiFile psiFile = myPsiManager.findFile(ext);
      if (psiFile == null) continue;
      possibleAnnotationsXmls.add(psiFile);
    }
    List<PsiFile> result;
    if (possibleAnnotationsXmls.isEmpty()) {
      myExternalAnnotations.put(fqn, NULL_LIST);
      result = null;
    }
    else {
      result = new SmartList<PsiFile>(possibleAnnotationsXmls);
      // sorting by writability: writable go first
      Collections.sort(result, new Comparator<PsiFile>() {
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

      myExternalAnnotations.put(fqn, result);
    }
    return result;
  }

  @NotNull
  protected abstract List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile);

  protected void dropCache() {
    myExternalAnnotations.clear();
    annotationFileToDataAndModStamp.clear();
    cache.clear();
  }

  // This method is used for legacy reasons.
  // Old external annotations sometimes are bad XML: they have "<" and ">" characters in attributes values. To prevent SAX parser from
  // failing, we escape attributes values.
  @NotNull
  private static String escapeAttributes(@NotNull String invalidXml) {
    // We assume that XML has single- and double-quote characters only for attribute values, therefore we don't any complex parsing,
    // just have binary inAttribute state
    StringBuilder buf = new StringBuilder(invalidXml.length());
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

  protected void cacheExternalAnnotations(@NotNull String packageName, @NotNull PsiFile fromFile, @NotNull List<PsiFile> annotationFiles) {
    String fqn = getFQN(packageName, fromFile);
    if (fqn != null) {
      myExternalAnnotations.put(fqn, annotationFiles);
    }
  }

  private class AnnotationData {
    @NotNull private final String annotationClassFqName;
    @NotNull private final String annotationParameters;
    private PsiAnnotation annotation;

    private AnnotationData(@NotNull String annotationClassFqName, @NotNull String annotationParameters) {
      this.annotationClassFqName = annotationClassFqName;
      this.annotationParameters = annotationParameters;
    }

    @NotNull
    private PsiAnnotation getAnnotation() {
      PsiAnnotation a = annotation;
      if (a == null) {
        annotation = a = createAnnotationFromText("@" + annotationClassFqName + (annotationParameters.isEmpty() ? "" : "("+annotationParameters+")"));
      }
      return a;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AnnotationData data = (AnnotationData)o;

      return annotationClassFqName.equals(data.annotationClassFqName) && annotationParameters.equals(data.annotationParameters);
    }

    @Override
    public int hashCode() {
      int result = annotationClassFqName.hashCode();
      result = 31 * result + annotationParameters.hashCode();
      return result;
    }
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager, new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null, charTable);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
    }
    return (PsiAnnotation)element;
  }
  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };
}
