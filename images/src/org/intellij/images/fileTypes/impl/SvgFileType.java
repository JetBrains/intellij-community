// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.fileTypes.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import org.intellij.images.ImagesIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class SvgFileType extends XmlLikeFileType implements UIBasedFileType {
  public static final SvgFileType INSTANCE = new SvgFileType();

  private SvgFileType() {
    super(SvgLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "SVG";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeBundle.message("filetype.scalable.vector.graphics.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "svg";
  }

  @Override
  public Icon getIcon() {
    return ImagesIcons.ImagesFileType;
  }
}
