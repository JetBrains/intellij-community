// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;


import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class LightEditPanel extends JPanel implements Disposable {

  private final LightEditorManager myEditorManager;
  private final LightEditTabs myTabs;

  public LightEditPanel() {
    myEditorManager = new LightEditorManager();
    Disposer.register(this, myEditorManager);
    setLayout(new BorderLayout());
    myTabs = new LightEditTabs(this);
    add(myTabs, BorderLayout.CENTER);
  }

  public void loadFile(@NotNull VirtualFile file) {
    Editor editor = myEditorManager.createEditor(file);
    if (editor != null) {
      myTabs.addEditorTab(editor, file.getPresentableName());
    }
  }

  @Override
  public void dispose() {
  }

}
