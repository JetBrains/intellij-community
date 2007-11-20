package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public interface PsiIncludeManager {
  ExtensionPointName<PsiIncludeHandler> EP_NAME = new ExtensionPointName<PsiIncludeHandler>("com.intellij.psi.includeHandler");

  @NotNull
  PsiFile[] getIncludingFiles(@NotNull PsiFile file);

  @Nullable
  PsiFile getInclusionContext(@NotNull PsiFile file);

  interface PsiIncludeHandler {
    boolean shouldCheckFile(VirtualFile psiFile);
    IncludeInfo[] findIncludes(PsiFile psiFile);
  }

  final class IncludeInfo {
    public static IncludeInfo[] EMPTY_ARRAY = new IncludeInfo[0];
    public @Nullable final PsiFile targetFile;
    public @Nullable final PsiElement includeElement;
    public @NotNull final String[] possibleTargetFileNames;

    public IncludeInfo(@Nullable final PsiFile targetFile,
                       @Nullable final PsiElement includeElement,
                       String[] possibleTargetFileNames) {
      this.targetFile = targetFile;
      this.includeElement = includeElement;
      this.possibleTargetFileNames = possibleTargetFileNames;
    }
  }
}
