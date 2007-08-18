package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.settings.BaseRenderersConfigurable;
import com.intellij.debugger.settings.CompositeConfigurable;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.ui.impl.FrameDebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

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
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());

    final CompositeConfigurable configurable = new CompositeConfigurable() {
      protected List<Configurable> createConfigurables() {
        ArrayList<Configurable> array = new ArrayList<Configurable>();
        array.add(new BaseRenderersConfigurable(project));
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

    new SingleConfigurableEditor(project, configurable).show();
  }

  public void update(AnActionEvent e) {
    DebuggerTree tree = getTree(e.getDataContext());
    e.getPresentation().setVisible(tree instanceof FrameDebuggerTree);
    e.getPresentation().setText(ActionsBundle.actionText(DebuggerActions.CUSTOMIZE_VIEWS));
  }
}
