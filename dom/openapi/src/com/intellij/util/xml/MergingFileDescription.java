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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
    final Set<XmlFile> files = getFilesToMerge(element);

    final XmlFile xmlFile = element.getRoot().getFile();
    if (xmlFile != null) {
      files.add(xmlFile);
    }

    ArrayList<T> roots = new ArrayList<T>(files.size());
    for (XmlFile file: files) {
      roots.add(element.getManager().<T>getFileElement(file).getRootElement());
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

    final List<Method> methods = DomUtil.getFixedPath(element.getParent());
    if (methods == null) return super.getIdentityScope(element);

    Object o = getMergedRoot(element);
    for (final Method method : methods) {
      o = DomReflectionUtil.invokeMethod(method, o);
    }
    return (DomElement)o;
  }
}
