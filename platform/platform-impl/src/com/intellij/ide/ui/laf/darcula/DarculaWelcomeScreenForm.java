/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NORTHWEST;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaWelcomeScreenForm {
  private final DarculaIntelliJWelcomeScreen myWelcomeScreen;
  private JPanel myRoot;
  private JPanel myRecentProjects;
  private JPanel myQuickStartPanel;
  private JPanel myHelpPanel;
  private ArrayList<DarculaQuickStartButton> myQuickStartButtons = new ArrayList<DarculaQuickStartButton>();
  private ArrayList<DarculaHelpButton> myHelpButtons = new ArrayList<DarculaHelpButton>();

  public DarculaWelcomeScreenForm(DarculaIntelliJWelcomeScreen welcomeScreen) {
    myWelcomeScreen = welcomeScreen;
    createRecentProjectPanel(myRoot);
    myQuickStartPanel.add(fillButtons(myRoot, true), BorderLayout.NORTH);
    JPanel p = new JPanel(); p.setOpaque(false);
    myQuickStartPanel.add(p, BorderLayout.CENTER);
    myHelpPanel.add(fillButtons(myRoot, false), BorderLayout.NORTH);
    p = new JPanel(); p.setOpaque(false);
    myHelpPanel.add(p, BorderLayout.CENTER);
  }
  private void createRecentProjectPanel(final JPanel root) {
    myRecentProjects.removeAll();
    myRecentProjects.setBorder(new EmptyBorder(0, 20, 0, 20));
    final AnAction[] recentProjectsActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);
    JLabel caption = new JLabel("Recent Projects");
    caption.setUI(DarculaWelcomeScreenLabelUI.createUI(caption));
    caption.setBorder(new EmptyBorder(10, 0, 0, 0));
    caption.setHorizontalAlignment(SwingConstants.CENTER);
    caption.setFont(new Font("Tahoma", Font.BOLD, 18));
    final Color fg = UIUtil.getPanelBackground();
    caption.setForeground(UIUtil.getPanelBackground());
    myRecentProjects.add(caption, new GridBagConstraints(0, 0, 2, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));

    int row = 1;
    for (final AnAction action : recentProjectsActions) {
      if (!(action instanceof ReopenProjectAction)) continue;

      final SimpleColoredComponent pathLabel = new SimpleColoredComponent();
      final SimpleColoredComponent nameLabel = new SimpleColoredComponent() {
        @Override
        public Dimension getPreferredSize() {
          boolean hasIcon = getIcon() != null;
          Dimension preferredSize = super.getPreferredSize();
          return new Dimension(preferredSize.width + (hasIcon ? 0 : AllIcons.Actions.CloseNew.getIconWidth() + myIconTextGap),
                               preferredSize.height);
        }

        @Override
        public Dimension getMinimumSize() {
          return getPreferredSize();
        }
      };
      nameLabel.append(String.valueOf(row) + ". ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));

      nameLabel.append(((ReopenProjectAction)action).getProjectName(),
                  new SimpleTextAttributes(/*SimpleTextAttributes.STYLE_UNDERLINE | */SimpleTextAttributes.STYLE_BOLD, null));
      nameLabel.setIconOnTheRight(true);

      String path = ((ReopenProjectAction)action).getProjectPath();
      File pathFile = new File(path);
      if (pathFile.isDirectory() && pathFile.getName().equals(((ReopenProjectAction)action).getProjectName())) {
        path = pathFile.getParent();
      }
      path = FileUtil.getLocationRelativeToUserHome(path);
      pathLabel.append("   " + path, new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null));

      nameLabel.setFont(new Font("Tahoma", Font.PLAIN, 14));
      pathLabel.setFont(new Font("Tahoma", Font.PLAIN, 8));

      for (final SimpleColoredComponent label : new SimpleColoredComponent[]{nameLabel, pathLabel}) {
      label.setForeground(fg);
      label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));


      new ClickListener() {
        @Override
        public boolean onClick(MouseEvent e, int clickCount) {
          if (e.getButton() == MouseEvent.BUTTON1) {
            DataContext dataContext = DataManager.getInstance().getDataContext(root);
            int fragment = label.findFragmentAt(e.getX());
            if (fragment == SimpleColoredComponent.FRAGMENT_ICON) {
              final int rc = Messages.showOkCancelDialog(PlatformDataKeys.PROJECT.getData(dataContext),
                                                         "Remove '" + action.getTemplatePresentation().getText() +
                                                         "' from recent projects list?",
                                                         "Remove Recent Project",
                                                         Messages.getQuestionIcon());
              if (rc == 0) {
                final RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstance();
                assert action instanceof ReopenProjectAction : action;
                manager.removePath(((ReopenProjectAction)action).getProjectPath());
                final AnAction[] actions = manager.getRecentProjectsActions(false);
                if (actions.length == 0) {
                  myRecentProjects.setVisible(false);
                }
                else {
                  for (int i = myRecentProjects.getComponentCount() - 1; i >= 0; i--) {
                    myRecentProjects.remove(i);
                  }
                  final Container parent = myRecentProjects.getParent();
                  parent.remove(myRecentProjects);
                  createRecentProjectPanel(root);
                  root.add(myRecentProjects, BorderLayout.CENTER);
                  myRecentProjects.revalidate();
                }
              }
            }
            else if (fragment != -1) {
              AnActionEvent event = new AnActionEvent(e, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
              action.actionPerformed(event);
            }
          }
          return true;
        }
      }.installOn(label);

      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          nameLabel.setIcon(AllIcons.Actions.CloseNew);
          nameLabel.setForeground(new Color(0xE09600));
          pathLabel.setForeground(new Color(0xE09600));
        }

        @Override
        public void mouseExited(MouseEvent e) {
          nameLabel.setIcon(EmptyIcon.create(AllIcons.Actions.CloseNew));
          nameLabel.setForeground(UIUtil.getPanelBackground());
          pathLabel.setForeground(UIUtil.getPanelBackground());
        }
      });
      }
      nameLabel.setIcon(EmptyIcon.create(AllIcons.Actions.CloseNew));
      nameLabel.setOpaque(false);
      pathLabel.setOpaque(false);
      nameLabel.setIconOpaque(false);
      action.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_0 + row, InputEvent.ALT_DOWN_MASK)), root,
                                       myWelcomeScreen);
      myRecentProjects.add(nameLabel,
                           new GridBagConstraints(1, 2 * row - 1, 1, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      myRecentProjects.add(pathLabel, new GridBagConstraints(1, 2*row, 1, 1, 1, 0, NORTHWEST, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      row++;
      if (row == 10) break;
    }
  }

  private JPanel fillButtons(final JPanel root, boolean quickStartActions) {
    final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance()
      .getAction(quickStartActions ? IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART : IdeActions.GROUP_WELCOME_SCREEN_DOC);
    fillActions(root, actionGroup, quickStartActions);
    final JPanel panel = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension((root.getWidth() - myRecentProjects.getPreferredSize().width) / 2, super.getPreferredSize().height+ 60);
      }

      @Override
      public Dimension getMinimumSize() {
        return new Dimension(getPreferredSize().width, super.getMinimumSize().height);
      }

      @Override
      public Dimension getMaximumSize() {
        return new Dimension(getPreferredSize().width, super.getMaximumSize().height);
      }
    };
    panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    panel.setOpaque(false);
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    if (quickStartActions) {
      for (DarculaQuickStartButton button : myQuickStartButtons) {
        panel.add(button.getComponent());
      }
    }
    else {
      for (DarculaHelpButton button : myHelpButtons) {
        panel.add(button.getComponent());
      }
    }
    return panel;
  }

  private void fillActions(JPanel root, final ActionGroup group, boolean quickStart) {
    final AnAction[] actions = group.getChildren(null);
    PresentationFactory factory = new PresentationFactory();
    for (final AnAction action : actions) {
      if (action instanceof ActionGroup) {
        final ActionGroup childGroup = (ActionGroup)action;
        fillActions(root, childGroup, quickStart);
      }
      else {
        Presentation presentation = factory.getPresentation(action);
        action.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(root),
                                        ActionPlaces.WELCOME_SCREEN, presentation, ActionManager.getInstance(), 0));
        if (presentation.isVisible()) {
          if (quickStart) {
            myQuickStartButtons.add(new DarculaQuickStartButton(action));
          } else {
            myHelpButtons.add(new DarculaHelpButton(action));
          }
        }
      }
    }
  }

  public JComponent getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myRecentProjects = new JPanel(new GridBagLayout()) {
      @Override
      public Dimension getMinimumSize() {
        return getPreferredSize();
      }

      @Override
      public Dimension getMaximumSize() {
        return getPreferredSize();
      }
    };
    myRecentProjects.setOpaque(false);
  }
}
