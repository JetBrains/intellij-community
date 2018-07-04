// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tools;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author lene
 */
public class ExternalToolsCheckinHandlerFactory extends CheckinHandlerFactory {
  public static final Object NONE_TOOL = ObjectUtils.sentinel("NONE_TOOL");

  @NotNull
  @Override
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    final ToolsProjectConfig config = ToolsProjectConfig.getInstance(panel.getProject());
    return new CheckinHandler() {
      @Override
      public RefreshableOnComponent getAfterCheckinConfigurationPanel(Disposable parentDisposable) {
        final JLabel label = new JLabel(ToolsBundle.message("tools.after.commit.description"));
        ComboboxWithBrowseButton listComponent = new ComboboxWithBrowseButton();
        final JComboBox comboBox = listComponent.getComboBox();
        comboBox.setModel(new CollectionComboBoxModel(getComboBoxElements(), null));
        comboBox.setRenderer(new ListCellRendererWrapper<Object>() {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value instanceof ToolsGroup) {
              setText(StringUtil.notNullize(((ToolsGroup)value).getName(), ToolsBundle.message("tools.unnamed.group")));
            }
            else if (value instanceof Tool) {
              setText("  " + StringUtil.notNullize(((Tool)value).getName()));
            }
            else {
              setText(ToolsBundle.message("tools.list.item.none"));
            }
          }
        });

        listComponent.getButton().addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            final Object item = comboBox.getSelectedItem();
            String id = null;
            if (item instanceof Tool) {
              id = ((Tool)item).getActionId();
            }
            final ToolSelectDialog dialog = new ToolSelectDialog(panel.getProject(), id, new ToolsPanel());
            if (!dialog.showAndGet()) {
              return;
            }

            comboBox.setModel(new CollectionComboBoxModel(getComboBoxElements(), dialog.getSelectedTool()));
          }
        });

        BorderLayout layout = new BorderLayout();
        layout.setVgap(3);
        final JPanel panel = new JPanel(layout);
        panel.add(label, BorderLayout.NORTH);
        panel.add(listComponent, BorderLayout.CENTER);
        listComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));

        if (comboBox.getItemCount() == 0 || (comboBox.getItemCount() == 1 && comboBox.getItemAt(0) == NONE_TOOL)) {
          return null;
        }

        return new RefreshableOnComponent() {
          @Override
          public JComponent getComponent() {
            return panel;
          }

          @Override
          public void refresh() {
            String id = config.getAfterCommitToolsId();
            if (id == null) {
              comboBox.setSelectedIndex(-1);
            }
            else {
              for (int i = 0; i < comboBox.getItemCount(); i++) {
                final Object itemAt = comboBox.getItemAt(i);
                if (itemAt instanceof Tool && id.equals(((Tool)itemAt).getActionId())) {
                  comboBox.setSelectedIndex(i);
                  return;
                }
              }
            }
          }

          @Override
          public void saveState() {
            Object item = comboBox.getSelectedItem();
            config.setAfterCommitToolId(item instanceof Tool ? ((Tool)item).getActionId() : null);
          }

          @Override
          public void restoreState() {
            refresh();
          }
        };
      }

      @Override
      public void checkinSuccessful() {
        final String id = config.getAfterCommitToolsId();
        if (id == null) {
          return;
        }
        DataManager.getInstance()
                   .getDataContextFromFocusAsync()
                   .onSuccess(context -> UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ToolAction.runTool(id, context)));
      }
    };
  }

  private static List<Object> getComboBoxElements() {
    List<Object> result = new SmartList<>();
    ToolManager manager = ToolManager.getInstance();
    result.add(NONE_TOOL);//for empty selection
    for (ToolsGroup group : manager.getGroups()) {
      result.add(group);
      result.addAll(manager.getTools(group.getName()));
    }

    return result;
  }
}
