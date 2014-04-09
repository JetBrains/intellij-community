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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: 1/25/11
 */
public class NullableNotNullManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + NullableNotNullManager.class.getName());

  public String myDefaultNullable = AnnotationUtil.NULLABLE;
  public String myDefaultNotNull = AnnotationUtil.NOT_NULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();

  public static final String[] DEFAULT_NULLABLES = {AnnotationUtil.NULLABLE, "javax.annotation.Nullable",
    "edu.umd.cs.findbugs.annotations.Nullable", "android.support.annotation.Nullable"
  };
  public static final String[] DEFAULT_NOT_NULLS = {AnnotationUtil.NOT_NULL, "javax.annotation.Nonnull",
    "edu.umd.cs.findbugs.annotations.NonNull", "android.support.annotation.NonNull"
  };

  public NullableNotNullManager() {
    Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
    Collections.addAll(myNullables, DEFAULT_NULLABLES);
  }

  public static NullableNotNullManager getInstance(Project project) {
    return ServiceManager.getService(project, NullableNotNullManager.class);
  }

  /**
   * @return if owner has a @NotNull or @Nullable annotation, or is in scope of @ParametersAreNullableByDefault or ParametersAreNonnullByDefault
   */
  public boolean hasNullability(@NotNull PsiModifierListOwner owner) {
    return isNullable(owner, false) || isNotNull(owner, false);
  }

  private static void addAllIfNotPresent(Collection<String> collection, String... annotations) {
    for (String annotation : annotations) {
      LOG.assertTrue(annotation != null);
      if (!collection.contains(annotation)) {
        collection.add(annotation);
      }
    }
  }

  public void setNotNulls(String... annotations) {
    myNotNulls.clear();
    addAllIfNotPresent(myNotNulls, DEFAULT_NOT_NULLS);
    addAllIfNotPresent(myNotNulls, annotations);
  }

  public void setNullables(String... annotations) {
    myNullables.clear();
    addAllIfNotPresent(myNullables, DEFAULT_NULLABLES);
    addAllIfNotPresent(myNullables, annotations);
  }

  public String getDefaultNullable() {
    return myDefaultNullable;
  }
  
  @Nullable
  public String getNullable(PsiModifierListOwner owner) {
    PsiAnnotation annotation = findNullabilityAnnotation(owner, false, true);
    return annotation == null ? null : annotation.getQualifiedName();
  }

  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
  }

  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }
  
  @Nullable
  public String getNotNull(PsiModifierListOwner owner) {
    PsiAnnotation annotation = findNullabilityAnnotation(owner, false, false);
    return annotation == null ? null : annotation.getQualifiedName();
  }

  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
  }

  @Nullable 
  private PsiAnnotation findNullabilityAnnotation(@NotNull PsiModifierListOwner owner, boolean checkBases, boolean nullable) {
    Set<String> qNames = ContainerUtil.newHashSet(nullable ? getNullables() : getNotNulls());
    PsiAnnotation annotation = checkBases && (owner instanceof PsiClass || owner instanceof PsiMethod)
                               ? AnnotationUtil.findAnnotationInHierarchy(owner, qNames)
                               : AnnotationUtil.findAnnotation(owner, qNames);
    if (annotation != null) {
      return annotation;
    }

    if (owner instanceof PsiParameter && !TypeConversionUtil.isPrimitiveAndNotNull(((PsiParameter)owner).getType())) {
      // even if javax.annotation.Nullable is not configured, it should still take precedence over ByDefault annotations
      if (AnnotationUtil.isAnnotated(owner, nullable ? Arrays.asList(DEFAULT_NOT_NULLS) : Arrays.asList(DEFAULT_NULLABLES), checkBases, false)) {
        return null;
      }
      return findContainerAnnotation(owner, nullable
                                            ? "javax.annotation.ParametersAreNullableByDefault"
                                            : "javax.annotation.ParametersAreNonnullByDefault");
    }
    return null;
  }

  public boolean isNullable(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullabilityAnnotation(owner, checkBases, true) != null;
  }

  public boolean isNotNull(@NotNull PsiModifierListOwner owner, boolean checkBases) {
    return findNullabilityAnnotation(owner, checkBases, false) != null;
  }

  @Nullable 
  private static PsiAnnotation findContainerAnnotation(PsiModifierListOwner owner, String annotationFQN) {
    PsiElement element = owner.getParent();
    while (element != null) {
      if (element instanceof PsiModifierListOwner) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiModifierListOwner)element, annotationFQN);
        if (annotation != null) {
          return annotation;
        }
      }

      if (element instanceof PsiClassOwner) {
        String packageName = ((PsiClassOwner)element).getPackageName();
        PsiPackage psiPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
        return AnnotationUtil.findAnnotation(psiPackage, annotationFQN);
      }

      element = element.getContext();
    }
    return null;
  }

  public List<String> getNullables() {
    return myNullables;
  }

  public List<String> getNotNulls() {
    return myNotNulls;
  }

  public boolean hasDefaultValues() {
    if (DEFAULT_NULLABLES.length != getNullables().size() || DEFAULT_NOT_NULLS.length != getNotNulls().size()) {
      return false;
    }
    if (!myDefaultNotNull.equals(AnnotationUtil.NOT_NULL) || !myDefaultNullable.equals(AnnotationUtil.NULLABLE)) {
      return false;
    }
    for (int i = 0; i < DEFAULT_NULLABLES.length; i++) {
      if (!getNullables().get(i).equals(DEFAULT_NULLABLES[i])) {
        return false;
      }
    }
    for (int i = 0; i < DEFAULT_NOT_NULLS.length; i++) {
      if (!getNotNulls().get(i).equals(DEFAULT_NOT_NULLS[i])) {
        return false;
      }
    }

    return true;
  }

  @Override
  public Element getState() {
    final Element component = new Element("component");

    if (hasDefaultValues()) {
      return component;
    }

    try {
      DefaultJDOMExternalizer.writeExternal(this, component);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return component;
  }

  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      if (myNullables.isEmpty()) {
        Collections.addAll(myNullables, DEFAULT_NULLABLES);
      }
      if (myNotNulls.isEmpty()) {
        Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return !isNotNull(owner) && getInstance(owner.getProject()).isNullable(owner, true);
  }

  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNotNull(owner, true);
  }
}
