// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.intention.impl.config.BeforeAfterActionMetaData;
import com.intellij.codeInsight.intention.impl.config.BeforeAfterMetaData;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PostfixTemplateMetaData extends BeforeAfterActionMetaData {

  public static final String KEY = "$key";

  public static final PostfixTemplateMetaData EMPTY_METADATA = new PostfixTemplateMetaData();
  private static final String DESCRIPTION_FOLDER = "postfixTemplates";

  @NotNull
  public static BeforeAfterMetaData createMetaData(@Nullable PostfixTemplate template) {
    if (template == null) return EMPTY_METADATA;
    if (template instanceof PostfixTemplateWrapper) {
      return new PostfixTemplateWrapperMetaData((PostfixTemplateWrapper)template);
    }
    if (template instanceof EditablePostfixTemplate && !template.isBuiltin()) {
      return new EditablePostfixTemplateMetaData((EditablePostfixTemplate)template);
    }
    return new PostfixTemplateMetaData(template);
  }

  private PostfixTemplate myTemplate;

  public PostfixTemplateMetaData(@NotNull PostfixTemplate template) {
    super(template.getClass().getClassLoader(), template.getClass().getSimpleName());
    myTemplate = template;
  }

  PostfixTemplateMetaData() {
    super(EMPTY_DESCRIPTION, EMPTY_EXAMPLE, EMPTY_EXAMPLE);
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesBefore() {
    return decorateTextDescriptor(getRawExampleUsagesBefore());
  }

  TextDescriptor @NotNull [] getRawExampleUsagesBefore() {
    return super.getExampleUsagesBefore();
  }

  private TextDescriptor @NotNull [] decorateTextDescriptor(TextDescriptor[] before) {
    String key = myTemplate.getKey();
    return decorateTextDescriptorWithKey(before, key);
  }

  static TextDescriptor @NotNull [] decorateTextDescriptorWithKey(TextDescriptor[] before, @NotNull @NlsSafe String key) {
    List<TextDescriptor> list = new ArrayList<>(before.length);
    for (final TextDescriptor descriptor : before) {
      list.add(new TextDescriptor() {
        @NotNull
        @Override
        public String getText() throws IOException {
          return StringUtil.replace(descriptor.getText(), KEY, key);
        }

        @NotNull
        @Override
        public String getFileName() {
          return descriptor.getFileName();
        }
      });
    }
    return list.toArray(new TextDescriptor[0]);
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesAfter() {
    return decorateTextDescriptor(getRawExampleUsagesAfter());
  }

  TextDescriptor @NotNull [] getRawExampleUsagesAfter() {
    return super.getExampleUsagesAfter();
  }

  @Override
  protected String getResourceLocation(String resourceName) {
    return DESCRIPTION_FOLDER + "/" + myDescriptionDirectoryName + "/" + resourceName;
  }
}