/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface DirDiffWindow {
  @NotNull
  Disposable getDisposable();

  void setTitle(@NotNull @NlsContexts.DialogTitle String title);


  class Dialog implements DirDiffWindow {
    @NotNull private final DirDiffDialog myDialog;

    public Dialog(@NotNull DirDiffDialog dialog) {
      myDialog = dialog;
    }

    @NotNull
    @Override
    public Disposable getDisposable() {
      return myDialog.getDisposable();
    }

    @Override
    public void setTitle(@NotNull @NlsContexts.DialogTitle String title) {
      myDialog.setTitle(title);
    }
  }

  class Frame implements DirDiffWindow {
    @NotNull private final DirDiffFrame myFrame;

    public Frame(@NotNull DirDiffFrame frame) {
      myFrame = frame;
    }

    @NotNull
    @Override
    public Disposable getDisposable() {
      return myFrame;
    }

    @Override
    public void setTitle(@NotNull String title) {
      ((JFrame)myFrame.getFrame()).setTitle(title);
    }
  }
}
