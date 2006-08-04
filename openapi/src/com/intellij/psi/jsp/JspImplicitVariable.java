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
package com.intellij.psi.jsp;

import com.intellij.pom.Navigatable;
import com.intellij.psi.ImplicitVariable;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.NavigationItem;

public interface JspImplicitVariable extends ImplicitVariable, NavigationItem {
  JspImplicitVariable[] EMPTY_ARRAY = new JspImplicitVariable[0];
  int INSIDE = 1;
  int AFTER = 2;
  int getDeclarationRange();

  PsiElement getDeclaration();
}