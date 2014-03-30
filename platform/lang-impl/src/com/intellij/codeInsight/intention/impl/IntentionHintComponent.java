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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.PriorityQuestionAction;
import com.intellij.codeInsight.hint.ScrollAwareHint;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 * @author and me too (Chinee?)
 */
public class IntentionHintComponent extends JPanel implements Disposable, ScrollAwareHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  static final Icon ourInactiveArrowIcon = new EmptyIcon(AllIcons.General.ArrowDown.getIconWidth(), AllIcons.General.ArrowDown.getIconHeight());

  private static final int NORMAL_BORDER_SIZE = 6;
  private static final int SMALL_BORDER_SIZE = 4;

  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE);
  private static final Border ACTIVE_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE-1, NORMAL_BORDER_SIZE-1, NORMAL_BORDER_SIZE-1));

  private static final Border INACTIVE_BORDER_SMALL = BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE);
  private static final Border ACTIVE_BORDER_SMALL = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1, SMALL_BORDER_SIZE-1));

  private final Editor myEditor;

  private static final Alarm myAlarm = new Alarm();

  private final RowIcon myHighlightedIcon;
  private final JLabel myIconLabel;

  private final RowIcon myInactiveIcon;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private volatile boolean myPopupShown = false;
  private boolean myDisposed = false;
  private volatile ListPopup myPopup;
  private final PsiFile myFile;

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
  public static IntentionHintComponent showIntentionHint(@NotNull final Project project,
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
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!editor.isDisposed() && editor.getComponent().isShowing()) {
            component.showPopup();
          }
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
    super.hide();

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

  //true if actions updated, there is nothing to do
  //false if has to recreate popup, no need to reshow
  //null if has to reshow
  public Boolean updateActions(@NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    if (myPopup.isDisposed()) return null;
    if (!myFile.isValid()) return null;
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    if (!step.updateActions(intentions)) {
      return Boolean.TRUE;
    }
    if (!myPopupShown) {
      return Boolean.FALSE;
    }
    return null;
  }

  // for using in tests !
  @Nullable
  public IntentionAction getAction(int index) {
    if (myPopup == null || myPopup.isDisposed()) {
      return null;
    }
    IntentionListStep listStep = (IntentionListStep)myPopup.getListStep();
    List<IntentionActionWithTextCaching> values = listStep.getValues();
    if (values.size() <= index) {
      return null;
    }
    return values.get(index).getAction();
  }

  public void recreate() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    recreateMyPopup(step);
  }

  private void showIntentionHintImpl(final boolean delay, final Point position) {
    final int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();

    PriorityQuestionAction action = new PriorityQuestionAction() {
      @Override
      public boolean execute() {
        showPopup();
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
      final int borderHeight = NORMAL_BORDER_SIZE;

      int yShift = -(NORMAL_BORDER_SIZE + AllIcons.Actions.RealIntentionBulb.getIconHeight());
      if (canPlaceBulbOnTheSameLine(editor)) {
        yShift = -(borderHeight + ((AllIcons.Actions.RealIntentionBulb.getIconHeight() - editor.getLineHeight())/2) + 3);
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
    return point.x > (AllIcons.Actions.RealIntentionBulb.getIconWidth() + (editor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE) * 2);
  }

  private IntentionHintComponent(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull final Editor editor,
                                 @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myFile = file;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showRefactoringsBulb = ContainerUtil.exists(intentions.inspectionFixesToShow, new Condition<HighlightInfo.IntentionActionDescriptor>() {
      @Override
      public boolean value(HighlightInfo.IntentionActionDescriptor descriptor) {
        return descriptor.getAction() instanceof BaseRefactoringIntentionAction;
      }
    });
    boolean showFix = !showRefactoringsBulb && ContainerUtil.exists(intentions.errorFixesToShow, new Condition<HighlightInfo.IntentionActionDescriptor>() {
      @Override
      public boolean value(HighlightInfo.IntentionActionDescriptor descriptor) {
        return IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction());
      }
    });

    Icon smartTagIcon = showRefactoringsBulb ? AllIcons.Actions.RefactoringBulb : showFix ? AllIcons.Actions.QuickfixBulb : AllIcons.Actions.IntentionBulb;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(smartTagIcon, 0);
    myHighlightedIcon.setIcon(AllIcons.General.ArrowDown, 1);

    myInactiveIcon = new RowIcon(2);
    myInactiveIcon.setIcon(smartTagIcon, 0);
    myInactiveIcon.setIcon(ourInactiveArrowIcon, 1);

    myIconLabel = new JLabel(myInactiveIcon);
    myIconLabel.setOpaque(false);

    add(myIconLabel, BorderLayout.CENTER);

    setBorder(editor.isOneLineMode() ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER);

    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
          showPopup();
        }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        onMouseEnter(editor.isOneLineMode());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        onMouseExit(editor.isOneLineMode());
      }
    });

    myComponentHint = new MyComponentHint(this);
    IntentionListStep step = new IntentionListStep(this, intentions, myEditor, myFile, project);
    recreateMyPopup(step);
    // dispose myself when editor closed
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == myEditor) {
          hide();
        }
      }
    }, this);
  }

  @Override
  public void hide() {
    Disposer.dispose(this);
  }

  private void onMouseExit(final boolean small) {
    Window ancestor = SwingUtilities.getWindowAncestor(myPopup.getContent());
    if (ancestor == null) {
      myIconLabel.setIcon(myInactiveIcon);
      setBorder(small ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER);
    }
  }

  private void onMouseEnter(final boolean small) {
    myIconLabel.setIcon(myHighlightedIcon);
    setBorder(small ? ACTIVE_BORDER_SMALL : ACTIVE_BORDER);

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

  private void showPopup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myPopup == null || myPopup.isDisposed()) return;

    if (isShowing()) {
      final RelativePoint swCorner = RelativePoint.getSouthWestOf(this);
      final int yOffset = canPlaceBulbOnTheSameLine(myEditor) ? 0 : myEditor.getLineHeight() - (myEditor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE);
      myPopup.show(new RelativePoint(swCorner.getComponent(), new Point(swCorner.getPoint().x, swCorner.getPoint().y + yOffset)));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  private void recreateMyPopup(@NotNull IntentionListStep step) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myPopup != null) {
      Disposer.dispose(myPopup);
    }
    myPopup = JBPopupFactory.getInstance().createListPopup(step);
    myPopup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        myPopupShown = false;
      }
    });

    if (myEditor.isOneLineMode()) {
      // hide popup on combobox popup show
      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, myEditor.getContentComponent());
      if (ancestor != null) {
        final JComboBox comboBox = (JComboBox)ancestor;
        myOuterComboboxPopupListener = new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            hide();
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
          }
        };

        comboBox.addPopupMenuListener(myOuterComboboxPopupListener);
      }
    }

    Disposer.register(this, myPopup);
    Disposer.register(myPopup, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().assertIsDispatchThread();
      }
    });
  }

  void canceled(@NotNull IntentionListStep intentionListStep) {
    if (myPopup.getListStep() != intentionListStep || myDisposed) {
      return;
    }
    // Root canceled. Create new popup. This one cannot be reused.
    recreateMyPopup(intentionListStep);
  }

  private class MyComponentHint extends LightweightHint {
    private boolean myVisible = false;
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
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            showImpl(parentComponent, x, y, focusBackComponent);
          }
        }, DELAY);
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

    public void setShouldDelay(boolean shouldDelay) {
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
      return mySettings.isEnabled(myAction) ?
             CodeInsightBundle.message("disable.intention.action", myFamilyName) :
             CodeInsightBundle.message("enable.intention.action", myFamilyName);
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
      final IntentionSettingsConfigurable configurable = new IntentionSettingsConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
        @Override
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              configurable.selectIntention(myFamilyName);
            }
          });
        }
      });
    }
  }

  private static abstract class AbstractEditIntentionSettingsAction implements IntentionAction {
    protected final String myFamilyName;
    private final boolean myDisabled;

    public AbstractEditIntentionSettingsAction(IntentionAction action) {
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
