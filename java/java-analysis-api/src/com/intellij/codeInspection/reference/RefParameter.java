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

import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.Nullable;

/**
 * A node in the reference graph corresponding to a Java method parameter.
 *
 * @author anna
 * @since 6.0
 */
public interface RefParameter extends RefJavaElement {
  /**
   * Checks if the parameter is used for reading.
   *
   * @return true if the parameter has read accesses, false otherwise.
   */
  boolean isUsedForReading();

  /**
   * Checks if the parameter is used for writing.
   *
   * @return true if the parameter has write accesses, false otherwise.
   */
  boolean isUsedForWriting();

  /**
   * Returns the index of the parameter in the parameter list of its owner method.
   *
   * @return the index of the parameter.
   */
  int getIndex();

  /**
   * If all invocations of the method pass the same value to the parameter, returns
   * that value (the name of a static final field or the text of a literal expression).
   * Otherwise, returns null.
   *
   * @return the parameter value or null if it's different or impossible to determine.
   */
  @Nullable String getActualValueIfSame();

  /**
   * Marks the parameter as referenced for reading or writing.
   *
   * @param forWriting true if the parameter is marked as referenced for writing, false
   * otherwise.
   */
  void parameterReferenced(final boolean forWriting);

  @Override
  PsiParameter getElement();
}
