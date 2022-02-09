// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class ShowProductVersion implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "-version";
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Override
  public void main(String @NotNull [] args) {
    System.out.println(ApplicationInfoEx.getInstanceEx().getFullVersion());
    System.exit(0);
  }
}
