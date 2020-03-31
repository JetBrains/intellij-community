// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class LightEditPanel extends JPanel implements Disposable {
  private final LightEditorManager myEditorManager;
  private final LightEditTabs myTabs;

  public LightEditPanel() {
    myEditorManager = LightEditService.getInstance().getEditorManager();
    setLayout(new BorderLayout());
    myTabs = new LightEditTabs(this, (LightEditorManagerImpl)myEditorManager);
    add(myTabs, BorderLayout.CENTER);
  }

  LightEditTabs getTabs() {
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
    myTabs.removeAll();
  }
}
