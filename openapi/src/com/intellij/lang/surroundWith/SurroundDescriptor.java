/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.lang.surroundWith;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a code fragment type on which the Surround With action can be used for files
 * in a custom language. All surround descriptors registered for a language are queried
 * sequentially, and as soon as one is found that returns a non-empty list of elements
 * from {@link #getElementsToSurround(com.intellij.psi.PsiFile, int, int)}, the user
 * is prompted to choose a specific surrounder for that surround descriptor.
 *
 * @author ven
 * @see com.intellij.lang.Language#getSurroundDescriptors()
 */
public interface SurroundDescriptor {
  /**
   * Returns the list of elements which will be included in the surrounded region for
   * the specified selection in the specified file, or an empty array if no surrounders
   * from this surround descriptor are applicable to the specified selection.
   *
   * @param file        the file where elements are to be surrounded.
   * @param startOffset the selection start offset, with whitespaces skipped
   * @param endOffset   the selection end offset, with whitespaces skipped
   * @return the elements to be surrounded, or an empty array if cannot surround
   */
  @NotNull
  PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset);

  /**
   * Returns the list of surrounders (surround templates) which can be used for this
   * code fragment type.
   *
   * @return the list of surrounders.
   */
  @NotNull
  Surrounder[] getSurrounders();
}
