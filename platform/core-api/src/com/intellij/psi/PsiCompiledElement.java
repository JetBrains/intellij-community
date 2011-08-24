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
package com.intellij.psi;

/**
 * Represents an element in a Java library used in a project. References
 * to library classes/methods are always resolved to compiled elements;
 * to get the corresponding source code, if it is available,
 * {@link com.intellij.psi.PsiElement#getNavigationElement()} should be called.
 */
public interface PsiCompiledElement extends PsiElement {
  /**
   * Returns the corresponding PSI element in a decompiled file created by IDEA from
   * the library element.
   *
   * @return the counterpart of the element in decompiled file.
   */
  PsiElement getMirror();
}