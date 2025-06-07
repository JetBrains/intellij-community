// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class FileDirRelativeToSourcepathMacro2 extends FileDirRelativeToSourcepathMacro {
  @Override
  public @NotNull String getName() {
    return "/FileDirRelativeToSourcepath";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.dir.relative.to.sourcepath.root.fwd.slash");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
