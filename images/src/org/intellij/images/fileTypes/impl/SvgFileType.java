// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.fileTypes.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.XmlCharsetDetector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Konstantin Bulenkov
 */
public final class SvgFileType extends LanguageFileType implements UIBasedFileType {
  public static final SvgFileType INSTANCE = new SvgFileType();

  private SvgFileType() {
    super(SvgLanguage.INSTANCE);
  }

  // copy-pasted from XmlLikeFileType
  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    LoadTextUtil.DetectResult guessed = LoadTextUtil.guessFromContent(file, content);
    String charset =
      guessed.hardCodedCharset != null
      ? guessed.hardCodedCharset.name()
      : XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    return charset == null ? CharsetToolkit.UTF8 : charset;
  }

  // copy-pasted from XmlLikeFileType
  @Override
  public Charset extractCharsetFromFileContent(final Project project, final @Nullable VirtualFile file, final @NotNull CharSequence content) {
    String name = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    Charset charset = CharsetToolkit.forName(name);
    return charset == null ? StandardCharsets.UTF_8 : charset;
  }

  @Override
  public @NotNull String getName() {
    return "SVG";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeBundle.message("filetype.scalable.vector.graphics.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return IdeBundle.message("filetype.scalable.vector.graphics.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "svg";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Image;
  }
}
