/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 27-Dec-2005
 */
public interface RefClass extends RefElement {

  @NotNull
  HashSet<RefClass> getBaseClasses();

  @NotNull
  HashSet<RefClass> getSubClasses();

  @NotNull
  ArrayList<RefMethod> getConstructors();

  @NotNull
  Set<RefElement> getInTypeReferences();

  @NotNull
  Set<RefElement> getInstanceReferences();

  RefMethod getDefaultConstructor();

  @NotNull
  List<RefMethod> getLibraryMethods();

  boolean isAnonymous();

  boolean isInterface();

  boolean isUtilityClass();

  boolean isAbstract();

  boolean isEjb();

  boolean isApplet();

  boolean isServlet();

  boolean isTestCase();

  boolean isLocalClass();

  boolean isSelfInheritor(PsiClass psiClass);

  PsiClass getElement();
}
