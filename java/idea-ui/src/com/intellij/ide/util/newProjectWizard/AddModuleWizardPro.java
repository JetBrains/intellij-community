package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;

/**
 * @author Konstantin Bulenkov
 */
public class AddModuleWizardPro extends AddModuleWizard {
  protected JPanel myStepsPanel;

  public AddModuleWizardPro(Project project, @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(project, modulesProvider, defaultPath);
    getWizardContext().addContextListener(new WizardContext.Listener() {
      @Override
      public void buttonsUpdateRequested() {
        updateStepsPanel();
      }

      @Override
      public void nextStepRequested() {
        updateStepsPanel();
      }
    });
  }

  @Override
  protected void updateButtons() {
    super.updateButtons();
    if (isLastStep()) {
      getNextButton().setText("Done");
    }

    getPreviousButton().setVisible(false);
    getCancelButton().setVisible(false);
  }

  public AddModuleWizardPro(Component parent, Project project, @NotNull ModulesProvider modulesProvider) {
    super(parent, project, modulesProvider);
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
  }

  @Override
  protected JComponent createCenterPanel() {
    myStepsPanel = new JPanel() {
      @Override
      public void addNotify() {
        super.addNotify();
        getNextStepObject().updateDataModel();
        updateStepsPanel();
        new AnAction(){
          @Override
          public void actionPerformed(AnActionEvent e) {
            if (!isLastStep()) doNextAction();
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("control TAB"), getContentPanel(), getDisposable());
        new AnAction(){
          @Override
          public void actionPerformed(AnActionEvent e) {
            doPreviousAction();
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("control shift TAB"), getContentPanel(), getDisposable());
        new AnAction(){
          @Override
          public void actionPerformed(AnActionEvent e) {
            myCurrentStep = mySteps.size() - 1;
            doOKAction();
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("control shift ENTER"), getContentPanel(), getDisposable());
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final Component c = getComponent(getComponentCount() - 1);
        int y = c.getBounds().y + c.getBounds().height + 4;
        g.translate(0, y);
        myIcon.paintIcon(g);
        g.translate(0, -y);
      }
    };
    myStepsPanel.setLayout(new BoxLayout(myStepsPanel, BoxLayout.Y_AXIS) {
      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int maxWidth = -1;
        for (Component c : target.getComponents()) {
          maxWidth = Math.max(maxWidth, c.getWidth());
        }
        for (Component c : target.getComponents()) {
          final Rectangle b = c.getBounds();
          c.setBounds(b.x, b.y, maxWidth, b.height);
        }
      }
    });
    myStepsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    updateStepsPanel();

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myStepsPanel, BorderLayout.WEST);
    panel.add(myContentPanel, BorderLayout.CENTER);
    return panel;

  }

  @Override
  public void doNextAction() {
    super.doNextAction();
    updateStepsPanel();
  }

  @Override
  protected void doPreviousAction() {
    super.doPreviousAction();
    updateStepsPanel();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    final ValidationInfo result = super.doValidate();
    updateStepsPanel();
    return result;
  }

  private void updateStepsPanel() {
    myStepsPanel.removeAll();
    int index = 0;
    final ButtonGroup group = new ButtonGroup();
    while (index != -1) {
      final int ind = index;
      final ModuleWizardStep step = mySteps.get(index);
      final JRadioButton rb = new JRadioButton(step.getName(), index == myCurrentStep);
      rb.setFocusable(false);
      rb.setUI(new WizardArrowUI(rb, index < myCurrentStep));
      myStepsPanel.add(rb);
      group.add(rb);
      rb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final ModuleWizardStep step = getCurrentStepObject();
          if (ind > getCurrentStep() && !commitStepData(step)) {
            updateStepsPanel();
            return;
          }
          step.onStepLeaving();

          // Commit data of current step
          final Step currentStep = mySteps.get(myCurrentStep);
          try {
            currentStep._commit(false);
          }
          catch (final CommitStepException exc) {
            Messages.showErrorDialog(
              myContentPanel,
              exc.getMessage()
            );
            return;
          }

          myCurrentStep = ind;
          updateStep();

        }
      });

      final int next = getNextStep(index);
      index = index == next ? -1 : next;
    }

    final Enumeration<AbstractButton> buttons = group.getElements();
    while (buttons.hasMoreElements()) {
      final JRadioButton b = (JRadioButton)buttons.nextElement();
      b.setUI(new WizardArrowUI(b, index < myCurrentStep));
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myStepsPanel.revalidate();
        myStepsPanel.repaint();
      }
    });
  }
}
