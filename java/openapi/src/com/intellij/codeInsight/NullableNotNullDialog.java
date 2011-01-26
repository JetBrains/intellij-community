/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/25/11
 */
public class NullableNotNullDialog extends DialogWrapper {
  @NonNls private static final String GENERAL_ADD_ICON_PATH = "/general/add.png";
  private static final Icon ADD_ICON = IconLoader.getIcon(GENERAL_ADD_ICON_PATH);
  private static final Icon REMOVE_ICON = IconLoader.getIcon("/general/remove.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/ide/defaultProfile.png");

  private final Project myProject;
  private AnnoPanel myNullablePanel;
  private AnnoPanel myNotNullPanel;

  public NullableNotNullDialog(Project project) {
    super(project, true);
    myProject = project;
    init();
    setTitle("Nullable/NotNull configuration");
  }


  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel =
      new AnnoPanel("Nullable", manager.getDefaultNullable(), manager.getNullables(), NullableNotNullManager.DEFAULT_NULLABLES);
    panel.add(myNullablePanel, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0));
    myNotNullPanel = new AnnoPanel("NotNull", manager.getDefaultNotNull(), manager.getNotNulls(), NullableNotNullManager.DEFAULT_NOT_NULLS);
    panel.add(myNotNullPanel, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0));
    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0,0));
    return panel;
  }

  @Override
  protected void doOKAction() {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

    manager.setNotNulls(myNotNullPanel.getAnns());
    manager.setDefaultNotNull(myNotNullPanel.getDefaultAnn());

    manager.setNullables(myNullablePanel.getAnns());
    manager.setDefaultNullable(myNullablePanel.getDefaultAnn());

    super.doOKAction();
  }

  private class AnnoPanel extends JPanel {
    private String myDefaultAnn;
    private final String[] myDefaultAnns;
    private final JBList myList;

    private AnnoPanel(final String title, final String defaultAnn, final List<String> anns, final String[] defaultAnns) {
      super(new GridBagLayout());
      myDefaultAnn = defaultAnn;
      myDefaultAnns = defaultAnns;
      setBorder(BorderFactory.createTitledBorder(title));
      myList = new JBList(anns);
      myList.setCellRenderer(new DefaultListCellRenderer(){
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (Comparing.strEqual((String)value, myDefaultAnn)) {
            setIcon(Icons.ADVICE_ICON);
            setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
          }
          else {
            setIcon(EmptyIcon.ICON_16);
          }
          setText((String)value);
          return component;
        }
      });
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setSelectedValue(defaultAnn, true);
      final DefaultActionGroup group = new DefaultActionGroup();
      group.add(new AnAction("Add", "Add", ADD_ICON) {
        {
          registerCustomShortcutSet(CommonShortcuts.INSERT, myList);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          chooseAnnotation(title, myList, null);
        }
      });
      group.add(new AnAction("Delete", "Delete", REMOVE_ICON) {
        {
          registerCustomShortcutSet(CommonShortcuts.DELETE, myList);
        }

        @Override
        public void update(AnActionEvent e) {
          final Object selectedValue = myList.getSelectedValue();
          e.getPresentation().setEnabled(ArrayUtil.find(myDefaultAnns, selectedValue) == -1);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          ((DefaultListModel)myList.getModel()).removeElement(myList.getSelectedValue());
        }
      });
      group.add(new AnAction("Make default", "Default", SAVE_ICON) {
        @Override
        public void update(AnActionEvent e) {
          e.getPresentation().setEnabled(!Comparing.strEqual(myDefaultAnn, (String)myList.getSelectedValue()));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
           myDefaultAnn = (String)myList.getSelectedValue();
        }
      });
      GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 0);
      add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(), gc);
      gc.gridy = 1;
      gc.weighty = 1;
      gc.fill = GridBagConstraints.BOTH;
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myList);
      scrollPane.setMinimumSize(new Dimension(-1, 150));
      add(scrollPane, gc);
    }

    private void chooseAnnotation(String title, JBList list, PsiClass initial) {
      final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
        .createNoInnerClassesScopeChooser("Choose " + title, GlobalSearchScope.allScope(myProject), new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return aClass.isAnnotationType();
          }
        }, initial);
      chooser.showDialog();
      final PsiClass selected = chooser.getSelected();
      if (selected != null) {
        final String qualifiedName = selected.getQualifiedName();
        ((DefaultListModel)list.getModel()).addElement(qualifiedName);
      }
    }

    public String getDefaultAnn() {
      return myDefaultAnn;
    }

    public Object[] getAnns() {
      return ((DefaultListModel)myList.getModel()).toArray();
    }
  }
}
