/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.lang.annotation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AnnotationSession extends UserDataHolderBase {
  private static final Key<TextRange> VR = Key.create("VR");
  private final PsiFile myFile;

  @ApiStatus.Internal
  public AnnotationSession(@NotNull PsiFile file) {
    myFile = file;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  @NotNull
  public TextRange getPriorityRange() {
    return Objects.requireNonNullElseGet(getUserData(VR), ()->getFile().getTextRange());
  }

  @ApiStatus.Internal
  public void setVR(TextRange range) {
    putUserData(VR, range);
  }
}
