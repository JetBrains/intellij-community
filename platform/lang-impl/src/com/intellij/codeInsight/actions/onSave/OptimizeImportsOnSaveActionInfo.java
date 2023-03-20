// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class OptimizeImportsOnSaveActionInfo extends FormatOnSaveActionInfoBase<OptimizeImportsOnSaveOptions> {

  private static final Key<OptimizeImportsOnSaveOptions> CURRENT_UI_STATE_KEY = Key.create("optimize.imports.on.save.options");

  OptimizeImportsOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context, CodeInsightBundle.message("actions.on.save.page.checkbox.optimize.imports"), CURRENT_UI_STATE_KEY);
  }

  @Override
  protected @NotNull OptimizeImportsOnSaveOptions getOptionsFromStoredState() {
    return OptimizeImportsOnSaveOptions.getInstance(getProject());
  }

  @Override
  protected void addApplicableFileTypes(@NotNull Collection<? super FileType> result) {
    ExtensionPoint<KeyedLazyInstance<ImportOptimizer>> ep = LanguageImportStatements.INSTANCE.getPoint();
    if (ep != null) {
      for (KeyedLazyInstance<ImportOptimizer> instance : ep.getExtensionList()) {
        String languageId = instance.getKey();
        Language language = Language.findLanguageByID(languageId);
        ContainerUtil.addIfNotNull(result, language != null ? language.getAssociatedFileType() : null);
      }
    }
  }

  @Override
  protected void apply() {
    getOptionsFromStoredState().loadState(getCurrentUiState().getState().clone());
  }
}
