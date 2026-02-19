// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes parameter class to create or existing class, chosen to wrap parameters
 */
public abstract class IntroduceParameterObjectClassDescriptor<M extends PsiNamedElement, P extends ParameterInfo> {
  /**
   * Class name to create/existing class short name
   */
  private final @NotNull String myClassName;
  /**
   * Package name where class should be created/package name of the existing class. Won't be used if 'create inner class' option is chosen
   */
  private final String myPackageName;

  /**
   * Flag to search for existing class with fqn: {@code myPackageName.myClassName}
   */
  private final boolean myUseExistingClass;

  /**
   * Flag that inner class with name {@code myClassName} should be created in outer class: {@code method.getContainingClass()}
   */
  private final boolean myCreateInnerClass;

  /**
   * Visibility for newly created class
   */
  private final String myNewVisibility;

  /**
   * Flag to generate accessors for existing class when fields won't be accessible from new usages
   */
  private final boolean myGenerateAccessors;

  /**
   * Bundle of method parameters which should correspond to the newly created/existing class fields
   */
  private final P[] myParamsToMerge;

  /**
   * Store existing class found by fqn / created class in refactoring#performRefactoring
   */
  private PsiElement myExistingClass;
  /**
   * Detected compatible constructor of the existing class
   */
  private M myExistingClassCompatibleConstructor;

  public IntroduceParameterObjectClassDescriptor(@NotNull String className,
                                                 String packageName,
                                                 boolean useExistingClass,
                                                 boolean createInnerClass,
                                                 String newVisibility,
                                                 boolean generateAccessors,
                                                 P[] parameters) {
    myClassName = className;
    myPackageName = packageName;
    myUseExistingClass = useExistingClass;
    myCreateInnerClass = createInnerClass;
    myNewVisibility = newVisibility;
    myGenerateAccessors = generateAccessors;
    myParamsToMerge = parameters;
  }

  public @NotNull String getClassName() {
    return myClassName;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public boolean isUseExistingClass() {
    return myUseExistingClass;
  }

  public boolean isCreateInnerClass() {
    return myCreateInnerClass;
  }

  public String getNewVisibility() {
    return myNewVisibility;
  }

  public P[] getParamsToMerge() {
    return myParamsToMerge;
  }

  public PsiElement getExistingClass() {
    return myExistingClass;
  }

  public void setExistingClass(PsiElement existingClass) {
    myExistingClass = existingClass;
  }

  public boolean isGenerateAccessors() {
    return myGenerateAccessors;
  }

  public P getParameterInfo(int oldIndex) {
    for (P info : myParamsToMerge) {
      if (info.getOldIndex() == oldIndex) {
        return info;
      }
    }
    return null;
  }

  /**
   * Corresponding field accessors how they should appear inside changed method body
   */
  public abstract String getSetterName(P paramInfo, @NotNull PsiElement context);
  public abstract String getGetterName(P paramInfo,
                                       @NotNull PsiElement context,
                                       ReadWriteAccessDetector.Access access);

  /**
   * Called if use existing class is chosen only. Should find constructor to use
   */
  public abstract @Nullable M findCompatibleConstructorInExistingClass(M method);
  public M getExistingClassCompatibleConstructor() {
    return myExistingClassCompatibleConstructor;
  }
  public void setExistingClassCompatibleConstructor(M existingClassCompatibleConstructor) {
    myExistingClassCompatibleConstructor = existingClassCompatibleConstructor;
  }

  public abstract PsiElement createClass(M method, ReadWriteAccessDetector.Access[] accessors);
}
