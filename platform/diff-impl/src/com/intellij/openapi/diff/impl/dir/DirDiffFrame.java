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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffFrame extends FrameWrapper {
  private DirDiffPanel myPanel;

  public DirDiffFrame(Project project, DirDiffTableModel model) {
    super(project, "DirDiffDialog");
    setSize(JBUI.size(800, 600));
    setTitle(model.getTitle());
    myPanel = new DirDiffPanel(model, new DirDiffWindow.Frame(this));
    Disposer.register(this, myPanel);
    setComponent(myPanel.getPanel());
    if (project != null) {
      setProject(project);
    }
    closeOnEsc();
    DataManager.registerDataProvider(myPanel.getPanel(), new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.HELP_ID.is(dataId)) {
          return "reference.dialogs.diff.folder";
        }
        return null;
      }
    });
  }


  @Override
  protected void loadFrameState() {
    super.loadFrameState();
    myPanel.setupSplitter();
  }
}
