// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class JavaPostfixTemplateProviderImpl extends JavaPostfixTemplateProvider {
  @NotNull
  private final Set<PostfixTemplate> templates;

  public JavaPostfixTemplateProviderImpl() {
    templates = ContainerUtil.newHashSet(new CastExpressionPostfixTemplate(),
                                         new ElseStatementPostfixTemplate(),
                                         new ForAscendingPostfixTemplate(),
                                         new ForDescendingPostfixTemplate(),
                                         new ForeachPostfixTemplate("iter"),
                                         new ForeachPostfixTemplate("for"),
                                         new FormatPostfixTemplate(),
                                         new IfStatementPostfixTemplate(),
                                         new InstanceofExpressionPostfixTemplate(),
                                         new InstanceofExpressionPostfixTemplate("inst"),
                                         new IntroduceFieldPostfixTemplate(),
                                         new IntroduceVariablePostfixTemplate(),
                                         new IsNullCheckPostfixTemplate(),
                                         new NotExpressionPostfixTemplate(),
                                         new NotExpressionPostfixTemplate("!"),
                                         new NotNullCheckPostfixTemplate(),
                                         new NotNullCheckPostfixTemplate("nn"),
                                         new ParenthesizedExpressionPostfixTemplate(),
                                         new ReturnStatementPostfixTemplate(),
                                         new SoutPostfixTemplate(),
                                         new SwitchStatementPostfixTemplate(),
                                         new SynchronizedStatementPostfixTemplate(),
                                         new ThrowExceptionPostfixTemplate(),
                                         new TryStatementPostfixTemplate(),
                                         new TryWithResourcesPostfixTemplate(),
                                         new WhileStatementPostfixTemplate(),
                                         new StreamPostfixTemplate(),
                                         new OptionalPostfixTemplate(),
                                         new LambdaPostfixTemplate(),
                                         new ObjectsRequireNonNullPostfixTemplate());
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return templates;
  }
}
