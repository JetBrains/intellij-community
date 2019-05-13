/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;

/**
 * @author Nikolay.Tropin
 */
public class JavaSourcePositionHighlighter extends SourcePositionHighlighter implements DumbAware {
  @Override
  public TextRange getHighlightRange(SourcePosition sourcePosition) {
    PsiElement method = DebuggerUtilsEx.getContainingMethod(sourcePosition);
    if (method instanceof PsiLambdaExpression) {
      return method.getTextRange();
    }
    return null;
  }
}
