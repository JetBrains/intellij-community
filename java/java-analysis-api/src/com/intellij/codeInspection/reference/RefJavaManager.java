// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.uast.UastMetaLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UParameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class RefJavaManager implements RefManagerExtension<RefJavaManager> {
  @NonNls public static final String CLASS = "class";
  @NonNls public static final String METHOD = "method";
  @NonNls static final String IMPLICIT_CONSTRUCTOR = "implicit.constructor";
  @NonNls public static final String FIELD = "field";
  @NonNls static final String PARAMETER = "parameter";
  @NonNls static final String JAVA_MODULE = "java.module";
  @NonNls public static final String PACKAGE = "package";
  static final String FUNCTIONAL_EXPRESSION = "functional.expression";
  public static final Key<RefJavaManager> MANAGER = Key.create("RefJavaManager");
  private final List<Language> myLanguages;

  protected RefJavaManager() {
    List<Language> languages = new ArrayList<>(Language.findInstance(UastMetaLanguage.class).getMatchingLanguages());
    for (String lang : ContainerUtil.set(Registry.stringValue("ignored.jvm.languages").split(","))) {
      languages.removeIf(l -> l.isKindOf(lang));
    }
    if (!Registry.is("batch.jvm.inspections") && !ApplicationManager.getApplication().isUnitTestMode()) {
      languages.removeIf(l -> l.isKindOf("kotlin"));
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

  @NotNull
  @Override
  public Collection<Language> getLanguages() {
    return myLanguages;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @NotNull
  @Override
  public Key<RefJavaManager> getID() {
    return MANAGER;
  }
}