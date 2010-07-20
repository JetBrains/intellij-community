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
package com.intellij.ide.wizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CommandButtonGroup;
import com.intellij.ui.components.panels.OpaquePanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractWizard<T extends Step> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.wizard.AbstractWizard");

  protected int myCurrentStep;
  protected final ArrayList<T> mySteps;
  private JButton myPreviousButton;
  private JButton myNextButton;
  private JButton myFinishButton;
  private JButton myCancelButton;
  private JButton myHelpButton;
  private JPanel myContentPanel;
  private TallImageComponent myIcon;
  private Component myCurrentStepComponent;
  private final Map<Component, String> myComponentToIdMap = new HashMap<Component, String>();
  private final StepListener myStepListener = new StepListener() {
    public void stateChanged() {
      updateStep();
    }
  };

  public AbstractWizard(final String title, final Component dialogParent) {
    super(dialogParent, true);
    mySteps = new ArrayList<T>();
    initWizard(title);
  }

  public AbstractWizard(final String title, final Project project) {
    super(project, true);
    mySteps = new ArrayList<T>();
    initWizard(title);
  }

  private void initWizard(final String title) {
    setTitle(title);
    myCurrentStep = 0;
    myPreviousButton = new JButton(IdeBundle.message("button.wizard.previous"));
    myNextButton = new JButton(IdeBundle.message("button.wizard.next"));
    myFinishButton = new JButton(IdeBundle.message("button.finish"));
    myCancelButton = new JButton(CommonBundle.getCancelButtonText());
    myHelpButton = new JButton(CommonBundle.getHelpButtonText());
    myContentPanel = new JPanel(new CardLayout());

    myIcon = new TallImageComponent(null);

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          helpAction();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          helpAction();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  protected JComponent createSouthPanel() {
    final CommandButtonGroup panel = new CommandButtonGroup();
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

    if (mySteps.size() > 1) {
      panel.addButton(myPreviousButton);
      panel.addButton(myNextButton);
    }
    panel.addButton(myFinishButton);
    panel.addButton(myCancelButton);
    panel.addButton(myHelpButton);

    myPreviousButton.setEnabled(false);
    myPreviousButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doPreviousAction();
      }
    });
    myNextButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doNextAction();
      }
    });
    myFinishButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          // Commit data of current step and perform OK action
          final Step currentStep = mySteps.get(myCurrentStep);
          LOG.assertTrue(currentStep != null);
          try {
            currentStep._commit(true);
            doOKAction();
          }
          catch (final CommitStepException exc) {
            String message = exc.getMessage();
            if (message != null) {
              Messages.showErrorDialog(myContentPanel, message);
            }
          }
        }
      }
    );
    myCancelButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          doCancelAction();
        }
      }
    );
    myHelpButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        helpAction();
      }
    });

    return panel;
  }

  private static class TallImageComponent extends OpaquePanel {
    private Icon myIcon;

    private TallImageComponent(Icon icon) {
      myIcon = icon;
    }

    @Override
    protected void paintChildren(Graphics g) {
      if (myIcon == null) return;

      final BufferedImage image = new BufferedImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D gg = image.createGraphics();
      myIcon.paintIcon(this, gg, 0, 0);

      final Rectangle bounds = g.getClipBounds();
      int y = bounds.y;
      while (y < bounds.y + bounds.height) {
        g.drawImage(image,
                    bounds.x, y, bounds.x + bounds.width, y + 5,
                    0, myIcon.getIconHeight() - 10, bounds.width, myIcon.getIconHeight() - 5, this);

        y += 5;
      }


      g.drawImage(image, 0, 0, this);
    }

    public void setIcon(Icon icon) {
      myIcon = icon;
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myIcon != null ? myIcon.getIconWidth() : 0, 0);
    }
  }

  protected JComponent createCenterPanel() {
    JPanel iconPanel = new JPanel(new BorderLayout());
    iconPanel.setBorder(new EmptyBorder(0, 5, 0, 5));
    iconPanel.add(myIcon, BorderLayout.CENTER);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(iconPanel, BorderLayout.WEST);
    panel.add(myContentPanel, BorderLayout.CENTER);
    return panel;
  }

  public int getCurrentStep() {
    return myCurrentStep;
  }

  protected T getCurrentStepObject() {
    return mySteps.get(myCurrentStep);
  }

  public void addStep(@NotNull final T step) {
    mySteps.add(step);

    if (step instanceof StepAdapter) {
      ((StepAdapter)step).registerStepListener(myStepListener);
    }
    // card layout is used
    final Component component = step.getComponent();
    if (component != null) {
      addStepComponent(component);
    }

    if (mySteps.size() > 1) {
      myFinishButton.setText(IdeBundle.message("button.finish"));
    }
    else {
      myFinishButton.setText(IdeBundle.message("button.ok"));
    }
  }

  protected void init() {
    super.init();
    updateStep();
  }


  private String addStepComponent(final Component component) {
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = Integer.toString(myComponentToIdMap.size());
      myComponentToIdMap.put(component, id);
      myContentPanel.add(component, id);
    }
    return id;
  }

  private void showStepComponent(final Component component) {
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = addStepComponent(component);
      myContentPanel.revalidate();
      myContentPanel.repaint();
    }
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, id);
  }

  protected void doPreviousAction() {
    // Commit data of current step
    final Step currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
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

    myCurrentStep = getPreviousStep(myCurrentStep);
    updateStep();
  }

  protected void doNextAction() {
    // Commit data of current step
    final Step currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
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

    myCurrentStep = getNextStep(myCurrentStep);
    updateStep();
  }

  /**
   * override this to provide alternate step order
   * @param step index
   * @return the next step's index
   */
  protected int getNextStep(int step) {
    final int stepCount = mySteps.size();
    if (++step >= stepCount) {
      step = stepCount - 1;
    }
    return step;
  }

  protected final int getNextStep() {
    return getNextStep(getCurrentStep());
  }

  protected T getNextStepObject() {
    int step = getNextStep();
    return mySteps.get(step);
  }

  /**
   * override this to provide alternate step order
   * @param step index
   * @return the previous step's index
   */
  protected int getPreviousStep(int step) {
    if (--step < 0) {
      step = 0;
    }
    return step;
  }

  protected final int getPreviousStep() {
    return getPreviousStep(getCurrentStep());
  }

  protected void updateStep() {
    if (mySteps.size() == 0) {
      return;
    }

    final Step step = mySteps.get(myCurrentStep);
    LOG.assertTrue(step != null);
    step._init();
    myCurrentStepComponent = step.getComponent();
    LOG.assertTrue(myCurrentStepComponent != null);
    showStepComponent(myCurrentStepComponent);

    myIcon.setIcon(step.getIcon());

    myNextButton.setEnabled(mySteps.size() == 1 || myCurrentStep < mySteps.size() - 1);
    myPreviousButton.setEnabled(myCurrentStep > 0);
  }

  protected JButton getNextButton() {
    return myNextButton;
  }

  protected JButton getPreviousButton() {
    return myPreviousButton;
  }

  protected JButton getFinishButton() {
    return myFinishButton;
  }

  public Component getCurrentStepComponent() {
    return myCurrentStepComponent;
  }

  protected void helpAction() {
    HelpManager.getInstance().invokeHelp(getHelpID());
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpID());
  }

  protected int getNumberOfSteps() {
    return mySteps.size();
  }

  @Nullable
  @NonNls
  protected abstract String getHelpID();

  protected boolean isCurrentStep(final T step) {
    return step != null && getCurrentStepComponent() == step.getComponent();
  }
}
