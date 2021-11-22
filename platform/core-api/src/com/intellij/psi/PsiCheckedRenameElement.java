// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

public interface PsiCheckedRenameElement extends PsiNamedElement {
  /**
   * Checks if it is possible to rename the element to the specified name,
   * and throws an exception if the renaming is not possible. Does not actually modify anything.
   *
   * @param name the new name to check the renaming possibility for.
   * @throws IncorrectOperationException if the renaming is not supported or not possible for some reason.
   */
  void checkSetName(String name) throws IncorrectOperationException;
}
