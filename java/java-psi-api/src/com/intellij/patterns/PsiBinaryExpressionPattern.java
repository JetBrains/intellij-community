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
package com.intellij.patterns;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiBinaryExpressionPattern extends PsiExpressionPattern<PsiBinaryExpression, PsiBinaryExpressionPattern>{
  protected PsiBinaryExpressionPattern() {
    super(PsiBinaryExpression.class);
  }

  public PsiBinaryExpressionPattern left(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("left") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getLOperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern right(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("right") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getROperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("operation") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getOperationSign(), context);
      }
    });
  }

}
