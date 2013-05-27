/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.*;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.ui.*;
import com.intellij.ui.components.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Dialog that appears when user clicks the "Configure IsNull/IsNotNull/True/False Check/Assertion Methods"
 * on the Errors dialog for the Constant Conditions Inspection.  It is divided into 6 parts
 * <ol>
 * <li>Is Null Check MethodsPanel</li>
 * <li>Is Not Null Check MethodsPanel</li>
 * <li>Assert Is Null MethodsPanel</li>
 * <li>Assert Is Not Null MethodsPanel</li>
 * <li>Assert True MethodsPanel</li>
 * <li>Assert False MethodsPanel</li>
 * </ol>
 *
 * @author <a href="mailto:johnnyclark@gmail.com">Johnny Clark</a>
 *         Creation Date: 8/3/12
 */
public class ConditionCheckDialog extends DialogWrapper {
  private final Project myProject;
  @NotNull private final Splitter mainSplitter;
  @NotNull private final MethodsPanel myIsNullCheckMethodPanel;
  @NotNull private final MethodsPanel myIsNotNullCheckMethodPanel;
  @NotNull private final MethodsPanel myAssertIsNullMethodPanel;
  @NotNull private final MethodsPanel myAssertIsNotNullMethodPanel;
  @NotNull private final MethodsPanel myAssertTrueMethodPanel;
  @NotNull private final MethodsPanel myAssertFalseMethodPanel;

  public ConditionCheckDialog(Project project, String mainDialogTitle) {
    super(project, true);
    myProject = project;

    final ConditionCheckManager manager = ConditionCheckManager.getInstance(myProject);
    mainSplitter = new Splitter(true, 0.3f);
    final Splitter topThirdSplitter = new Splitter(false);
    final Splitter bottomTwoThirdsSplitter = new Splitter(true);
    final Splitter isNullIsNotNullCheckMethodSplitter = new Splitter(false);
    final Splitter assertTrueFalseMethodSplitter = new Splitter(false);

    List<ConditionChecker> isNullCheckMethods = new ArrayList<ConditionChecker>(manager.getIsNullCheckMethods());
    List<ConditionChecker> isNotNullCheckMethods = new ArrayList<ConditionChecker>(manager.getIsNotNullCheckMethods());
    List<ConditionChecker> assertIsNullMethods = new ArrayList<ConditionChecker>(manager.getAssertIsNullMethods());
    List<ConditionChecker> assertIsNotNullMethods = new ArrayList<ConditionChecker>(manager.getAssertIsNotNullMethods());
    List<ConditionChecker> assertTrueMethods = new ArrayList<ConditionChecker>(manager.getAssertTrueMethods());
    List<ConditionChecker> assertFalseMethods = new ArrayList<ConditionChecker>(manager.getAssertFalseMethods());

    myAssertIsNullMethodPanel = new MethodsPanel(assertIsNullMethods, ConditionChecker.Type.ASSERT_IS_NULL_METHOD, myProject);
    myAssertIsNotNullMethodPanel = new MethodsPanel(assertIsNotNullMethods, ConditionChecker.Type.ASSERT_IS_NOT_NULL_METHOD, myProject);
    myIsNullCheckMethodPanel = new MethodsPanel(isNullCheckMethods, ConditionChecker.Type.IS_NULL_METHOD, myProject);
    myIsNotNullCheckMethodPanel = new MethodsPanel(isNotNullCheckMethods, ConditionChecker.Type.IS_NOT_NULL_METHOD, myProject);
    myAssertTrueMethodPanel = new MethodsPanel(assertTrueMethods, ConditionChecker.Type.ASSERT_TRUE_METHOD, myProject);
    myAssertFalseMethodPanel = new MethodsPanel(assertFalseMethods, ConditionChecker.Type.ASSERT_FALSE_METHOD, myProject);

    isNullIsNotNullCheckMethodSplitter.setFirstComponent(myIsNullCheckMethodPanel.getComponent());
    isNullIsNotNullCheckMethodSplitter.setSecondComponent(myIsNotNullCheckMethodPanel.getComponent());
    assertTrueFalseMethodSplitter.setFirstComponent(myAssertTrueMethodPanel.getComponent());
    assertTrueFalseMethodSplitter.setSecondComponent(myAssertFalseMethodPanel.getComponent());

    topThirdSplitter.setFirstComponent(myAssertIsNullMethodPanel.getComponent());
    topThirdSplitter.setSecondComponent(myAssertIsNotNullMethodPanel.getComponent());
    bottomTwoThirdsSplitter.setFirstComponent(isNullIsNotNullCheckMethodSplitter);
    bottomTwoThirdsSplitter.setSecondComponent(assertTrueFalseMethodSplitter);

    mainSplitter.setFirstComponent(topThirdSplitter);
    mainSplitter.setSecondComponent(bottomTwoThirdsSplitter);

    topThirdSplitter.setPreferredSize(new Dimension(600, 150));
    bottomTwoThirdsSplitter.setPreferredSize(new Dimension(600, 300));

    myAssertIsNullMethodPanel
      .setOtherMethodsPanels(myAssertIsNotNullMethodPanel, myIsNullCheckMethodPanel, myIsNotNullCheckMethodPanel, myAssertTrueMethodPanel,
                             myAssertFalseMethodPanel);
    myAssertIsNotNullMethodPanel
      .setOtherMethodsPanels(myAssertIsNullMethodPanel, myIsNullCheckMethodPanel, myIsNotNullCheckMethodPanel, myAssertTrueMethodPanel,
                             myAssertFalseMethodPanel);
    myIsNullCheckMethodPanel
      .setOtherMethodsPanels(myAssertIsNullMethodPanel, myAssertIsNotNullMethodPanel, myIsNotNullCheckMethodPanel, myAssertTrueMethodPanel,
                             myAssertFalseMethodPanel);
    myIsNotNullCheckMethodPanel
      .setOtherMethodsPanels(myAssertIsNullMethodPanel, myAssertIsNotNullMethodPanel, myIsNullCheckMethodPanel, myAssertTrueMethodPanel,
                             myAssertFalseMethodPanel);
    myAssertTrueMethodPanel
      .setOtherMethodsPanels(myAssertIsNullMethodPanel, myAssertIsNotNullMethodPanel, myIsNotNullCheckMethodPanel, myIsNullCheckMethodPanel,
                             myAssertFalseMethodPanel);
    myAssertFalseMethodPanel
      .setOtherMethodsPanels(myAssertIsNullMethodPanel, myAssertIsNotNullMethodPanel, myIsNotNullCheckMethodPanel, myIsNullCheckMethodPanel,
                             myAssertTrueMethodPanel);

    init();
    setTitle(mainDialogTitle);
  }

  @Override
  protected JComponent createCenterPanel() {
    return mainSplitter;
  }

  @Override
  protected void doOKAction() {
    final ConditionCheckManager manager = ConditionCheckManager.getInstance(myProject);
    manager.setIsNotNullCheckMethods(myIsNotNullCheckMethodPanel.getConditionChecker());
    manager.setIsNullCheckMethods(myIsNullCheckMethodPanel.getConditionChecker());
    manager.setAssertIsNotNullMethods(myAssertIsNotNullMethodPanel.getConditionChecker());
    manager.setAssertIsNullMethods(myAssertIsNullMethodPanel.getConditionChecker());
    manager.setAssertTrueMethods(myAssertTrueMethodPanel.getConditionChecker());
    manager.setAssertFalseMethods(myAssertFalseMethodPanel.getConditionChecker());

    super.doOKAction();
  }

  /**
   * Is Null, Is Not Null, Assert True and Assert False Method Panel at the top of the main Dialog.
   */
  class MethodsPanel {
    @NotNull private final JBList myList;
    @NotNull private final JPanel myPanel;
    @NotNull private final Project myProject;
    private Set<MethodsPanel> otherPanels;

    public MethodsPanel(final List<ConditionChecker> checkers, final ConditionChecker.Type type, @NotNull final Project myProject) {
      this.myProject = myProject;
      myList = new JBList(new CollectionListModel<ConditionChecker>(checkers));
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(IdeBorderFactory.createTitledBorder(initTitle(type), false, new Insets(10, 0, 0, 0)));
      myPanel.setPreferredSize(new Dimension(400, 150));

      myList.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          String s = value.toString();
          if (s.contains("*")) {
            int indexOfAsterix1 = s.indexOf("*");
            int indexOfAsterix2 = s.lastIndexOf("*");
            if (indexOfAsterix1 >= 0 &&
                indexOfAsterix1 < s.length() &&
                indexOfAsterix2 >= 0 &&
                indexOfAsterix2 < s.length() &&
                indexOfAsterix1 < indexOfAsterix2) {
              append(s.substring(0, indexOfAsterix1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(s.substring(indexOfAsterix1 + 1, indexOfAsterix2), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
              append(s.substring(indexOfAsterix2 + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            else {
              append(s, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      });

      final ToolbarDecorator toolbarDecorator =
        ToolbarDecorator.createDecorator(myList).disableUpDownActions().setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            chooseMethod(null, type, myList.getModel().getSize());
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            CollectionListModel model = getCollectionListModel();
            if (myList.getSelectedIndex() >= 0 && myList.getSelectedIndex() < model.getSize()) {
              model.remove(myList.getSelectedIndex());
            }
          }
        });

      myList.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            int index = myList.locationToIndex(e.getPoint());
            CollectionListModel<ConditionChecker> model = getCollectionListModel();
            if (index >= 0 && model.getSize() > index) {
              chooseMethod(model.getElementAt(index), type, index);
            }
          }
        }
      });
      final JPanel panel = toolbarDecorator.createPanel();
      myPanel.add(panel);
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private String initTitle(@NotNull ConditionChecker.Type type) {
      if (type.equals(ConditionChecker.Type.IS_NULL_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.isNull.method.panel.title");
      }
      else if (type.equals(ConditionChecker.Type.IS_NOT_NULL_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.isNotNull.method.panel.title");
      }
      else if (type.equals(ConditionChecker.Type.ASSERT_IS_NULL_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.assert.isNull.method.panel.title");
      }
      else if (type.equals(ConditionChecker.Type.ASSERT_IS_NOT_NULL_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.assert.isNotNull.method.panel.title");
      }
      else if (type.equals(ConditionChecker.Type.ASSERT_TRUE_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.assert.true.method.panel.title");
      }
      else if (type.equals(ConditionChecker.Type.ASSERT_FALSE_METHOD)) {
        return InspectionsBundle.message("configure.checker.option.assert.false.method.panel.title");
      }
      else {
        throw new IllegalArgumentException("MethodCheckerDetailsDialog does not support type " + type);
      }
    }

    private void chooseMethod(@Nullable ConditionChecker checker, ConditionChecker.Type type, int index) {
      MethodCheckerDetailsDialog pickMethodPanel =
        new MethodCheckerDetailsDialog(checker, type, myProject, myPanel, getConditionCheckers(), getOtherCheckers());
      pickMethodPanel.show();
      ConditionChecker chk = pickMethodPanel.getConditionChecker();
      if (chk != null) {
        CollectionListModel<ConditionChecker> model = getCollectionListModel();
        if (model.getSize() <= index) {
          model.add(chk);
        }
        else {
          model.setElementAt(chk, index);
        }
      }
    }

    private CollectionListModel<ConditionChecker> getCollectionListModel() {
      //noinspection unchecked
      return (CollectionListModel<ConditionChecker>)myList.getModel();
    }

    @NotNull
    public JPanel getComponent() {
      return myPanel;
    }

    public List<ConditionChecker> getConditionChecker() {
      CollectionListModel<ConditionChecker> model = getCollectionListModel();
      return new ArrayList<ConditionChecker>(model.getItems());
    }

    public Set<ConditionChecker> getConditionCheckers() {
      Set<ConditionChecker> set = new HashSet<ConditionChecker>();
      set.addAll(getConditionChecker());
      return set;
    }

    public void setOtherMethodsPanels(MethodsPanel p1, MethodsPanel p2, MethodsPanel p3, MethodsPanel p4, MethodsPanel p5) {
      otherPanels = new HashSet<MethodsPanel>();
      otherPanels.add(p1);
      otherPanels.add(p2);
      otherPanels.add(p3);
      otherPanels.add(p4);
      otherPanels.add(p5);
    }

    public Set<ConditionChecker> getOtherCheckers() {
      Set<ConditionChecker> otherCheckers = new HashSet<ConditionChecker>();
      for (MethodsPanel otherPanel : otherPanels) {
        otherCheckers.addAll(otherPanel.getConditionCheckers());
      }
      return otherCheckers;
    }
  }
}
