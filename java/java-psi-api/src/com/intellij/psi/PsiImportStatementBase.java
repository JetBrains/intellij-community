/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code import} or {@code import static} statement.
 */
public interface PsiImportStatementBase extends PsiElement {
  /**
   * The empty array of PSI base import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportStatementBase[] EMPTY_ARRAY = new PsiImportStatementBase[0];

  ArrayFactory<PsiImportStatementBase> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiImportStatementBase[count];

  /**
   * Checks if the statement represents a single element or on-demand import.
   *
   * @return true if the import statement is on-demand, false otherwise.
   */
  boolean isOnDemand();

  /**
   * Returns the reference element which specifies the imported class, package or member.
   *
   * @return the import reference element.
   * @see PsiImportStaticReferenceElement
   */
  @Nullable
  PsiJavaCodeReferenceElement getImportReference();

  /**
   * Resolves the reference to the imported class, package or member.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */
  @Nullable
  PsiElement resolve();

  boolean isForeignFileImport();
}
