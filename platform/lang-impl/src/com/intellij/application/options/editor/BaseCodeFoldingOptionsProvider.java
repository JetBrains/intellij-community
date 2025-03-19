// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.BeanConfigurable;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class BaseCodeFoldingOptionsProvider extends BeanConfigurable<CodeFoldingSettings> implements CodeFoldingOptionsProvider {
  public BaseCodeFoldingOptionsProvider() {
    super(CodeFoldingSettings.getInstance(), ApplicationBundle.message("title.general"));
    CodeFoldingSettings settings = getInstance();
    checkBox(ApplicationBundle.message("checkbox.collapse.file.header"), ()->settings.COLLAPSE_FILE_HEADER, v->settings.COLLAPSE_FILE_HEADER=v);
    checkBox(ApplicationBundle.message("checkbox.collapse.title.imports"), ()->settings.COLLAPSE_IMPORTS, v->settings.COLLAPSE_IMPORTS=v);
    checkBox(ApplicationBundle.message("checkbox.collapse.javadoc.comments"), ()->settings.COLLAPSE_DOC_COMMENTS, v->settings.COLLAPSE_DOC_COMMENTS=v);
    checkBox(ApplicationBundle.message("checkbox.collapse.method.bodies"), ()->settings.COLLAPSE_METHODS, v->settings.COLLAPSE_METHODS=v);
    checkBox(ApplicationBundle.message("checkbox.collapse.custom.folding.regions"), ()->settings.COLLAPSE_CUSTOM_FOLDING_REGIONS, v->settings.COLLAPSE_CUSTOM_FOLDING_REGIONS=v);
  }
}
