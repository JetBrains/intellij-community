// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class LightEditPanel extends JPanel implements Disposable {
  private final LightEditTabs myTabs;

  public LightEditPanel(@NotNull Project project) {
    LightEditorManager editorManager = LightEditService.getInstance().getEditorManager();
    setLayout(new BorderLayout());
    myTabs = new LightEditTabs(project,this, (LightEditorManagerImpl)editorManager);
    add(myTabs, BorderLayout.CENTER);
    setBackground(JBColor.background().darker());
  }

  LightEditTabs getTabs() {
    return myTabs;
  }

  @Override
  public void dispose() {
    myTabs.removeAll();
  }
}
