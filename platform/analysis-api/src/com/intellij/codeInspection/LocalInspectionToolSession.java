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

/*
 * User: anna
 * Date: 07-Aug-2008
 */
package com.intellij.codeInspection;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class LocalInspectionToolSession extends UserDataHolderBase {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;

  public LocalInspectionToolSession(@NotNull PsiFile file, final int startOffset, final int endOffset) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }
}
