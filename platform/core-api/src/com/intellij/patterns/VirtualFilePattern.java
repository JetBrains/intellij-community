// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
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
      public boolean accepts(final @NotNull VirtualFile virtualFile, final ProcessingContext context) {
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
      public boolean accepts(final @NotNull VirtualFile virtualFile, final ProcessingContext context) {
        final String extension = virtualFile.getExtension();
        return ArrayUtil.contains(extension, alternatives);
      }
    });
  }

  public VirtualFilePattern withExtension(final @NonNls @NotNull String extension) {
    return with(new PatternCondition<VirtualFile>("withExtension") {
      @Override
      public boolean accepts(final @NotNull VirtualFile virtualFile, final ProcessingContext context) {
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
      public boolean accepts(final @NotNull VirtualFile virtualFile, final ProcessingContext context) {
        return namePattern.accepts(virtualFile.getName(), context);
      }
    });
  }

  public VirtualFilePattern withPath(final ElementPattern<String> pathPattern) {
    return with(new PatternCondition<VirtualFile>("withName") {
      @Override
      public boolean accepts(final @NotNull VirtualFile virtualFile, final ProcessingContext context) {
        return pathPattern.accepts(virtualFile.getPath(), context);
      }
    });
  }

  @Override
  protected VirtualFile getParent(final @NotNull VirtualFile t) {
    return t.getParent();
  }

  @Override
  protected VirtualFile[] getChildren(final @NotNull VirtualFile file) {
    return file.getChildren();
  }
}
