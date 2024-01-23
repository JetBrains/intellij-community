// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.application.options.GeneralCodeStylePanel;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.DropDownLink;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

final class FormatOnSaveActionInfo extends FormatOnSaveActionInfoBase<FormatOnSaveOptions> {

  private static final Key<FormatOnSaveOptions> CURRENT_UI_STATE_KEY = Key.create("format.on.save.options");

  FormatOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context, CodeInsightBundle.message("actions.on.save.page.checkbox.reformat.code"), CURRENT_UI_STATE_KEY);
  }

  @Override
  protected @NotNull FormatOnSaveOptions getOptionsFromStoredState() {
    return FormatOnSaveOptions.getInstance(getProject());
  }

  private boolean isFormatOnlyChangedLines() {
    return getCurrentUiState().isFormatOnlyChangedLines();
  }

  private void setFormatOnlyChangedLines(boolean changedLines) {
    getCurrentUiState().setFormatOnlyChangedLines(changedLines);
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    return List.of(new ActionLink(CodeInsightBundle.message("actions.on.save.page.link.configure.scope"), __ -> {
      GeneralCodeStylePanel.selectFormatterTab(getSettings());
    }));
  }

  @Override
  public @NotNull List<? extends DropDownLink<?>> getDropDownLinks() {
    DropDownLink<String> fileTypesLink = createFileTypesDropDownLink();
    DropDownLink<String> changedLinesLink = createChangedLinesDropDownLink();
    return changedLinesLink != null ? List.of(fileTypesLink, changedLinesLink) : List.of(fileTypesLink);
  }

  @Override
  protected void addApplicableFileTypes(@NotNull Collection<? super FileType> result) {
    // add all file types that can be handled by the IDE internal formatter (== have FormattingModelBuilder)
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (FileType fileType : fileTypeManager.getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType lft &&
          !fileTypeManager.getAssociations(lft).isEmpty() &&
          LanguageFormatting.INSTANCE.forLanguage(lft.getLanguage()) != null) {
        LanguageFileType associatedFileType = lft.getLanguage().getAssociatedFileType();
        result.add(lft.isSecondary() && associatedFileType != null
                   ? associatedFileType
                   : lft);
      }
    }

    // Iterating only FormattingModelBuilders is not enough. Some file types may get formatted by
    // external formatter integrated in the IDE (like ShExternalFormatter).
    //
    // A good sign that IDE supports some file type formatting is that it has a Code Style page for this file type. The following code makes
    // sure that all file types that have their Code Style pages are included in the result set.
    //
    // The logic of iterating Code Style pages is similar to what's done in CodeStyleSchemesConfigurable.buildConfigurables()
    for (CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      Language language = provider.getLanguage();
      if (provider.hasSettingsPage() && language != null) {
        ContainerUtil.addIfNotNull(result, language.getAssociatedFileType());
      }
    }
    for (LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.getSettingsPagesProviders()) {
      ContainerUtil.addIfNotNull(result, provider.getLanguage().getAssociatedFileType());
    }
  }

  private @Nullable DropDownLink<String> createChangedLinesDropDownLink() {
    if (!VcsFacade.getInstance().hasActiveVcss(getProject())) return null;

    String wholeFile = CodeInsightBundle.message("actions.on.save.page.label.whole.file");
    String changedLines = CodeInsightBundle.message("actions.on.save.page.label.changed.lines");

    String current = isFormatOnlyChangedLines() ? changedLines : wholeFile;

    return new DropDownLink<>(current, List.of(wholeFile, changedLines),
                              choice -> setFormatOnlyChangedLines(Strings.areSameInstance(choice, changedLines)));
  }


  @Override
  protected void apply() {
    getOptionsFromStoredState().loadState(getCurrentUiState().getState().clone());
  }
}
