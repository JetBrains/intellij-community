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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ConcurrentWeakValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public abstract class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#" + BaseExternalAnnotationsManager.class.getName());
  @NotNull private static final List<XmlFile> NULL = new ArrayList<XmlFile>();
  @NotNull protected final ConcurrentMap<String, List<XmlFile>> myExternalAnnotations = new ConcurrentWeakValueHashMap<String, List<XmlFile>>();
  protected final PsiManager myPsiManager;

  public BaseExternalAnnotationsManager(final PsiManager psiManager) {
    myPsiManager = psiManager;
  }

  @Nullable
  protected static String getExternalName(PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }

  @Nullable
  protected static String getFQN(String packageName, @Nullable VirtualFile virtualFile) {
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

  protected abstract boolean hasAnyAnnotationsRoots();

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    return collectExternalAnnotations(listOwner).get(annotationFQN);
  }

  @Override
  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final Map<String, PsiAnnotation> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : result.values().toArray(new PsiAnnotation[result.size()]);
  }

  private final Map<PsiModifierListOwner, Map<String, PsiAnnotation>> cache = new ConcurrentWeakHashMap<PsiModifierListOwner, Map<String, PsiAnnotation>>();
  @NotNull
  private Map<String, PsiAnnotation> collectExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    if (!hasAnyAnnotationsRoots()) return Collections.emptyMap();

    Map<String, PsiAnnotation> map = cache.get(listOwner);
    if (map == null) {
      map = doCollect(listOwner);
      cache.put(listOwner, map);
    }
    return map;
  }

  private Map<String, PsiAnnotation> doCollect(@NotNull PsiModifierListOwner listOwner) {
    final List<XmlFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) {
      return Collections.emptyMap();
    }
    final Map<String, PsiAnnotation> result = new HashMap<String, PsiAnnotation>();
    for (XmlFile file : files) {
      if (!file.isValid()) continue;
      final XmlDocument document = file.getDocument();
      if (document == null) continue;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) continue;
      final String externalName = getExternalName(listOwner, false);
      final String oldExternalName = getNormalizedExternalName(listOwner);
      for (final XmlTag tag : rootTag.getSubTags()) {
        final String className = StringUtil.unescapeXml(tag.getAttributeValue("name"));
        if (!Comparing.strEqual(className, externalName) && !Comparing.strEqual(className, oldExternalName)) {
          continue;
        }
        for (XmlTag annotationTag : tag.getSubTags()) {
          final String annotationFQN = annotationTag.getAttributeValue("name");
          final StringBuilder buf = new StringBuilder();
          for (XmlTag annotationParameter : annotationTag.getSubTags()) {
            buf.append(",");
            final String nameValue = annotationParameter.getAttributeValue("name");
            if (nameValue != null) {
              buf.append(nameValue).append("=");
            }
            buf.append(StringUtil.unescapeXml(annotationParameter.getAttributeValue("val")));
          }
          final String annotationText =
            "@" + annotationFQN + (buf.length() > 0 ? "(" + StringUtil.trimStart(buf.toString(), ",") + ")" : "");
          try {
            result.put(annotationFQN,
                       JavaPsiFacade.getInstance(myPsiManager.getProject()).getElementFactory().createAnnotationFromText(
                         annotationText, null));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
    return result;
  }

  @NotNull
  protected abstract List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile);

  @Nullable
  protected List<XmlFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner) {
    final PsiFile containingFile = listOwner.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return null;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
    final String packageName = javaFile.getPackageName();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    String fqn = getFQN(packageName, virtualFile);
    if (fqn == null) return null;
    final List<XmlFile> files = myExternalAnnotations.get(fqn);
    if (files == NULL) return null;
    if (files != null) {
      for (Iterator<XmlFile> it = files.iterator(); it.hasNext();) {
        if (!it.next().isValid()) it.remove();
      }
      return files;
    }

    if (virtualFile == null) {
      return null;
    }

    List<XmlFile> possibleAnnotationsXmls = new ArrayList<XmlFile>();
    for (VirtualFile root : getExternalAnnotationsRoots(virtualFile)) {
      final VirtualFile ext = root.findFileByRelativePath(packageName.replace(".", "/") + "/" + ANNOTATIONS_XML);
      if (ext == null) continue;
      final PsiFile psiFile = myPsiManager.findFile(ext);
      if (!(psiFile instanceof XmlFile)) continue;
      possibleAnnotationsXmls.add((XmlFile)psiFile);
    }
    if (!possibleAnnotationsXmls.isEmpty()) {
      myExternalAnnotations.put(fqn, possibleAnnotationsXmls);
      return possibleAnnotationsXmls;
    }
    myExternalAnnotations.put(fqn, NULL);
    return null;
  }

  protected void dropCache() {
    cache.clear();
  }
}
