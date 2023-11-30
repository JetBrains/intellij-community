// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @see PlatformPatterns#psiFile()
 */
public class PsiFilePattern<T extends PsiFile, Self extends PsiFilePattern<T, Self>> extends PsiElementPattern<T, Self> {

  protected PsiFilePattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiFilePattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withParentDirectoryName(final StringPattern namePattern) {
    return with(new PatternCondition<T>("withParentDirectoryName") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        PsiDirectory directory = t.getContainingDirectory();
        return directory != null && namePattern.accepts(directory.getName(), context);
      }
    });
  }

  public Self withOriginalFile(final ElementPattern<? extends T> filePattern) {
    return with(new PatternCondition<T>("withOriginalFile") {
      @Override
      public boolean accepts(@NotNull T file, ProcessingContext context) {
        return filePattern.accepts(file.getOriginalFile());
      }
    });
  }

  public Self withVirtualFile(final ElementPattern<? extends VirtualFile> vFilePattern) {
    return with(new PatternCondition<T>("withVirtualFile") {
      @Override
      public boolean accepts(@NotNull T file, ProcessingContext context) {
        return vFilePattern.accepts(file.getVirtualFile(), context);
      }
    });
  }

  public Self withFileType(final ElementPattern<? extends FileType> fileTypePattern) {
    return with(new PatternCondition<T>("withFileType") {
      @Override
      public boolean accepts(@NotNull T file, ProcessingContext context) {
        return fileTypePattern.accepts(file.getFileType(), context);
      }
    });
  }

  public static class Capture<T extends PsiFile> extends PsiFilePattern<T, Capture<T>> {

    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(final @NotNull InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}
