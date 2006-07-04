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

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.InstanceMap;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomFileDescription<T> {
  private final InstanceMap<ScopeProvider> myScopeProviders = new InstanceMap<ScopeProvider>();
  protected final Class<T> myRootElementClass;
  protected final String myRootTagName;
  private Map<Class<? extends DomElement>,Class<? extends DomElement>> myImplementations = new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();

  protected DomFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName) {
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
  }

  protected final <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    myImplementations.put(domElementClass, implementationClass);
  }

  protected static void registerClassChooser(final Type aClass, final TypeChooser typeChooser, Disposable parentDisposable) {
    TypeChooserManager.registerClassChooser(aClass, typeChooser, parentDisposable);
  }

  protected abstract void initializeFileDescription();

  @Nullable
  public DomElementsAnnotator createAnnotator() {
    return null;
  }

  public final Map<Class<? extends DomElement>,Class<? extends DomElement>> getImplementations() {
    initializeFileDescription();
    return myImplementations;
  }

  public final Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  public final String getRootTagName() {
    return myRootTagName;
  }

  public boolean isMyFile(XmlFile file) {
    XmlDocument doc = file.getDocument();
    if (doc != null) {
      XmlTag rootTag = doc.getRootTag();
      if (rootTag != null && rootTag.getLocalName().equals(myRootTagName)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return reference.getRoot();
  }

  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    return element.getParent();
  }

  @Nullable
  protected final DomElement getScopeFromAnnotation(final DomElement element) {
    final Scope scope = element.getAnnotation(Scope.class);
    if (scope != null) {
      return myScopeProviders.get(scope.value()).getScope(element);
    }
    return null;
  }

}
