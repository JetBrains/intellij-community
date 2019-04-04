// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contain temporarily modified code style settings if there are on-the-fly code style modifications on top of initial project settings for
 * a specific PSI file.
 * @see CodeStyle#getSettings(PsiFile)
 * @see CodeStyleSettingsModifier
 */
public final class TransientCodeStyleSettings extends CodeStyleSettings {
  private final PsiFile myPsiFile;
  private CodeStyleSettingsModifier myModifier;
  private final List<Object> myDependencies = ContainerUtil.newArrayList();

  public TransientCodeStyleSettings(@NotNull PsiFile psiFile, @NotNull CodeStyleSettings settings) {
    myPsiFile = psiFile;
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

  @NotNull
  public PsiFile getPsiFile() {
    return myPsiFile;
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
