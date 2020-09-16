// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Contain temporarily modified code style settings if there are on-the-fly code style modifications on top of initial project settings for
 * a specific PSI file.
 * @see CodeStyle#getSettings(PsiFile)
 * @see CodeStyleSettingsModifier
 */
public final class TransientCodeStyleSettings extends CodeStyleSettings {
  private final WeakReference<PsiFile> myPsiFileRef;
  private CodeStyleSettingsModifier myModifier;
  private final List<Object> myDependencies = new ArrayList<>();

  public TransientCodeStyleSettings(@NotNull PsiFile psiFile, @NotNull CodeStyleSettings settings) {
    super(true, false);
    myPsiFileRef = new WeakReference<>(psiFile);
    copyFrom(settings);
    myDependencies.add(settings.getModificationTracker());
  }

  public void setModifier(@NotNull CodeStyleSettingsModifier modifier) {
    myModifier = modifier;
  }

  @Nullable
  public CodeStyleSettingsModifier getModifier() {
    return myModifier;
  }

  /**
   * @return A file for which the settings were initially computed or {@code null} if the file is no longer valid
   * (doesn't exist) and has been garbage collected.
   */
  @Nullable
  public PsiFile getPsiFile() {
    return myPsiFileRef.get();
  }

  @NotNull
  @Override
  public IndentOptions getIndentOptionsByFile(@Nullable PsiFile file,
                                              @Nullable TextRange formatRange,
                                              boolean ignoreDocOptions,
                                              @Nullable Processor<? super FileIndentOptionsProvider> providerProcessor) {
    if (file != null && file.isValid()) {
      FileType fileType = file.getFileType();
      return getIndentOptions(fileType);
    }
    return OTHER_INDENT_OPTIONS;
  }

  @ApiStatus.Internal
  public void applyIndentOptionsFromProviders(@NotNull PsiFile file) {
    for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensionList()) {
      if (provider.useOnFullReformat()) {
        IndentOptions indentOptions = provider.getIndentOptions(this, file);
        if (indentOptions != null) {
          IndentOptions targetOptions = getIndentOptions(file.getFileType());
          if (targetOptions != indentOptions) {
            targetOptions.copyFrom(indentOptions);
          }
        }
      }
    }
  }

  public void addDependency(@NotNull ModificationTracker dependency) {
    myDependencies.add(dependency);
  }

  public void addDependencies(@NotNull List<? extends ModificationTracker> dependencies) {
    myDependencies.addAll(dependencies);
  }

  public List<Object> getDependencies() {
    return myDependencies;
  }

}
