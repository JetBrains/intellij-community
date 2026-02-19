// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.uast.UastMetaLanguage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UParameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class RefJavaManager implements RefManagerExtension<RefJavaManager> {
  public static final @NonNls String CLASS = "class";
  public static final @NonNls String METHOD = "method";
  @ApiStatus.Internal
  public static final @NonNls String IMPLICIT_CONSTRUCTOR = "implicit.constructor";
  public static final @NonNls String FIELD = "field";
  @ApiStatus.Internal
  public static final @NonNls String PARAMETER = "parameter";
  @ApiStatus.Internal
  public static final @NonNls String JAVA_MODULE = "java.module";
  public static final @NonNls String PACKAGE = "package";
  @ApiStatus.Internal
  public static final String FUNCTIONAL_EXPRESSION = "functional.expression";
  public static final Key<RefJavaManager> MANAGER = Key.create("RefJavaManager");
  private final List<Language> myLanguages;

  protected RefJavaManager() {
    List<Language> languages = new ArrayList<>(Language.findInstance(UastMetaLanguage.class).getMatchingLanguages());
    for (String lang : Registry.stringValue("batch.inspections.ignored.jvm.languages").split(",")) {
      languages.removeIf(l -> l.isKindOf(lang));
    }
    myLanguages = Collections.unmodifiableList(languages);
  }

  public abstract RefImplicitConstructor getImplicitConstructor(String classFQName);

  /**
   * Creates (if necessary) and returns the reference graph node for the package
   * with the specified name.
   *
   * @param packageName the name of the package for which the reference graph node is requested.
   * @return the node for the package.
   */
  public abstract RefPackage getPackage(String packageName);

  /**
   * Creates (if necessary) and returns the reference graph node for the specified PSI parameter.
   *
   * @param param the parameter for which the reference graph node is requested.
   * @param index the index of the parameter in its parameter list.
   * @param refElement the owner of the parameter, i.e. a {@link RefMethod} or {@link RefFunctionalExpression}
   * @return the node for the element, or null if the element is not valid or does not have
   * a corresponding reference graph node type (is not a field, method, class or file).
   */
  public abstract RefParameter getParameterReference(UParameter param, int index, RefElement refElement);

  public abstract RefPackage getDefaultPackage();

  public abstract PsiMethod getAppMainPattern();

  public abstract PsiMethod getAppPremainPattern();

  public abstract PsiMethod getAppAgentmainPattern();

  public abstract PsiClass getApplet();

  public abstract String getAppletQName();

  public abstract String getServletQName();

  public abstract EntryPointsManager getEntryPointsManager();

  @Override
  public @NotNull Collection<Language> getLanguages() {
    return myLanguages;
  }

  @Override
  public @NotNull Key<RefJavaManager> getID() {
    return MANAGER;
  }
}