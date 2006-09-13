/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class MergingFileDescription<T extends DomElement> extends DomFileDescription<T>{
  private ModelMerger myMerger;

  protected MergingFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName) {
    super(rootElementClass, rootTagName);
  }

  @NotNull
  protected abstract Set<XmlFile> getFilesToMerge(DomElement element);

  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return getMergedRoot(reference);
  }

  protected final DomElement getMergedRoot(DomElement element) {
    Set<XmlFile> files = getFilesToMerge(element);

    final XmlFile xmlFile = element.getRoot().getFile();
    if (xmlFile != null) {
      files = new HashSet<XmlFile>(files);
      files.add(xmlFile);
    }

    ArrayList<T> roots = new ArrayList<T>(files.size());
    for (XmlFile file: files) {
      final DomFileElement<T> fileElement = element.getManager().getFileElement(file);
      if (fileElement != null) {
        roots.add(fileElement.getRootElement());
      }
    }

    if (roots.size() == 1) {
      return roots.iterator().next();
    }

    if (myMerger == null) {
      myMerger = element.getManager().createModelMerger();
    }
    return myMerger.mergeModels(getRootElementClass(), roots);
  }

  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    final List<JavaMethod> methods = DomUtil.getFixedPath(element.getParent());
    if (methods == null) return super.getIdentityScope(element);

    Object o = getMergedRoot(element);
    for (final JavaMethod method : methods) {
      o = method.invoke(o, ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    return (DomElement)o;
  }

  public boolean isAutomaticHighlightingEnabled() {
    return false;
  }
}
