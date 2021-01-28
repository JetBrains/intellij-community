// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @see PlatformPatterns#virtualFile()
 */
public class VirtualFilePattern extends TreeElementPattern<VirtualFile, VirtualFile, VirtualFilePattern> {
  public VirtualFilePattern() {
    super(VirtualFile.class);
  }

  public VirtualFilePattern ofType(final FileType type) {
    // Avoid capturing FileType instance if plugin providing the file type is unloaded
    String fileTypeName = type.getName();
    return with(new PatternCondition<VirtualFile>("ofType") {
      @Override
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return virtualFile.getFileType().getName().equals(fileTypeName);
      }
    });
  }

  /**
   * @see #withName(ElementPattern)
   */
  public VirtualFilePattern withName(final String name) {
    return withName(StandardPatterns.string().equalTo(name));
  }

  public VirtualFilePattern withExtension(@NotNull @NonNls String @NotNull ... alternatives) {
    return with(new PatternCondition<VirtualFile>("withExtension") {
      @Override
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        final String extension = virtualFile.getExtension();
        for (String alternative : alternatives) {
          if (alternative.equals(extension)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public VirtualFilePattern withExtension(@NonNls @NotNull final String extension) {
    return with(new PatternCondition<VirtualFile>("withExtension") {
      @Override
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return extension.equals(virtualFile.getExtension());
      }
    });
  }

  /**
   * @see #withName(String)
   */
  public VirtualFilePattern withName(final ElementPattern<String> namePattern) {
    return with(new PatternCondition<VirtualFile>("withName") {
      @Override
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return namePattern.accepts(virtualFile.getName(), context);
      }
    });
  }

  public VirtualFilePattern withPath(final ElementPattern<String> pathPattern) {
    return with(new PatternCondition<VirtualFile>("withName") {
      @Override
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return pathPattern.accepts(virtualFile.getPath(), context);
      }
    });
  }

  @Override
  protected VirtualFile getParent(@NotNull final VirtualFile t) {
    return t.getParent();
  }

  @Override
  protected VirtualFile[] getChildren(@NotNull final VirtualFile file) {
    return file.getChildren();
  }
}
