/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class MacroCallNode extends Expression {
  private final List<Macro> myMacros;
  private final ArrayList<Expression> myParameters = new ArrayList<>();

  public MacroCallNode(@NotNull Macro macro) {
    this(Collections.singletonList(macro));
  }

  public MacroCallNode(List<Macro> macros) {
    myMacros = macros;
    assert macros.size() > 0;
  }

  public void addParameter(Expression node) {
    myParameters.add(node);
  }

  public Macro getMacro(TemplateContextType[] context) {
    Predicate<Macro> isAcceptableInContext = macro -> Arrays.stream(context).anyMatch(macro::isAcceptableInContext);
    return myMacros.stream().filter(isAcceptableInContext).findFirst().orElse(myMacros.get(0));
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return getMacro(context.getCompatibleContexts()).calculateResult(parameters, context);
  }

  @Override
  public Result calculateQuickResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return getMacro(context.getCompatibleContexts()).calculateQuickResult(parameters, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return getMacro(context.getCompatibleContexts()).calculateLookupItems(parameters, context);
  }

  public Expression[] getParameters() {
    return myParameters.toArray(new Expression[myParameters.size()]);
  }
}
