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

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiField;

/**
 * A node in the reference graph corresponding to a Java field.
 *
 * @author anna
 * @since 6.0
 */
public interface RefField extends RefJavaElement {
   Key<Boolean> ENUM_CONSTANT = Key.create("ENUM_CONSTANT");
   Key<Boolean> IMPLICITLY_WRITTEN = Key.create("IMPLICITLY_WRITTEN");
   Key<Boolean> IMPLICITLY_READ = Key.create("IMPLICITLY_READ");
  /**
   * Checks if the field is used for reading.
   *
   * @return true if the field has read accesses, false otherwise.
   */
  boolean isUsedForReading();

  /**
   * Checks if the field is used for writing.
   *
   * @return true if the field has write accesses, false otherwise.
   */
  boolean isUsedForWriting();

  /**
   * Checks if the only write access of the field is its initializer.
   *
   * @return true if the only write access of the field is its initializer, false otherwise.
   */
  boolean isOnlyAssignedInInitializer();

  /**
   * Returns the reference graph node for the class to which the field belongs.
   *
   * @return the owner class of the field.
   */
  RefClass getOwnerClass();

  @Override
  PsiField getElement();
}
