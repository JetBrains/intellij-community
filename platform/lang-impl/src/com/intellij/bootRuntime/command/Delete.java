// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.bundles.Runtime;

import java.awt.event.ActionEvent;

public class Delete extends Command {
  public Delete(Runtime runtime) {
    super("Delete", runtime);
  }

  @Override
  public void actionPerformed(ActionEvent e) {

  }
}
