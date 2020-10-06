// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
final class MoveTabDownAction extends SplitAction {
  MoveTabDownAction() {
    super(SwingConstants.HORIZONTAL, true);
  }
}
