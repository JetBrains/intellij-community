// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.fileTypes.impl.FileTypeSelectable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// this file is assigned to "Plain text" file type even though there's a plugin supporting this specific extension/file pattern
public final class ReassignedToPlainTextInspection extends LocalInspectionTool {
  @Override
  public @NonNls @NotNull String getShortName() {
    return "ReassignedToPlainText";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) return null;
    if (!file.isPhysical()) return null;
    FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider.getBaseLanguage() != file.getLanguage()) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    FileType fileType = virtualFile.getFileType();
    if (fileType != PlainTextFileType.INSTANCE) {
      return null;
    }
    if (PlainTextFileType.INSTANCE.getDefaultExtension().equals(virtualFile.getExtension())) {
      return null;
    }
    FileType assigned = FileTypeManager.getInstance().getFileTypeByFileName(virtualFile.getNameSequence());
    if (assigned != PlainTextFileType.INSTANCE) {
      return null;
    }

    LocalQuickFix removeFix = new LocalQuickFix() {
      @Override
      public @Nls @NotNull String getFamilyName() {
        return InspectionsBundle.message("reassigned.to.plain.text.inspection.fix.remove.name");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        ((FileTypeManagerImpl)FileTypeManager.getInstance()).removePlainTextAssociationsForFile(descriptor.getPsiElement().getContainingFile().getName());
      }

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        return IntentionPreviewInfo.EMPTY;
      }
    };
    LocalQuickFix editFix = new LocalQuickFix() {
      @Override
      public @Nls @NotNull String getFamilyName() {
        return InspectionsBundle.message("reassigned.to.plain.text.inspection.fix.edit.name");
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        editFileType(project, PlainTextFileType.INSTANCE);
      }
    };
    ProblemDescriptor descriptor = manager.createProblemDescriptor(file, InspectionsBundle.message("reassigned.to.plain.text.inspection.message"), new LocalQuickFix[]{removeFix, editFix},
                                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                   true, false);
    return new ProblemDescriptor[]{descriptor};
  }
  private static void editFileType(@Nullable Project project, @NotNull FileType fileType) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project,
      configurable -> configurable instanceof SearchableConfigurable && ((SearchableConfigurable)configurable).getId().equals("preferences.fileTypes"),
      configurable -> {
        if (configurable instanceof ConfigurableWrapper) {
          configurable = (Configurable)((ConfigurableWrapper)configurable).getConfigurable();
        }
        FileTypeSelectable fileTypeSelectable = (FileTypeSelectable)configurable;
        fileTypeSelectable.selectFileType(fileType);
      });
  }
}