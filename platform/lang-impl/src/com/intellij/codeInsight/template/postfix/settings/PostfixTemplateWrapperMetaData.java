// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.intention.impl.config.BeforeAfterMetaData;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateWrapper;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData.decorateTextDescriptorWithKey;

public final class PostfixTemplateWrapperMetaData implements BeforeAfterMetaData {

  private final @NotNull BeforeAfterMetaData myDelegateMetaData;
  private final @NotNull @NlsSafe String myKey;

  public PostfixTemplateWrapperMetaData(@NotNull PostfixTemplateWrapper wrapper) {
    myKey = wrapper.getKey();
    myDelegateMetaData = PostfixTemplateMetaData.createMetaData(wrapper.getDelegate());
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesBefore() {
    if (myDelegateMetaData instanceof PostfixTemplateMetaData) {
      return decorateTextDescriptorWithKey(((PostfixTemplateMetaData)myDelegateMetaData).getRawExampleUsagesBefore(), myKey);
    }

    return myDelegateMetaData.getExampleUsagesBefore();
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesAfter() {
    if (myDelegateMetaData instanceof PostfixTemplateMetaData) {
      return decorateTextDescriptorWithKey(((PostfixTemplateMetaData)myDelegateMetaData).getRawExampleUsagesAfter(), myKey);
    }
    return myDelegateMetaData.getExampleUsagesAfter();
  }

  @Override
  public @NotNull TextDescriptor getDescription() {
    return myDelegateMetaData.getDescription();
  }
}
