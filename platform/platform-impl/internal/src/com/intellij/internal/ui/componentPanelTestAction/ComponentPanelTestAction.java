// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentPanelTestAction;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.SplitButtonAction;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsActions;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;

final class ComponentPanelTestAction extends DumbAwareAction {
  private enum Placement {
    Top(SwingConstants.TOP, "Top"),
    Bottom(SwingConstants.BOTTOM, "Bottom"),
    Left(SwingConstants.LEFT, "Left"),
    Right(SwingConstants.RIGHT, "Right");

    private final String name;

    @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
    private final int placement;

    Placement(
      @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
      int placement,
      String name) {
      this.name = name;
      this.placement = placement;
    }

    @Override
    public String toString() {
      return name;
    }

    @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.LEFT, SwingConstants.RIGHT})
    public int placement() {
      return placement;
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new ComponentPanelTest(project).show();
    }
  }

  @SuppressWarnings({"MethodMayBeStatic", "UseOfSystemOutOrSystemErr"})
  private static final class ComponentPanelTest extends DialogWrapper {

    private final Alarm myAlarm = new Alarm(getDisposable());
    private ProgressTimerRequest progressTimerRequest;

    private JTabbedPane   pane;
    private final Project project;

    private ComponentPanelTest(Project project) {
      super(project);

      this.project = project;

      init();
      setTitle("Component Panel Test Action");
    }

    @Override
    protected @NotNull JComponent createCenterPanel() {
      pane = new JBTabbedPane(SwingConstants.TOP);
      ProgressPanelResult progressResult = ProgressPanelKt.createProgressPanel(myAlarm);
      progressTimerRequest = progressResult.timerRequest;

      pane.addTab("Component", ComponentPanelKt.createComponentPanel(project, getDisposable(), pane));
      pane.addTab("Progress", progressResult.panel);
      pane.addTab("Validators", ValidatorsPanelKt.createValidatorsPanel(project, getDisposable()));
      pane.addTab("Multilines", createMultilinePanel());

      pane.addChangeListener(_ -> {
        if (pane.getSelectedIndex() == 4) {
          myAlarm.addRequest(progressTimerRequest, 200, ModalityState.any());
        } else {
          myAlarm.cancelRequest(progressTimerRequest);
        }
      });

      BorderLayoutPanel panel = JBUI.Panels.simplePanel(pane);

      panel.addToTop(createToolbar(pane));

      JPanel southPanel = new JPanel();
      southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));

      JCheckBox enabledCB = new JCheckBox("Enable TabPane", true);
      enabledCB.addActionListener(_ -> pane.setEnabled(enabledCB.isSelected()));
      southPanel.add(enabledCB);

      southPanel.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));

      JComboBox<Placement> placementCombo = new ComboBox<>(Placement.values());
      placementCombo.setSelectedIndex(0);
      placementCombo.addActionListener(_ -> {
        Placement p = (Placement)placementCombo.getSelectedItem();
        if (p != null) pane.setTabPlacement(p.placement());
      });
      southPanel.add(placementCombo);
      southPanel.add(new Box.Filler(JBUI.size(0), JBUI.size(0), JBUI.size(Integer.MAX_VALUE, 0)));

      BrowserLink externalLink = new BrowserLink("External link", "https://google.com");
      southPanel.add(externalLink);
      panel.addToBottom(southPanel);

      return panel;
    }

    private JComponent createMultilinePanel() {
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START,
                                                     GridBagConstraints.HORIZONTAL, JBUI.insets(10, 0, 0, 4), 0, 0);

      panel.add(new JLabel("Label one:"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("<html>Multiline<br/>html<br/>checkbox</html>"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("<html>Single line html checkbox</html>"), gc);

      gc.gridx++;
      panel.add(new JCheckBox("Single line checkbox"), gc);

      gc.gridx++;
      panel.add(new JButton("Button 1"), gc);

      gc.gridy++;
      gc.gridx = 0;
      panel.add(new JLabel("Label two:"), gc);

      ButtonGroup bg = new ButtonGroup();
      JRadioButton rb = new JRadioButton("<html>Multiline<br/>html<br/>radiobutton</html>");
      bg.add(rb);
      rb.setSelected(true);

      gc.gridx++;
      panel.add(rb, gc);

      rb = new JRadioButton("<html>Single line html radiobutton</html>");
      bg.add(rb);

      gc.gridx++;
      panel.add(rb, gc);

      rb = new JRadioButton("Single line radiobutton");
      bg.add(rb);

      gc.gridx++;
      panel.add(rb, gc);

      gc.gridx++;
      panel.add(new JButton("Button 2"), gc);

      gc.gridy++;
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.PAGE_END;
      gc.fill = GridBagConstraints.BOTH;
      gc.weightx = 1.0;
      gc.weighty = 1.0;
      gc.gridwidth = 5;
      panel.add(new JPanel(), gc);

      return JBUI.Panels.simplePanel().addToTop(panel);
    }

    private int counter = 5;

    private JComponent createToolbar(@NotNull JComponent toolbarTarget) {
      boolean[] enabledArray = new boolean[3];
      Arrays.fill(enabledArray, true);
      AnAction[] actionsArray = new AnAction[3];
      actionsArray[0] = new MyAction("Play", AllIcons.Actions.Execute) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(enabledArray[0]);
        }
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (--counter == 0) {
            enabledArray[0] = false;
          }
          System.out.println(e.getPresentation().getDescription() + ", counter = " + counter);
        }
      };

      actionsArray[1] = new MyAction("Stop", AllIcons.Actions.Suspend) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(enabledArray[1]);
        }
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          counter = 5;
          enabledArray[0] = true;
          System.out.println(e.getPresentation().getDescription() + ", counter = " + counter);
        }
      };

      actionsArray[2] = new MyToggleAction("Mute", AllIcons.Debugger.MuteBreakpoints) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(enabledArray[2]);
        }
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          selected = !selected;
          if (selected) {
            System.out.println("Unmute buttons");
            enabledArray[0] = true;
            enabledArray[1] = true;
          }
          else {
            System.out.println("Mute buttons");
            enabledArray[0] = false;
            enabledArray[1] = false;
          }

          Toggleable.setSelected(e.getPresentation(), selected);
        }
      };

      DefaultActionGroup actions = DefaultActionGroup.createFlatGroup(() -> "Simple Group");
      actions.addAll(actionsArray);

      DefaultActionGroup subActions = DefaultActionGroup.createPopupGroup(() -> "Ratings");
      subActions.getTemplatePresentation().setIcon(AllIcons.Ide.Rating);
      subActions.addAll(new MyAction("Rating One", AllIcons.Ide.Rating1).withDefaultDescription(),
                        new MyAction("Rating Two", AllIcons.Ide.Rating2).withDefaultDescription(),
                        new MyAction("Rating Three", AllIcons.Ide.Rating3).withDefaultDescription(),
                        new MyAction("Rating Four", AllIcons.Ide.Rating4).withDefaultDescription());
      actions.add(subActions);

      DefaultActionGroup toolbarActions = new DefaultActionGroup();
      toolbarActions.add(new SplitButtonAction(actions));
      toolbarActions.add(new MyAction("Short", AllIcons.Ide.Rating1) {
        {
          GotItTooltip actionGotIt = new GotItTooltip("short.action", "Short action text", project).withHeader("Header");
          actionGotIt.assignTo(getTemplatePresentation(),
                               GotItTooltip.BOTTOM_MIDDLE);
        }
      }.withShortCut("control K"));
      toolbarActions.add(new MyAction("Long", AllIcons.Ide.Rating2).withShortCut("control N"));
      toolbarActions.add(new MyAction(null, AllIcons.Ide.Rating3).withShortCut("control P"));

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", toolbarActions, true);
      toolbar.setTargetComponent(toolbarTarget);
      JComponent toolbarComponent = toolbar.getComponent();
      toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
      return toolbarComponent;
    }
  }

  private static class MyAction extends DumbAwareAction {
    private MyAction(@Nullable @NlsActions.ActionText String name, @Nullable Icon icon) {
      super(name, null, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      System.out.println(e.getPresentation().getDescription());
    }

    public MyAction withDefaultDescription() {
      getTemplatePresentation().setDescription(getTemplateText() + " description");
      return this;
    }

    public MyAction withShortCut(@NotNull String shortCut) {
      setShortcutSet(CustomShortcutSet.fromString(shortCut));
      return this;
    }
  }

  private static class MyToggleAction extends MyAction implements Toggleable {
    protected boolean selected;
    private MyToggleAction(String name, Icon icon) {
      super(name, icon);
    }
  }
}
