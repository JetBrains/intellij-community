// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class StepAdapter implements Step {

  private final List<StepListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void _init() {}

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {}

  @Override
  public JComponent getComponent() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public void registerStepListener(StepListener listener) {
    myListeners.add(listener);
  }

  public void fireStateChanged() {
    for (StepListener listener: myListeners) {
      listener.stateChanged();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}
