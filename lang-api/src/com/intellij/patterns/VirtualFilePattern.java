/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.patterns;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public class VirtualFilePattern extends TreeElementPattern<VirtualFile, VirtualFile, VirtualFilePattern> {
  public VirtualFilePattern() {
    super(VirtualFile.class);
  }

  public VirtualFilePattern ofType(final FileType type) {
    return with(new PatternCondition<VirtualFile>() {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return type.equals(virtualFile.getFileType());
      }
    });
  }

  public VirtualFilePattern withName(final ElementPattern namePattern) {
    return with(new PatternCondition<VirtualFile>() {
      public boolean accepts(@NotNull final VirtualFile virtualFile,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return namePattern.getCondition().accepts(virtualFile.getName(), matchingContext, traverseContext);
      }
    });
  }

  public VirtualFilePattern xmlWithRootTag(final ElementPattern tagNamePattern) {
    return with(new PatternCondition<VirtualFile>() {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        try {
          NanoXmlUtil.RootTagNameBuilder rootTagNameBuilder = new NanoXmlUtil.RootTagNameBuilder();
          NanoXmlUtil.parse(virtualFile.getInputStream(), rootTagNameBuilder);
          String tagName = rootTagNameBuilder.getResult();
          return tagName != null && tagNamePattern.getCondition().accepts(tagName, matchingContext, traverseContext);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  protected VirtualFile getParent(@NotNull final VirtualFile t) {
    return t.getParent();
  }

  protected VirtualFile[] getChildren(@NotNull final VirtualFile file) {
    return file.getChildren();
  }
}
