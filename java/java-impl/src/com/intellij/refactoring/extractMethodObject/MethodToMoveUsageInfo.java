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

package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class MethodToMoveUsageInfo extends UsageInfo {
  private boolean myMove = true;

  public MethodToMoveUsageInfo(@NotNull PsiMethod element) {
    super(element);
  }

  public boolean isMove() {
    return myMove;
  }

  public void setMove(boolean move) {
    myMove = move;
  }
}