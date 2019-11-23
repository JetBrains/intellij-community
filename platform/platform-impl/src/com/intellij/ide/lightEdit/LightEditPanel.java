// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;


import com.intellij.ide.lightEdit.statusBar.LightEditStatusBar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class LightEditPanel extends JPanel implements Disposable {

  private final LightEditorManager myEditorManager;
  private final LightEditTabs      myTabs;

  public LightEditPanel(LightEditorManager editorManager) {
    myEditorManager = editorManager;
    setLayout(new BorderLayout());
    myTabs = new LightEditTabs(this, myEditorManager);
    add(myTabs, BorderLayout.CENTER);
    LightEditStatusBar statusBar = new LightEditStatusBar(myEditorManager);
    add(statusBar, BorderLayout.SOUTH);
    Disposer.register(this, statusBar);
  }

  public LightEditTabs getTabs() {
    return myTabs;
  }


  public void loadFile(@NotNull VirtualFile file) {
    LightEditorInfo editorInfo = myEditorManager.createEditor(file);
    if (editorInfo != null) {
      myTabs.addEditorTab(editorInfo);
    }
  }

  @Override
  public void dispose() {
  }

}
