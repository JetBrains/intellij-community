/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.wizard;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
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
