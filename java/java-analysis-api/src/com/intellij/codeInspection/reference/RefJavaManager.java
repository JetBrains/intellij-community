/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class RefJavaManager implements RefManagerExtension<RefJavaManager> {
  @NonNls public static final String CLASS = "class";
  @NonNls public static final String METHOD = "method";
  @NonNls static final String IMPLICIT_CONSTRUCTOR = "implicit.constructor";
  @NonNls public static final String FIELD = "field";
  @NonNls static final String PARAMETER = "parameter";
  @NonNls static final String JAVA_MODULE = "java.module";
  @NonNls static final String PACKAGE = "package";
  public static final Key<RefJavaManager> MANAGER = Key.create("RefJavaManager");


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
   * @return the node for the element, or null if the element is not valid or does not have
   * a corresponding reference graph node type (is not a field, method, class or file).
   */
  public abstract RefParameter getParameterReference(PsiParameter param, int index);

  public abstract RefPackage getDefaultPackage();

  public abstract PsiMethod getAppMainPattern();

  public abstract PsiMethod getAppPremainPattern();

  public abstract PsiMethod getAppAgentmainPattern();

  public abstract PsiClass getApplet();

  public abstract PsiClass getServlet();

  public abstract EntryPointsManager getEntryPointsManager();

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