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

import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public interface PsiCheckedRenameElement extends PsiNamedElement {
  /**
   * Checks if it is possible to rename the element to the specified name,
   * and throws an exception if the rename is not possible. Does not actually modify anything.
   *
   * @param name the new name to check the renaming possibility for.
   * @throws IncorrectOperationException if the rename is not supported or not possible for some reason.
   */
  void checkSetName(String name) throws IncorrectOperationException;
}
