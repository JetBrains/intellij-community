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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.PriorityQuestionAction;
import com.intellij.codeInsight.hint.ScrollAwareHint;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

  static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/realIntentionBulb.png");
  static final Icon ourBulbIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");
  static final Icon ourIntentionOffIcon = IconLoader.getIcon("/actions/realIntentionOffBulb.png");
  static final Icon ourQuickFixOffIcon = IconLoader.getIcon("/actions/quickfixOffBulb.png");
  static final Icon ourArrowIcon = IconLoader.getIcon("/general/arrowDown.png");
  static final Icon ourInactiveArrowIcon = new EmptyIcon(ourArrowIcon.getIconWidth(), ourArrowIcon.getIconHeight());
  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(6, 6, 6, 6);
  private static final Border ACTIVE_BORDER = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(5, 5, 5, 5));

  private static final Border INACTIVE_BORDER_SMALL = BorderFactory.createEmptyBorder(4, 4, 4, 4);
  private static final Border ACTIVE_BORDER_SMALL = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(3, 3, 3, 3));

  private final Editor myEditor;

  private static final Alarm myAlarm = new Alarm();

  private final RowIcon myHighlightedIcon;
  private final JLabel myIconLabel;

  private final RowIcon myInactiveIcon;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private boolean myPopupShown = false;
  private boolean myDisposed = false;
  private ListPopup myPopup;
  private final PsiFile myFile;
  private static final int LIGHTBULB_OFFSET = 20;

  private PopupMenuListener myOuterComboboxPopupListener;

  public static IntentionHintComponent showIntentionHint(Project project, final PsiFile file, Editor editor, ShowIntentionsPass.IntentionsInfo intentions,
                                                         boolean showExpanded) {
    final Point position = getHintPosition(editor);
    return showIntentionHint(project, file, editor, intentions, showExpanded, position);
  }

  public static IntentionHintComponent showIntentionHint(Project project, final PsiFile file, Editor editor,
                                                         @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                         boolean showExpanded,
                                                         final Point position) {
    final IntentionHintComponent component = new IntentionHintComponent(project, file, editor, intentions);

    component.showIntentionHintImpl(!showExpanded, position);
    Disposer.register(project, component);
    if (showExpanded) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          component.showPopup();
        }
      });
    }

    return component;
  }

  @TestOnly
  public boolean isDisposed() {
    return myDisposed;
  }

  public void dispose() {
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

  public void editorScrolled() {
    closePopup();
  }

  //true if actions updated, there is nothing to do
  //false if has to recreate popup, no need to reshow
  //null if has to reshow
  public synchronized Boolean updateActions(ShowIntentionsPass.IntentionsInfo intentions) {
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

  public synchronized void recreate() {
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    recreateMyPopup(step);
  }

  private void showIntentionHintImpl(final boolean delay, final Point position) {
    final int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();

    PriorityQuestionAction action = new PriorityQuestionAction() {
      public boolean execute() {
        showPopup();
        return true;
      }

      public int getPriority() {
        return -10;
      }
    };
    if (hintManager.canShowQuestionAction(action)) {
      hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action);
    }
  }

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

      realPoint = new Point(- (ourIntentionIcon.getIconWidth() / 2) - 4, - (ourIntentionIcon.getIconHeight() / 2));
    } else {
      final int yShift = (ourIntentionIcon.getIconHeight() - editor.getLineHeight() - 1) / 2 - 1;
      final int xShift = ourIntentionIcon.getIconWidth();

      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      realPoint = new Point(Math.max(0,visibleArea.x - xShift), position.y + yShift- LIGHTBULB_OFFSET);
    }

    Point location = SwingUtilities.convertPoint(convertComponent, realPoint, editor.getComponent().getRootPane().getLayeredPane());
    return new Point(location.x, location.y);
  }

  private IntentionHintComponent(@NotNull Project project, @NotNull PsiFile file, @NotNull final Editor editor,
                                 @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myFile = file;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (final HighlightInfo.IntentionActionDescriptor pairs : intentions.errorFixesToShow) {
      IntentionAction fix = pairs.getAction();
      if (IntentionManagerSettings.getInstance().isShowLightBulb(fix)) {
        showFix = true;
        break;
      }
    }

    Icon smartTagIcon = showFix ? ourQuickFixIcon : ourBulbIcon;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(smartTagIcon, 0);
    myHighlightedIcon.setIcon(ourArrowIcon, 1);

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
  }

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
    if (acceleratorsText.length() > 0) {
      myIconLabel.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
    }
  }

  @TestOnly
  public LightweightHint getComponentHint() {
    return myComponentHint;
  }

  private void closePopup() {
    myPopup.cancel();
    myPopupShown = false;
  }

  private void showPopup() {
    if (myPopup == null || myPopup.isDisposed()) return;

    if (isShowing()) {
      final RelativePoint swCorner = RelativePoint.getSouthWestOf(this);
      myPopup.show(new RelativePoint(swCorner.getComponent(), new Point(swCorner.getPoint().x, swCorner.getPoint().y+LIGHTBULB_OFFSET)));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  private void recreateMyPopup(IntentionListStep step) {
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
      public void dispose() {
        ApplicationManager.getApplication().assertIsDispatchThread();
      }
    });
  }

  void canceled(IntentionListStep intentionListStep) {
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

    public void show(@NotNull final JComponent parentComponent,
                     final int x,
                     final int y,
                     final JComponent focusBackComponent,
                     @NotNull HintHint hintInfo) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
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

    public void hide() {
      super.hide();
      myVisible = false;
      myAlarm.cancelAllRequests();
    }

    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    public void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  public static class EnableDisableIntentionAction implements IntentionAction{
    private final String myActionFamilyName;
    private final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
    private final IntentionAction myAction;

    public EnableDisableIntentionAction(IntentionAction action) {
      myActionFamilyName = action.getFamilyName();
      myAction = action;
      // needed for checking errors in user written actions
      //noinspection ConstantConditions
      LOG.assertTrue(myActionFamilyName != null, "action "+action.getClass()+" family returned null");
    }

    @NotNull
    public String getText() {
      return mySettings.isEnabled(myAction) ?
             CodeInsightBundle.message("disable.intention.action", myActionFamilyName) :
             CodeInsightBundle.message("enable.intention.action", myActionFamilyName);
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myAction, !mySettings.isEnabled(myAction));
    }

    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public String toString() {
      return getText();
    }
  }
}
