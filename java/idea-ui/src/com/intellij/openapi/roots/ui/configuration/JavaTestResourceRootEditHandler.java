// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.awt.*;

public final class JavaTestResourceRootEditHandler extends JavaResourceRootEditHandlerBase {
  public JavaTestResourceRootEditHandler() {
    super(JavaResourceRootType.TEST_RESOURCE);
  }

  @Override
  public @NotNull String getRootTypeName() {
    return JavaUiBundle.message("title.test.resources");
  }

  @Override
  public @NotNull Icon getRootIcon() {
    return AllIcons.Modules.TestResourcesRoot;
  }

  @Override
  public @NotNull String getRootsGroupTitle() {
    return JavaUiBundle.message("section.title.test.resource.folders");
  }

  @Override
  public @NotNull Color getRootsGroupColor() {
    return new Color(0x739503);
  }

  @Override
  public @NotNull String getUnmarkRootButtonText() {
    return JavaUiBundle.message("button.unmark.test.resource");
  }
}
