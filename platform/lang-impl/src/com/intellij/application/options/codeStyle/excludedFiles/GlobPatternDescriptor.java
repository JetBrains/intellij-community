// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.formatting.fileSet.FileSetDescriptorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.PatternSyntaxException;

public final class GlobPatternDescriptor implements FileSetDescriptor {
  public static final String TYPE = "globPattern";

  private String myPattern;

  public GlobPatternDescriptor(@NotNull String pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean matches(@NotNull PsiFile psiFile) {
    return matches(psiFile.getVirtualFile());
  }

  public boolean matches(@Nullable VirtualFile file) {
    if (myPattern != null &&
        file != null &&
        file.isInLocalFileSystem()) {
      try {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(getGlob(myPattern));
        return matcher.matches(Paths.get(file.getPath()));
      }
      catch (PatternSyntaxException ignore) {
        return false;
      }
    }
    return false;
  }

  private static String getGlob(@NotNull String pattern) {
    return "glob:" + (pattern.startsWith("/") ? "**" : "**/") + pattern;
  }

  @Override
  public @NotNull String getType() {
    return TYPE;
  }

  @Override
  public @Nullable String getPattern() {
    return myPattern;
  }

  @Override
  public void setPattern(@Nullable String pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GlobPatternDescriptor && Objects.equals(((GlobPatternDescriptor)obj).myPattern, myPattern);
  }

  public static final class Factory implements FileSetDescriptorFactory {

    @Override
    public @Nullable FileSetDescriptor createDescriptor(@NotNull State state) {
      if (TYPE.equals(state.type) && state.pattern != null) {
        return new GlobPatternDescriptor(state.pattern);
      }
      return null;
    }
  }
}
