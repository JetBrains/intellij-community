// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.awt.*;

public final class JavaResourceRootEditHandler extends JavaResourceRootEditHandlerBase {
  public JavaResourceRootEditHandler() {
    super(JavaResourceRootType.RESOURCE);
  }

  @Override
  public @NotNull String getRootTypeName() {
    return JavaUiBundle.message("title.resources");
  }

  @Override
  public @NotNull Icon getRootIcon() {
    return AllIcons.Modules.ResourcesRoot;
  }

  @Override
  public @NotNull String getRootsGroupTitle() {
    return JavaUiBundle.message("section.title.resource.folders");
  }

  @Override
  public @NotNull Color getRootsGroupColor() {
    return new JBColor(new Color(0x812DF3), new Color(127, 96, 144));
  }

  @Override
  public @NotNull String getUnmarkRootButtonText() {
    return JavaUiBundle.message("button.unmark.resource");
  }
}
