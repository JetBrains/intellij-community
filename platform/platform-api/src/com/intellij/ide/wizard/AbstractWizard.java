/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractWizard<T extends Step> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.wizard.AbstractWizard");

  protected int myCurrentStep;
  protected final ArrayList<T> mySteps;
  private JButton myPreviousButton;
  private JButton myNextButton;
  private JButton myCancelButton;
  private JButton myHelpButton;
  protected JPanel myContentPanel;
  protected TallImageComponent myIcon;
  private Component myCurrentStepComponent;
  private final Map<Component, String> myComponentToIdMap = new HashMap<>();
  private final StepListener myStepListener = new StepListener() {
    public void stateChanged() {
      updateStep();
    }
  };

  public AbstractWizard(final String title, final Component dialogParent) {
    super(dialogParent, true);
    mySteps = new ArrayList<>();
    initWizard(title);
  }

  public AbstractWizard(final String title, @Nullable final Project project) {
    super(project, true);
    mySteps = new ArrayList<>();
    initWizard(title);
  }

  private void initWizard(final String title) {
    setTitle(title);
    myCurrentStep = 0;
    myPreviousButton = new JButton(IdeBundle.message("button.wizard.previous"));
    myNextButton = new JButton(IdeBundle.message("button.wizard.next"));
    myCancelButton = new JButton(CommonBundle.getCancelButtonText());
    myHelpButton = new JButton(CommonBundle.getHelpButtonText());
    myContentPanel = new JPanel(new JBCardLayout());

    myIcon = new TallImageComponent(null);

    JRootPane rootPane = getRootPane();
    if (rootPane != null) {        // it will be null in headless mode, i.e. tests
      rootPane.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            helpAction();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );

      rootPane.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            helpAction();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );
    }
  }

  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

    JPanel buttonPanel = new JPanel();

    if (SystemInfo.isMac) {
      panel.add(buttonPanel, BorderLayout.EAST);
      buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

      if (!UIUtil.isUnderDarcula()) {
        myHelpButton.putClientProperty("JButton.buttonType", "help");
      }
      if (UIUtil.isUnderAquaLookAndFeel()) {
        myHelpButton.setText("");
      }

      JPanel leftPanel = new JPanel();
      if (ApplicationInfo.contextHelpAvailable()) {
        leftPanel.add(myHelpButton);
      }
      leftPanel.add(myCancelButton);
      panel.add(leftPanel, BorderLayout.WEST);

      if (mySteps.size() > 1) {
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(myPreviousButton);
      }
      buttonPanel.add(Box.createHorizontalStrut(5));
      buttonPanel.add(myNextButton);
    }
    else {
      panel.add(buttonPanel, BorderLayout.CENTER);
      GroupLayout layout = new GroupLayout(buttonPanel);
      buttonPanel.setLayout(layout);
      layout.setAutoCreateGaps(true);

      final GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup();
      final GroupLayout.ParallelGroup vGroup = layout.createParallelGroup();
      final Collection<Component> buttons = ContainerUtil.newArrayListWithCapacity(5);
      final boolean helpAvailable = ApplicationInfo.contextHelpAvailable();

      if (helpAvailable && UIUtil.isUnderGTKLookAndFeel()) {
        add(hGroup, vGroup, buttons, myHelpButton);
      }
      add(hGroup, vGroup, null, Box.createHorizontalGlue());
      if (mySteps.size() > 1) {
        add(hGroup, vGroup, buttons, myPreviousButton);
      }
      add(hGroup, vGroup, buttons, myNextButton, myCancelButton);
      if (helpAvailable && !UIUtil.isUnderGTKLookAndFeel()) {
        add(hGroup, vGroup, buttons, myHelpButton);
      }

      layout.setHorizontalGroup(hGroup);
      layout.setVerticalGroup(vGroup);
      layout.linkSize(buttons.toArray(new Component[buttons.size()]));
    }

    myPreviousButton.setEnabled(false);
    myPreviousButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        doPreviousAction();
      }
    });
    myNextButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (isLastStep()) {
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
        else {
          doNextAction();
        }
      }
    });

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

  public JPanel getContentComponent() {
    return myContentPanel;
  }

  private static void add(final GroupLayout.Group hGroup,
                          final GroupLayout.Group vGroup,
                          @Nullable final Collection<Component> collection,
                          final Component... components) {
    for (Component component : components) {
      hGroup.addComponent(component);
      vGroup.addComponent(component);
      if (collection != null) collection.add(component);
    }
  }

  public static class TallImageComponent extends OpaquePanel {
    private Icon myIcon;

    private TallImageComponent(Icon icon) {
      myIcon = icon;
    }

    @Override
    protected void paintChildren(Graphics g) {
      if (myIcon == null) return;

      paintIcon(g);
    }

    public void paintIcon(Graphics g) {
      if (myIcon == null) {
        return;
      }
      final BufferedImage image = UIUtil.createImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D gg = image.createGraphics();
      myIcon.paintIcon(this, gg, 0, 0);

      final Rectangle bounds = g.getClipBounds();
      int y = myIcon.getIconHeight()-1;
      while (y < bounds.y + bounds.height) {
        g.drawImage(image,
                    bounds.x, y, bounds.x + bounds.width, y + 1,
                    0, myIcon.getIconHeight() - 1, bounds.width, myIcon.getIconHeight(), this);

        y++;
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

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(myIcon != null ? myIcon.getIconWidth() : 0, 0);
    }
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myContentPanel, BorderLayout.CENTER);
    panel.add(myIcon, BorderLayout.WEST);
    return panel;
  }

  public int getCurrentStep() {
    return myCurrentStep;
  }

  public int getStepCount() {
    return mySteps.size();
  }

  public T getCurrentStepObject() {
    return mySteps.get(myCurrentStep);
  }

  public void addStep(@NotNull final T step) {
    addStep(step, mySteps.size());
  }

  public void addStep(@NotNull final T step, int index) {
    mySteps.add(index, step);

    if (step instanceof StepAdapter) {
      ((StepAdapter)step).registerStepListener(myStepListener);
    }
    // card layout is used
    final Component component = step.getComponent();
    if (component != null) {
      addStepComponent(component);
    }
  }

  protected void init() {
    super.init();
    updateStep();
  }


  protected String addStepComponent(final Component component) {
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
    ((JBCardLayout)myContentPanel.getLayout()).swipe(myContentPanel, id, JBCardLayout.SwipeDirection.AUTO);
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
    LOG.assertTrue(!isLastStep(), "steps: " + mySteps + " current: " + currentStep);
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
    if (mySteps.isEmpty()) {
      return;
    }
    
    final Step step = mySteps.get(myCurrentStep);
    LOG.assertTrue(step != null);
    step._init();
    myCurrentStepComponent = step.getComponent();
    LOG.assertTrue(myCurrentStepComponent != null);
    showStepComponent(myCurrentStepComponent);

    Icon icon = step.getIcon();
    if (icon != null) {
      myIcon.setIcon(icon);
      myIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    }

    updateButtons();

    JComponent component = mySteps.get(getCurrentStep()).getPreferredFocusedComponent();
    requestFocusTo(component != null ? component : myNextButton);
  }

  private static void requestFocusTo(final JComponent component) {
    UiNotifyConnector.doWhenFirstShown(component, () -> {
      final IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(component);
      focusManager.requestFocus(component, false);
    });
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    JComponent component = getCurrentStepObject().getPreferredFocusedComponent();
    return component == null ? super.getPreferredFocusedComponent() : component;
  }

  protected boolean canGoNext() {
    return true;
  }

  protected boolean canFinish() {
    return isLastStep() && canGoNext();
  }

  protected void updateButtons() {
    boolean lastStep = isLastStep();
    updateButtons(lastStep, lastStep ? canFinish() : canGoNext(), isFirstStep());
  }

  public void updateWizardButtons() {
    if (!mySteps.isEmpty() && getRootPane() != null)
      updateButtons();
  }

  public void updateButtons(boolean lastStep, boolean canGoNext, boolean firstStep) {
    if (lastStep) {
      if (mySteps.size() > 1) {
        myNextButton.setText(UIUtil.removeMnemonic(IdeBundle.message("button.finish")));
        myNextButton.setMnemonic('F');
      }
      else {
        myNextButton.setText(IdeBundle.message("button.ok"));
      }
      myNextButton.setEnabled(canGoNext);
    }
    else {
      myNextButton.setText(UIUtil.removeMnemonic(IdeBundle.message("button.wizard.next")));
      myNextButton.setMnemonic('N');
      myNextButton.setEnabled(canGoNext);
    }

    if (myNextButton.isEnabled() && !ApplicationManager.getApplication().isUnitTestMode()) {
      getRootPane().setDefaultButton(myNextButton);
    }

    myPreviousButton.setEnabled(!firstStep);
  }

  protected boolean isFirstStep() {
    return myCurrentStep == 0;
  }

  protected boolean isLastStep() {
    return myCurrentStep == mySteps.size() - 1 || getCurrentStep() == getNextStep(getCurrentStep());
  }

  protected JButton getNextButton() {
    return myNextButton;
  }

  protected JButton getPreviousButton() {
    return myPreviousButton;
  }

  protected JButton getHelpButton() {
    return myHelpButton;
  }

  public JButton getCancelButton() {
    return myCancelButton;
  }

  @Deprecated
  protected JButton getFinishButton() {
    return new JButton();
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
}
