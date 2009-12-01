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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.settings.DebuggerDataViewsConfigurable;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.ui.impl.FrameDebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 4:39:53 PM
 */
public class CustomizeContextViewAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    Disposable disposable = Disposer.newDisposable();
    final CompositeConfigurable configurable = new TabbedConfigurable(disposable) {
      protected List<Configurable> createConfigurables() {
        ArrayList<Configurable> array = new ArrayList<Configurable>();
        array.add(new DebuggerDataViewsConfigurable(project));
        array.add(new UserRenderersConfigurable(project));
        return array;
      }

      public void apply() throws ConfigurationException {
        super.apply();
        NodeRendererSettings.getInstance().fireRenderersChanged();
      }

      public String getDisplayName() {
        return DebuggerBundle.message("title.customize.data.views");
      }

      public Icon getIcon() {
        return null;
      }

      public String getHelpTopic() {
        return null;
      }
    };

    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
    Disposer.register(editor.getDisposable(), disposable);
    editor.show();
  }

  public void update(AnActionEvent e) {
    DebuggerTree tree = getTree(e.getDataContext());
    e.getPresentation().setVisible(tree instanceof FrameDebuggerTree);
    e.getPresentation().setText(ActionsBundle.actionText(DebuggerActions.CUSTOMIZE_VIEWS));
  }
}
