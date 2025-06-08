// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
  private final WeakReference<VirtualFile> myFileRef;
  private final Project myProject;
  private CodeStyleSettingsModifier myModifier;
  private final List<Object> myDependencies = new ArrayList<>();

  /**
   * @deprecated Use {@link #TransientCodeStyleSettings(VirtualFile,Project,CodeStyleSettings)}
   */
  @Deprecated
  public TransientCodeStyleSettings(@NotNull PsiFile psiFile, @NotNull CodeStyleSettings settings) {
    super(true, false);
    myFileRef = new WeakReference<>(psiFile.getVirtualFile());
    myProject = psiFile.getProject();
    copyFrom(settings);
    myDependencies.add(settings.getModificationTracker());
  }


  public TransientCodeStyleSettings(@NotNull VirtualFile file, @NotNull Project project, @NotNull CodeStyleSettings settings) {
    super(true, false);
    myFileRef = new WeakReference<>(file);
    myProject = project;
    copyFrom(settings);
    myDependencies.add(settings.getModificationTracker());
  }

  public void setModifier(@NotNull CodeStyleSettingsModifier modifier) {
    myModifier = modifier;
  }

  public @Nullable CodeStyleSettingsModifier getModifier() {
    return myModifier;
  }

  /**
   * @return A file for which the settings were initially computed or {@code null} if the file is no longer valid
   * (doesn't exist) and has been garbage collected.
   */
  public @Nullable PsiFile getPsiFile() {
    VirtualFile file = myFileRef.get();
    return file != null && file.isValid() ? PsiManager.getInstance(myProject).findFile(file) : null;
  }

  @Override
  public @NotNull IndentOptions getIndentOptionsByFile(@NotNull Project project,
                                                       @Nullable VirtualFile file,
                                                       @Nullable TextRange formatRange,
                                                       boolean ignoreDocOptions,
                                                       @Nullable Processor<? super FileIndentOptionsProvider> providerProcessor) {
    if (file != null && file.isValid()) {
      FileType fileType = file.getFileType();
      return getIndentOptions(fileType);
    }
    return OTHER_INDENT_OPTIONS;
  }

  /**
   * @deprecated Use {@link #applyIndentOptionsFromProviders(Project, VirtualFile)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public void applyIndentOptionsFromProviders(@NotNull PsiFile file) {
    applyIndentOptionsFromProviders(file.getProject(), file.getVirtualFile());
  }

  private static final Key<Boolean> INDENT_OPTIONS_APPLY_PROGRESS = Key.create("INDENT_OPTIONS_APPLY_PROGRESS");  
  
  @ApiStatus.Internal
  public void applyIndentOptionsFromProviders(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getUserData(INDENT_OPTIONS_APPLY_PROGRESS) != null) {
      return;
    }

    // ClsElementImpl.getIndentSize is called inside document generation procedure,
    // which can trigger code style computation based on the document => Stack Overflow.
    file.putUserData(INDENT_OPTIONS_APPLY_PROGRESS, Boolean.TRUE);
    try {
      for (FileIndentOptionsProvider provider : FileIndentOptionsProvider.EP_NAME.getExtensionList()) {
        if (provider.useOnFullReformat()) {
          IndentOptions indentOptions = provider.getIndentOptions(project, this, file);
          if (indentOptions != null) {
            IndentOptions targetOptions = getIndentOptions(file.getFileType());
            if (targetOptions != indentOptions) {
              targetOptions.copyFrom(indentOptions);
            }
          }
        }
      }
    }
    finally {
      file.putUserData(INDENT_OPTIONS_APPLY_PROGRESS, null);
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
