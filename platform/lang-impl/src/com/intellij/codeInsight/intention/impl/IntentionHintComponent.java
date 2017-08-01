/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.*;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.IntentionsConfigurable;
import com.intellij.codeInsight.intention.impl.config.IntentionsConfigurableProvider;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 */
public class IntentionHintComponent implements Disposable, ScrollAwareHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  private static final Icon ourInactiveArrowIcon = EmptyIcon.create(AllIcons.General.ArrowDown);

  private static final int NORMAL_BORDER_SIZE = 6;
  private static final int SMALL_BORDER_SIZE = 4;

  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE);
  private static final Border INACTIVE_BORDER_SMALL = BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE);
  
  private static Border createActiveBorder() {
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(getBorderColor(), 1), BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE-1, NORMAL_BORDER_SIZE-1, NORMAL_BORDER_SIZE-1));
  }
  
  private static  Border createActiveBorderSmall() {
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(getBorderColor(), 1), BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1));
  }

  private static Color getBorderColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
  }

  public boolean isVisible() {
    return myPanel.isVisible();
  }

  private final Editor myEditor;

  private static final Alarm myAlarm = new Alarm();

  private final RowIcon myHighlightedIcon;
  private final JLabel myIconLabel;

  private final RowIcon myInactiveIcon;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private volatile boolean myPopupShown;
  private boolean myDisposed;
  private volatile ListPopup myPopup;
  private final PsiFile myFile;
  private final JPanel myPanel = new JPanel() {
    @Override
    public synchronized void addMouseListener(MouseListener l) {
      // avoid this (transparent) panel consuming mouse click events
    }
  };

  private PopupMenuListener myOuterComboboxPopupListener;

  @NotNull
  public static IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                         @NotNull PsiFile file,
                                                         @NotNull Editor editor,
                                                         @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                         boolean showExpanded) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Point position = getHintPosition(editor);
    return showIntentionHint(project, file, editor, intentions, showExpanded, position);
  }

  @NotNull
  private static IntentionHintComponent showIntentionHint(@NotNull final Project project,
                                                          @NotNull PsiFile file,
                                                          @NotNull final Editor editor,
                                                          @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                          boolean showExpanded,
                                                          @NotNull Point position) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final IntentionHintComponent component = new IntentionHintComponent(project, file, editor, intentions);

    component.showIntentionHintImpl(!showExpanded, position);
    Disposer.register(project, component);
    if (showExpanded) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!editor.isDisposed() && editor.getComponent().isShowing()) {
          component.showPopup(false);
        }
      }, project.getDisposed());
    }

    return component;
  }

  @TestOnly
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposed = true;
    myComponentHint.hide();
    myPanel.hide();

    if (myOuterComboboxPopupListener != null) {
      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, myEditor.getContentComponent());
      if (ancestor != null) {
        ((JComboBox)ancestor).removePopupMenuListener(myOuterComboboxPopupListener);
      }

      myOuterComboboxPopupListener = null;
    }
  }

  @Override
  public void editorScrolled() {
    closePopup();
  }

  public boolean isForEditor(@NotNull Editor editor) {
    return editor == myEditor;
  }


  public enum PopupUpdateResult {
    NOTHING_CHANGED,    // intentions did not change
    CHANGED_INVISIBLE,  // intentions changed but the popup has not been shown yet, so can recreate list silently
    HIDE_AND_RECREATE   // ahh, has to close already shown popup, recreate and re-show again
  }

  //true if actions updated, there is nothing to do
  //false if has to recreate popup, no need to reshow
  //null if has to reshow
  @NotNull
  public PopupUpdateResult updateActions(@NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    if (myPopup.isDisposed() || !myFile.isValid()) {
      return PopupUpdateResult.HIDE_AND_RECREATE;
    }
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    if (!step.wrapAndUpdateActions(intentions, true)) {
      return PopupUpdateResult.NOTHING_CHANGED;
    }
    if (!myPopupShown) {
      return PopupUpdateResult.CHANGED_INVISIBLE;
    }
    return PopupUpdateResult.HIDE_AND_RECREATE;
  }

  @Nullable
  @TestOnly
  public IntentionAction getAction(int index) {
    if (myPopup == null || myPopup.isDisposed()) {
      return null;
    }
    ListPopupStep listStep = myPopup.getListStep();
    List<IntentionActionWithTextCaching> values = listStep.getValues();
    if (values.size() <= index) {
      return null;
    }
    return values.get(index).getAction();
  }

  public void recreate() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ListPopupStep step = myPopup.getListStep();
    recreateMyPopup(step);
  }

  private void showIntentionHintImpl(final boolean delay, @NotNull Point position) {
    final int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();

    QuestionAction action = new PriorityQuestionAction() {
      @Override
      public boolean execute() {
        showPopup(false);
        return true;
      }

      @Override
      public int getPriority() {
        return -10;
      }
    };
    if (hintManager.canShowQuestionAction(action)) {
      hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action, HintManager.ABOVE);
    }
  }

  @NotNull
  private static Point getHintPosition(Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new Point();
    final int offset = editor.getCaretModel().getOffset();
    final VisualPosition pos = editor.offsetToVisualPosition(offset);
    int line = pos.line;

    final Point position = editor.visualPositionToXY(new VisualPosition(line, 0));
    LOG.assertTrue(editor.getComponent().isDisplayable());

    JComponent convertComponent = editor.getContentComponent();

    Point realPoint;
    final boolean oneLineEditor = editor.isOneLineMode();
    if (oneLineEditor) {
      // place bulb at the corner of the surrounding component
      final JComponent contentComponent = editor.getContentComponent();
      Container ancestorOfClass = SwingUtilities.getAncestorOfClass(JComboBox.class, contentComponent);

      if (ancestorOfClass != null) {
        convertComponent = (JComponent) ancestorOfClass;
      } else {
        ancestorOfClass = SwingUtilities.getAncestorOfClass(JTextField.class, contentComponent);
        if (ancestorOfClass != null) {
          convertComponent = (JComponent) ancestorOfClass;
        }
      }

      realPoint = new Point(- (AllIcons.Actions.RealIntentionBulb.getIconWidth() / 2) - 4, - (AllIcons.Actions.RealIntentionBulb
                                                                                                .getIconHeight() / 2));
    } else {
      // try to place bulb on the same line

      int yShift = -(NORMAL_BORDER_SIZE + AllIcons.Actions.RealIntentionBulb.getIconHeight());
      if (canPlaceBulbOnTheSameLine(editor)) {
        yShift = -(NORMAL_BORDER_SIZE + (AllIcons.Actions.RealIntentionBulb.getIconHeight() - editor.getLineHeight()) / 2 + 3);
      }
      else if (position.y < editor.getScrollingModel().getVisibleArea().y + editor.getLineHeight()) {
        yShift = editor.getLineHeight() - NORMAL_BORDER_SIZE;
      }

      final int xShift = AllIcons.Actions.RealIntentionBulb.getIconWidth();

      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      realPoint = new Point(Math.max(0,visibleArea.x - xShift), position.y + yShift);
    }

    Point location = SwingUtilities.convertPoint(convertComponent, realPoint, editor.getComponent().getRootPane().getLayeredPane());
    return new Point(location.x, location.y);
  }

  private static boolean canPlaceBulbOnTheSameLine(Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode() || editor.isOneLineMode()) return false;
    final int offset = editor.getCaretModel().getOffset();
    final VisualPosition pos = editor.offsetToVisualPosition(offset);
    int line = pos.line;

    final int firstNonSpaceColumnOnTheLine = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, line);
    if (firstNonSpaceColumnOnTheLine == -1) return false;
    final Point point = editor.visualPositionToXY(new VisualPosition(line, firstNonSpaceColumnOnTheLine));
    return point.x > AllIcons.Actions.RealIntentionBulb.getIconWidth() + (editor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE) * 2;
  }

  private IntentionHintComponent(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull final Editor editor,
                                 @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myFile = file;
    myEditor = editor;

    myPanel.setLayout(new BorderLayout());
    myPanel.setOpaque(false);

    boolean showRefactoringsBulb = ContainerUtil.exists(intentions.inspectionFixesToShow,
                                                        descriptor -> descriptor.getAction() instanceof BaseRefactoringIntentionAction);
    boolean showFix = !showRefactoringsBulb && ContainerUtil.exists(intentions.errorFixesToShow,
                                                                    descriptor -> IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction()));

    Icon smartTagIcon = showRefactoringsBulb ? AllIcons.Actions.RefactoringBulb : showFix ? AllIcons.Actions.QuickfixBulb : AllIcons.Actions.IntentionBulb;

    myHighlightedIcon = new RowIcon(smartTagIcon, AllIcons.General.ArrowDown);
    myInactiveIcon = new RowIcon(smartTagIcon, ourInactiveArrowIcon);

    myIconLabel = new JLabel(myInactiveIcon);
    myIconLabel.setOpaque(false);

    myPanel.add(myIconLabel, BorderLayout.CENTER);

    myPanel.setBorder(editor.isOneLineMode() ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER);

    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
          showPopup(true);
        }
      }

      @Override
      public void mouseEntered(@NotNull MouseEvent e) {
        onMouseEnter(editor.isOneLineMode());
      }

      @Override
      public void mouseExited(@NotNull MouseEvent e) {
        onMouseExit(editor.isOneLineMode());
      }
    });

    myComponentHint = new MyComponentHint(myPanel);
    ListPopupStep step = new IntentionListStep(this, intentions, myEditor, myFile, project);
    recreateMyPopup(step);
    EditorUtil.disposeWithEditor(myEditor, this);
  }

  public void hide() {
    Disposer.dispose(this);
  }

  private void onMouseExit(final boolean small) {
    Window ancestor = SwingUtilities.getWindowAncestor(myPopup.getContent());
    if (ancestor == null) {
      myIconLabel.setIcon(myInactiveIcon);
      myPanel.setBorder(small ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER);
    }
  }

  private void onMouseEnter(final boolean small) {
    myIconLabel.setIcon(myHighlightedIcon);
    myPanel.setBorder(small ? createActiveBorderSmall() : createActiveBorder());

    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (!acceleratorsText.isEmpty()) {
      myIconLabel.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
    }
  }

  @TestOnly
  public LightweightHint getComponentHint() {
    return myComponentHint;
  }

  private void closePopup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPopup.cancel();
    myPopupShown = false;
  }

  private void showPopup(boolean mouseClick) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myPopup == null || myPopup.isDisposed()) return;

    if (mouseClick && myPanel.isShowing()) {
      final RelativePoint swCorner = RelativePoint.getSouthWestOf(myPanel);
      final int yOffset = canPlaceBulbOnTheSameLine(myEditor) ? 0 : myEditor.getLineHeight() - (myEditor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE);
      myPopup.show(new RelativePoint(swCorner.getComponent(), new Point(swCorner.getPoint().x, swCorner.getPoint().y + yOffset)));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  private void recreateMyPopup(@NotNull ListPopupStep step) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myPopup != null) {
      Disposer.dispose(myPopup);
    }
    myPopup = JBPopupFactory.getInstance().createListPopup(step);
    if (myPopup instanceof WizardPopup) {
      Shortcut[] shortcuts = getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcuts();
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          if (keyboardShortcut.getSecondKeyStroke() == null) {
            ((WizardPopup)myPopup).registerAction("activateSelectedElement", keyboardShortcut.getFirstKeyStroke(), new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                myPopup.handleSelect(true);
              }
            });
          }
        }
      }
    }

    boolean committed = PsiDocumentManager.getInstance(myFile.getProject()).isCommitted(myEditor.getDocument());
    final PsiFile injectedFile = committed ? InjectedLanguageUtil.findInjectedPsiNoCommit(myFile, myEditor.getCaretModel().getOffset()) : null;
    final Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(myEditor, injectedFile);
    
    final ScopeHighlighter highlighter = new ScopeHighlighter(myEditor);
    final ScopeHighlighter injectionHighlighter = new ScopeHighlighter(injectedEditor);
    
    myPopup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        highlighter.dropHighlight();
        injectionHighlighter.dropHighlight();
        myPopupShown = false;
      }
    });
    myPopup.addListSelectionListener(e -> {
      final Object source = e.getSource();
      highlighter.dropHighlight();
      injectionHighlighter.dropHighlight();

      if (source instanceof DataProvider) {
        final Object selectedItem = PlatformDataKeys.SELECTED_ITEM.getData((DataProvider)source);
        if (selectedItem instanceof IntentionActionWithTextCaching) {
          IntentionAction action = ((IntentionActionWithTextCaching)selectedItem).getAction();
          if (action instanceof IntentionActionDelegate) {
            action = ((IntentionActionDelegate)action).getDelegate();
          }
          if (action instanceof SuppressIntentionActionFromFix) {
            if (injectedFile != null && ((SuppressIntentionActionFromFix)action).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
              final PsiElement at = injectedFile.findElementAt(injectedEditor.getCaretModel().getOffset());
              final PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
              if (container != null) {
                injectionHighlighter.highlight(container, Collections.singletonList(container));
              }
            }
            else {
              final PsiElement at = myFile.findElementAt(myEditor.getCaretModel().getOffset());
              final PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
              if (container != null) {
                highlighter.highlight(container, Collections.singletonList(container));
              }
            }
          }
        }
      }
    });

    if (myEditor.isOneLineMode()) {
      // hide popup on combobox popup show
      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, myEditor.getContentComponent());
      if (ancestor != null) {
        final JComboBox comboBox = (JComboBox)ancestor;
        myOuterComboboxPopupListener = new PopupMenuListenerAdapter() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            hide();
          }
        };

        comboBox.addPopupMenuListener(myOuterComboboxPopupListener);
      }
    }

    Disposer.register(this, myPopup);
    Disposer.register(myPopup, ApplicationManager.getApplication()::assertIsDispatchThread);
  }

  void canceled(@NotNull ListPopupStep intentionListStep) {
    if (myPopup.getListStep() != intentionListStep || myDisposed) {
      return;
    }
    // Root canceled. Create new popup. This one cannot be reused.
    recreateMyPopup(intentionListStep);
  }

  private static class MyComponentHint extends LightweightHint {
    private boolean myVisible;
    private boolean myShouldDelay;

    private MyComponentHint(JComponent component) {
      super(component);
    }

    @Override
    public void show(@NotNull final JComponent parentComponent,
                     final int x,
                     final int y,
                     final JComponent focusBackComponent,
                     @NotNull HintHint hintHint) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> showImpl(parentComponent, x, y, focusBackComponent), DELAY);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      super.show(parentComponent, x, y, focusBackComponent, new HintHint(parentComponent, new Point(x, y)));
    }

    @Override
    public void hide() {
      super.hide();
      myVisible = false;
      myAlarm.cancelAllRequests();
    }

    @Override
    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    private void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  public static class EnableDisableIntentionAction extends AbstractEditIntentionSettingsAction {
    private final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
    private final IntentionAction myAction;

    public EnableDisableIntentionAction(IntentionAction action) {
      super(action);
      myAction = action;
      // needed for checking errors in user written actions
      //noinspection ConstantConditions
      LOG.assertTrue(myFamilyName != null, "action "+action.getClass()+" family returned null");
    }

    @Override
    @NotNull
    public String getText() {
      return CodeInsightBundle.message(mySettings.isEnabled(myAction) ? "disable.intention.action" : "enable.intention.action", myFamilyName);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myAction, !mySettings.isEnabled(myAction));
    }

    @Override
    public String toString() {
      return getText();
    }
  }

  public static class EditIntentionSettingsAction extends AbstractEditIntentionSettingsAction implements HighPriorityAction {
    public EditIntentionSettingsAction(IntentionAction action) {
      super(action);
    }

    @NotNull
    @Override
    public String getText() {
      return "Edit intention settings";
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final IntentionsConfigurable configurable = (IntentionsConfigurable)ConfigurableExtensionPointUtil
        .createApplicationConfigurableForProvider(IntentionsConfigurableProvider.class);
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> SwingUtilities.invokeLater(() -> configurable.selectIntention(myFamilyName)));
    }
  }

  private abstract static class AbstractEditIntentionSettingsAction implements IntentionAction {
    final String myFamilyName;
    private final boolean myDisabled;

    private AbstractEditIntentionSettingsAction(IntentionAction action) {
      myFamilyName = action.getFamilyName();
      myDisabled = action instanceof IntentionActionWrapper &&
                   Comparing.equal(action.getFamilyName(), ((IntentionActionWrapper)action).getFullFamilyName());
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return !myDisabled;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
