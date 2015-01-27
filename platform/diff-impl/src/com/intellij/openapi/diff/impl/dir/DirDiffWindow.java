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

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffWindow {
  private final DirDiffDialog myDialog;
  private final DirDiffFrame myFrame;

  public DirDiffWindow(DirDiffDialog dialog) {
    myDialog = dialog;
    myFrame = null;
  }

  public DirDiffWindow(DirDiffFrame frame) {
    myFrame = frame;
    myDialog = null;
  }

  public Window getWindow() {
    return myDialog == null ? myFrame.getFrame() : myDialog.getWindow();
  }

  public Disposable getDisposable() {
    return myDialog == null ? myFrame : myDialog.getDisposable();
  }

  public void setTitle(String title) {
    if (myDialog == null) {
      ((JFrame)myFrame.getFrame()).setTitle(title);
    } else {
      myDialog.setTitle(title);
    }
  }
}
