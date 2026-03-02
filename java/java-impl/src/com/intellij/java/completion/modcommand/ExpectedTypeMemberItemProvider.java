// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.psi.PsiIdentifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;

@NotNullByDefault
final class ExpectedTypeMemberItemProvider extends JavaModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!(context.getPosition() instanceof PsiIdentifier)) {
      return;
    }
    Set<ExpectedTypeInfo> types = ContainerUtil.newHashSet(JavaSmartCompletionContributor.getExpectedTypes(context));
    boolean smart = context.getCompletionType() == CompletionType.SMART;
    if (smart || context.getInvocationCount() <= 1) { // on second basic completion, StaticMemberProcessor will suggest those
      //Consumer<LookupElement> consumer = e -> {
      //  sink.accept(smart ? JavaSmartCompletionContributor.decorate(e, types) : e);
      //};
      for (ExpectedTypeInfo info : types) {
        new JavaMembersGetter(info.getType(), context).addMembers(false, sink);
        if (!info.getType().equals(info.getDefaultType())) {
          new JavaMembersGetter(info.getDefaultType(), context).addMembers(false, sink);
        }
      }
    }
  }
}
