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

  void includeProcessed(@NotNull PsiElement includeDirective);

  interface PsiIncludeHandler {
    boolean shouldCheckFile(@NotNull VirtualFile psiFile);
    IncludeInfo[] findIncludes(PsiFile psiFile);

    void includeChanged(final PsiElement includeDirective, final PsiFile targetFile, final PsiTreeChangeEvent event);
  }

  final class IncludeInfo {
    public static IncludeInfo[] EMPTY_ARRAY = new IncludeInfo[0];
    public @Nullable final PsiFile targetFile;
    public @Nullable final PsiElement includeDirective;
    public @NotNull final String[] possibleTargetFileNames;
    public @NotNull final PsiIncludeHandler handler;

    public IncludeInfo(@Nullable final PsiFile targetFile,
                       @Nullable final PsiElement includeDirective,
                       String[] possibleTargetFileNames, final PsiIncludeHandler handler) {
      this.targetFile = targetFile;
      this.includeDirective = includeDirective;
      this.possibleTargetFileNames = possibleTargetFileNames;
      this.handler = handler;
    }
  }
}
