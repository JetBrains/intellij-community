// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.system.OS;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractWizard<T extends Step> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(AbstractWizard.class);

  public static final Key<AbstractWizard<?>> KEY = Key.create("AbstractWizard");

  protected int myCurrentStep;
  protected final ArrayList<T> mySteps;
  private JButton myPreviousButton;
  private JButton myNextButton;
  private JButton myCancelButton;
  private JButton myHelpButton;
  protected JPanel myContentPanel;
  protected TallImageComponent myIcon;
  private Component currentStepComponent;
  private JBCardLayout.SwipeDirection myTransitionDirection = JBCardLayout.SwipeDirection.AUTO;
  private final Map<Component, String> myComponentToIdMap = new HashMap<>();
  private final StepListener myStepListener = new StepListener() {
    @Override
    public void stateChanged() {
      updateStep();
    }
  };

  public AbstractWizard(@NlsContexts.DialogTitle String title, Component dialogParent) {
    super(dialogParent, true);
    mySteps = new ArrayList<>();
    initWizard(title);
  }

  public AbstractWizard(@NlsContexts.DialogTitle String title, @Nullable Project project) {
    super(project, true);
    mySteps = new ArrayList<>();
    initWizard(title);
  }

  private void initWizard(@NlsContexts.DialogTitle String title) {
    setTitle(title);
    myCurrentStep = 0;
    myPreviousButton = new JButton(IdeBundle.message("button.wizard.previous"));
    myNextButton = new JButton(IdeBundle.message("button.wizard.next"));
    myCancelButton = new JButton(CommonBundle.getCancelButtonText());
    myHelpButton = createHelpButton(JBInsets.emptyInsets());
    myContentPanel = new JPanel(new JBCardLayout());

    myIcon = new TallImageComponent(null);

    var rootPane = getRootPane();
    if (rootPane != null) {        // it will be null in headless mode, i.e. tests
      rootPane.registerKeyboardAction(
        e -> helpAction(),
        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );
      rootPane.registerKeyboardAction(
        e -> helpAction(),
        KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );
    }
  }

  @Override
  protected JComponent createSouthPanel() {
    if (useDialogWrapperSouthPanel()) {
      return super.createSouthPanel();
    }

    var panel = new JPanel(new BorderLayout());
    if (getStyle() == DialogStyle.COMPACT) {
      panel.setBorder(BorderFactory.createEmptyBorder(4, 15, 4, 15));
    }

    var buttonPanel = new JPanel();

    if (OS.CURRENT == OS.macOS) {
      panel.add(buttonPanel, BorderLayout.EAST);
      buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));

      if (!StartupUiUtil.INSTANCE.isDarkTheme()) {
        myHelpButton.putClientProperty("JButton.buttonType", "help");
      }

      var touchbarButtons = new ArrayList<JButton>();
      var leftPanel = new JPanel();
      leftPanel.add(myHelpButton);
      touchbarButtons.add(myHelpButton);
      leftPanel.add(myCancelButton);
      touchbarButtons.add(myCancelButton);
      panel.add(leftPanel, BorderLayout.WEST);

      var principalTouchbarButtons = new ArrayList<JButton>();
      if (mySteps.size() > 1) {
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(myPreviousButton);
        principalTouchbarButtons.add(myPreviousButton);
      }
      buttonPanel.add(Box.createHorizontalStrut(5));
      buttonPanel.add(myNextButton);
      principalTouchbarButtons.add(myNextButton);

      Touchbar.setButtonActions(panel, touchbarButtons, principalTouchbarButtons, myNextButton);
    }
    else {
      panel.add(buttonPanel, BorderLayout.CENTER);
      var layout = new GroupLayout(buttonPanel);
      buttonPanel.setLayout(layout);
      layout.setAutoCreateGaps(true);

      var hGroup = layout.createSequentialGroup();
      var vGroup = layout.createParallelGroup();
      var buttons = new ArrayList<Component>(5);

      add(hGroup, vGroup, null, Box.createHorizontalGlue());
      if (mySteps.size() > 1) {
        add(hGroup, vGroup, buttons, myPreviousButton);
      }
      add(hGroup, vGroup, buttons, myNextButton, myCancelButton);
      var leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      leftPanel.add(myHelpButton);
      panel.add(leftPanel, BorderLayout.WEST);

      layout.setHorizontalGroup(hGroup);
      layout.setVerticalGroup(vGroup);
      layout.linkSize(buttons.toArray(new Component[0]));
    }

    myPreviousButton.setEnabled(false);
    myPreviousButton.addActionListener(e -> doPreviousAction());
    myNextButton.addActionListener(e -> proceedToNextStep());
    myCancelButton.addActionListener(e -> doCancelAction());

    return panel;
  }

  protected boolean useDialogWrapperSouthPanel() { return false; }

  /**
   * Validates the current step. If the current step is valid commits it and moves the wizard to the next step.
   * Usually, should be used from UI event handlers or after deferred user interaction, e.g. validation in background thread.
   */
  @RequiresEdt
  public void proceedToNextStep() {
    if (isLastStep()) {
      // Commit data of current step and perform OK action
      Step currentStep = mySteps.get(myCurrentStep);
      LOG.assertTrue(currentStep != null);
      try {
        currentStep._commit(true);
        doOKAction();
      }
      catch (CommitStepException exc) {
        var message = exc.getMessage();
        Messages.showErrorDialog(myContentPanel, message);
      }
    }
    else {
      doNextAction();
    }
  }

  public JPanel getContentComponent() {
    return myContentPanel;
  }

  private static void add(GroupLayout.Group hGroup, GroupLayout.Group vGroup, @Nullable Collection<Component> collection, Component... components) {
    for (var component : components) {
      hGroup.addComponent(component);
      vGroup.addComponent(component);
      if (collection != null) collection.add(component);
    }
  }

  public static final class TallImageComponent extends OpaquePanel {
    private Icon icon;

    private TallImageComponent(Icon icon) {
      this.icon = icon;
    }

    @Override
    protected void paintChildren(Graphics g) {
      if (icon == null) return;

      paintIcon(g);
    }

    public void paintIcon(Graphics g) {
      if (icon == null) {
        return;
      }

      var image = ImageUtil.createImage(g, icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      var gg = image.createGraphics();
      icon.paintIcon(this, gg, 0, 0);

      var bounds = g.getClipBounds();
      var y = icon.getIconHeight() - 1;
      while (y < bounds.y + bounds.height) {
        g.drawImage(
          image,
          bounds.x, y, bounds.x + bounds.width, y + 1,
          0, icon.getIconHeight() - 1, bounds.width, icon.getIconHeight(), this
        );
        y++;
      }

      g.drawImage(image, 0, 0, this);
    }

    public void setIcon(Icon icon) {
      this.icon = icon;
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(icon != null ? icon.getIconWidth() : 0, 0);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(icon != null ? icon.getIconWidth() : 0, 0);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    var panel = new JPanel(new BorderLayout());
    panel.add(myIcon, BorderLayout.WEST);
    panel.add(myContentPanel, BorderLayout.CENTER);
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

  public void addStep(@NotNull T step) {
    addStep(step, mySteps.size());
  }

  public void addStep(@NotNull T step, int index) {
    mySteps.add(index, step);

    if (step instanceof StepAdapter) {
      ((StepAdapter)step).registerStepListener(myStepListener);
    }
    // card layout is used
    var component = step.getComponent();
    if (component != null) {
      addStepComponent(component);
    }
  }

  @Override
  protected void init() {
    super.init();
    updateStep();
  }


  protected String addStepComponent(@NotNull Component component) {
    if (component instanceof JPanel) {
      ((JPanel)component).putClientProperty(DIALOG_CONTENT_PANEL_PROPERTY, true);
    }

    var id = myComponentToIdMap.get(component);
    if (id == null) {
      id = Integer.toString(myComponentToIdMap.size());
      myComponentToIdMap.put(component, id);
      myContentPanel.add(component, id);
    }
    return id;
  }

  private void showStepComponent(Component component) {
    var id = myComponentToIdMap.get(component);
    if (id == null) {
      id = addStepComponent(component);
      myContentPanel.revalidate();
      myContentPanel.repaint();
    }
    ((JBCardLayout)myContentPanel.getLayout()).swipe(myContentPanel, id, myTransitionDirection);
  }

  protected void doPreviousAction() {
    // Commit data of current step
    var currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
    try {
      currentStep._commit(false);
    }
    catch (CommitStepException exc) {
      Messages.showErrorDialog(myContentPanel, exc.getMessage());
      return;
    }

    myCurrentStep = getPreviousStep(myCurrentStep);
    updateStep(JBCardLayout.SwipeDirection.BACKWARD);
  }

  protected final void updateStep(JBCardLayout.SwipeDirection direction) {
    //it would be better to pass 'direction' to 'updateStep' as a parameter, but since that method is used and overridden in plugins
    // we cannot do it without breaking compatibility
    try {
      myTransitionDirection = direction;
      updateStep();
    }
    finally {
      myTransitionDirection = JBCardLayout.SwipeDirection.AUTO;
    }
  }

  protected void doNextAction() {
    // Commit data of current step
    var currentStep = mySteps.get(myCurrentStep);
    LOG.assertTrue(currentStep != null);
    LOG.assertTrue(!isLastStep(), "steps: " + mySteps + " current: " + currentStep);
    try {
      currentStep._commit(false);
    }
    catch (CommitStepException exc) {
      Messages.showErrorDialog(myContentPanel, exc.getMessage());
      return;
    }

    myCurrentStep = getNextStep(myCurrentStep);
    updateStep(JBCardLayout.SwipeDirection.FORWARD);
  }

  /**
   * override this to provide alternate step order
   *
   * @param step index
   * @return the next step's index
   */
  protected int getNextStep(int step) {
    var stepCount = mySteps.size();
    if (++step >= stepCount) {
      step = stepCount - 1;
    }
    return step;
  }

  protected final int getNextStep() {
    return getNextStep(getCurrentStep());
  }

  protected T getNextStepObject() {
    var step = getNextStep();
    return mySteps.get(step);
  }

  /**
   * override this to provide alternate step order
   *
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

    var step = mySteps.get(myCurrentStep);
    LOG.assertTrue(step != null);
    step._init();
    currentStepComponent = step.getComponent();
    LOG.assertTrue(currentStepComponent != null);
    showStepComponent(currentStepComponent);

    var icon = step.getIcon();
    if (icon != null) {
      myIcon.setIcon(icon);
      myIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    }

    updateButtons();

    UiNotifyConnector.doWhenFirstShown(currentStepComponent, () -> requestFocusTo(getPreferredFocusedComponent()));
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    var step = getCurrentStepObject();
    var component = step == null ? null : step.getPreferredFocusedComponent();
    return ObjectUtils.chooseNotNull(component, myNextButton);
  }

  private static void requestFocusTo(@Nullable JComponent component) {
    if (component != null) {
      UiNotifyConnector.doWhenFirstShown(component, () -> {
        var focusManager = IdeFocusManager.findInstanceByComponent(component);
        focusManager.requestFocus(component, false);
      });
    }
  }

  protected boolean canGoNext() {
    return true;
  }

  protected boolean canFinish() {
    return isLastStep() && canGoNext();
  }

  protected void updateButtons() {
    var lastStep = isLastStep();
    updateButtons(lastStep, lastStep ? canFinish() : canGoNext(), isFirstStep());
  }

  public void updateWizardButtons() {
    if (!mySteps.isEmpty() && getRootPane() != null) {
      updateButtons();
    }
  }

  public void updateButtons(boolean lastStep, boolean canGoNext, boolean firstStep) {
    if (lastStep) {
      if (mySteps.size() > 1) {
        myNextButton.setText(UIUtil.removeMnemonic(IdeBundle.message("button.create")));
        myNextButton.setMnemonic(KeyEvent.VK_C);
      }
      else {
        myNextButton.setText(IdeBundle.message("button.ok"));
      }
    }
    else {
      myNextButton.setText(UIUtil.removeMnemonic(IdeBundle.message("button.wizard.next")));
      myNextButton.setMnemonic(KeyEvent.VK_N);
    }
    myNextButton.setEnabled(canGoNext);

    if (myNextButton.isEnabled() && !ApplicationManager.getApplication().isUnitTestMode() && getRootPane() != null) {
      getRootPane().setDefaultButton(myNextButton);
    }

    myPreviousButton.setEnabled(!firstStep);
    myPreviousButton.setVisible(!firstStep);
  }

  /** @deprecated always {@code true} */
  @Deprecated(forRemoval = true)
  public static boolean isNewWizard() {
    return true;
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

  public Component getCurrentStepComponent() {
    return currentStepComponent;
  }

  protected void helpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  @Override
  protected Action @NotNull [] createActions() {
    if (useDialogWrapperSouthPanel()) {
      throw new UnsupportedOperationException("Not implemented");
    }
    return super.createActions();
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(getHelpId());
  }

  protected int getNumberOfSteps() {
    return mySteps.size();
  }

  @ApiStatus.Internal
  public void setStepListener(Collection<T> steps) {
    for (var step : steps) {
      if (step instanceof StepAdapter sa) {
        sa.registerStepListener(myStepListener);
      }
    }
  }

  @Override
  protected @Nullable String getHelpId() {
    return getHelpID();
  }

  /** @deprecated use {@link #getHelpId()} instead */
  @Deprecated(forRemoval = true)
  protected @Nullable String getHelpID() {
    return null;
  }
}
