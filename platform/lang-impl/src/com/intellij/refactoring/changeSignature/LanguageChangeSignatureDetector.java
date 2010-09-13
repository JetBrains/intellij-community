/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Sep 6, 2010
 */
public interface LanguageChangeSignatureDetector {
  String MOVE_PARAMETER = "Parameter Move";

  @Nullable
  ChangeInfo createCurrentChangeSignature(final @NotNull PsiElement element,
                                          final @Nullable ChangeInfo changeInfo);

  boolean showDialog(ChangeInfo changeInfo, @NotNull String oldText);

  boolean isChangeSignatureAvailable(PsiElement element, ChangeInfo currentInfo);

  @Nullable
  TextRange getHighlightingRange(PsiElement element);

  boolean wasBanned(PsiElement element, @NotNull ChangeInfo bannedInfo);

  boolean isMoveParameterAvailable(PsiElement parameter, boolean left);

  void moveParameter(PsiElement parameter, Editor editor, boolean left);
}
