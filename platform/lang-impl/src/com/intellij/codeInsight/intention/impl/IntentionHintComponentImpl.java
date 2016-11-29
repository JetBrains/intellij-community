/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.Alarm;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 */
public class IntentionHintComponentImpl extends IntentionHintComponent implements ScrollAwareHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");
  private static final Alarm myAlarm = new Alarm();

  private static final int NORMAL_BORDER_SIZE = 6;
  private static final int SMALL_BORDER_SIZE = 4;

  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE);
  private static final Border INACTIVE_BORDER_SMALL = BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE);

  private static Border createActiveBorder() {
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(getBorderColor(), 1), BorderFactory.createEmptyBorder(
      NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1));
  }

  private static  Border createActiveBorderSmall() {
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(getBorderColor(), 1), BorderFactory.createEmptyBorder(
      SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1));
  }

  private static Color getBorderColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
  }

  @Override
  public boolean isVisible() {
    return myPanel.isVisible();
  }

  protected volatile boolean myPopupShown;
  protected volatile ListPopup myPopup;

  private final JLabel myIconLabel;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private final JPanel myPanel = new JPanel();

  private PopupMenuListener myOuterComboboxPopupListener;

  @Override
  public boolean isShown() {
    return myPopupShown;
  }

  @TestOnly
  @Override
  public boolean isDisposed() {
    return super.isDisposed() || myPopup.isDisposed();
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myComponentHint.hide();
    myPanel.hide();

    if (myOuterComboboxPopupListener != null) {
      final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, myEditor.getContentComponent());
      if (ancestor != null) {
        ((JComboBox)ancestor).removePopupMenuListener(myOuterComboboxPopupListener);
      }

      myOuterComboboxPopupListener = null;
    }
    super.dispose();
  }

  @Override
  public void editorScrolled() {
    closePopup();
  }


  @Override
  public void recreate() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ListPopupStep step = myPopup.getListStep();
    recreateMyPopup(step);
  }

  void showIntentionHintImpl(final boolean delay, @NotNull Point position) {
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
  static Point getHintPosition(Editor editor) {
    if (!ApplicationManager.getApplication().hasUI()) return new Point();
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
        final int borderHeight = NORMAL_BORDER_SIZE;
        yShift = -(borderHeight + (AllIcons.Actions.RealIntentionBulb.getIconHeight() - editor.getLineHeight()) /2 + 3);
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

  IntentionHintComponentImpl(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull final Editor editor,
                             @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    super(project, file, editor, intentions);
    ApplicationManager.getApplication().assertIsDispatchThread();

    myPanel.setLayout(new BorderLayout());
    myPanel.setOpaque(false);


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
    recreateMyPopup(myIntentionListStep);
    EditorUtil.disposeWithEditor(myEditor, this);
  }

  @Override
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

  void showPopup(boolean mouseClick) {
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
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
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
          final IntentionAction action = ((IntentionActionWithTextCaching)selectedItem).getAction();
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
    Disposer.register(myPopup, () -> ApplicationManager.getApplication().assertIsDispatchThread());
  }

  @Override
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
      if (!parentComponent.isShowing() && !ApplicationManager.getApplication().isOnAir()) return;
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

}
