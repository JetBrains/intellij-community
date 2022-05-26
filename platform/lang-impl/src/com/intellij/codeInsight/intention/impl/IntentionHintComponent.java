// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.DynamicPlugins;
import com.intellij.internal.statistic.IntentionsCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.HintHint;
import com.intellij.ui.IconManager;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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

import static com.intellij.codeInsight.intention.impl.IntentionShortcutUtils.getWrappedActionId;
import static com.intellij.codeInsight.intention.impl.IntentionShortcutUtils.invokeAsAction;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 */
public final class IntentionHintComponent implements Disposable, ScrollAwareHint {
  public interface Popup extends Disposable {
    boolean isVisible();

    void show(@NotNull IntentionHintComponent bulb, @Nullable RelativePoint positionHint);

    void close();
  }

  static class IntentionPopup implements Popup, Disposable.Parent {
    private final CachedIntentions myCachedIntentions;
    private final Editor myEditor;
    private final PsiFile myFile;
    private final Project myProject;
    private final IntentionPreviewPopupUpdateProcessor myPreviewPopupUpdateProcessor;
    private PopupMenuListener myOuterComboboxPopupListener;
    private IntentionHintComponent myHint;
    private ListPopup myPopup;
    private boolean myDisposed;
    private boolean myPopupShown;

    private IntentionPopup(@NotNull Project project,
                           @NotNull Editor editor,
                           @NotNull PsiFile file,
                           @NotNull CachedIntentions cachedIntentions) {
      myProject = project;
      myEditor = editor;
      myFile = file;
      myCachedIntentions = cachedIntentions;
      myPreviewPopupUpdateProcessor = new IntentionPreviewPopupUpdateProcessor(project, myFile, myEditor);
    }

    @Override
    public boolean isVisible() {
      return myPopup != null && SwingUtilities.getWindowAncestor(myPopup.getContent()) != null;
    }

    @Override
    public void show(@NotNull IntentionHintComponent component, @Nullable RelativePoint positionHint) {
      if (myDisposed || myEditor.isDisposed() || (myPopup != null && myPopup.isDisposed()) || myPopupShown) return;

      if (myPopup == null) {
        assert myHint == null;
        myHint = component;
        recreateMyPopup(this, new IntentionListStep(this, myEditor, myFile, myProject, myCachedIntentions));
      }
      else {
        assert myHint == component;
      }

      if (positionHint != null) {
        myPopup.show(positionHint);
      }
      else {
        myPopup.showInBestPositionFor(myEditor);
      }

      if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
        ApplicationManager.getApplication().invokeLater(() -> showPreview(this));
      }

      IntentionsCollector.reportShownIntentions(myFile.getProject(), myPopup, myFile.getLanguage());
      myPopupShown = true;
    }

    @Override
    public void close() {
      myPopup.cancel();
      myPopupShown = false;
    }

    public void cancelled(IntentionListStep step) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (myPopup.getListStep() == step && !myDisposed) {
        // Root canceled. Create new popup. This one cannot be reused.
        recreateMyPopup(this, step);
      }
    }


    @Override
    public void beforeTreeDispose() {
      // The flag has to be set early. Child's dispose() can call `cancelled` and it must be a no-op at this point.
      myDisposed = true;
    }

    @Override
    public void dispose() {
      if (myOuterComboboxPopupListener != null) {
        JComboBox<?> ancestor = findAncestorCombo(myEditor);
        if (ancestor != null) {
          ancestor.removePopupMenuListener(myOuterComboboxPopupListener);
        }

        myOuterComboboxPopupListener = null;
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(IntentionHintComponent.class);

  private static final Icon ourInactiveArrowIcon = IconManager.getInstance().createEmptyIcon(AllIcons.General.ArrowDown);

  private static final int NORMAL_BORDER_SIZE = 6;
  private static final int SMALL_BORDER_SIZE = 4;

  private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE);
  private static final Border INACTIVE_BORDER_SMALL = BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE);

  @TestOnly
  public CachedIntentions getCachedIntentions() {
    return ((IntentionPopup)myPopup).myCachedIntentions;
  }

  private final Popup myPopup;

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
  private boolean myDisposed; // accessed in EDT only
  private final JPanel myPanel = new JPanel() {
    @Override
    public synchronized void addMouseListener(MouseListener l) {
      // avoid this (transparent) panel consuming mouse click events
    }
  };

  @NotNull
  private static Icon getIcon(CachedIntentions cachedIntentions) {
    boolean showRefactoringsBulb = ContainerUtil.exists(cachedIntentions.getInspectionFixes(),
                                                        descriptor -> IntentionActionDelegate
                                                          .unwrap(descriptor.getAction()) instanceof BaseRefactoringIntentionAction);
    boolean showFix = !showRefactoringsBulb && ContainerUtil.exists(cachedIntentions.getErrorFixes(),
                                                                    descriptor -> IntentionManagerSettings.getInstance()
                                                                      .isShowLightBulb(descriptor.getAction()));

    return showRefactoringsBulb
           ? AllIcons.Actions.RefactoringBulb
           : showFix ? AllIcons.Actions.QuickfixBulb : AllIcons.Actions.IntentionBulb;
  }

  @NotNull
  public static IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                         @NotNull PsiFile file,
                                                         @NotNull Editor editor,
                                                         boolean showExpanded,
                                                         @NotNull Icon icon,
                                                         @NotNull IntentionHintComponent.Popup popup) {

    ApplicationManager.getApplication().assertIsDispatchThread();
    final IntentionHintComponent component = new IntentionHintComponent(project, file, editor, icon, popup);

    if (editor.getSettings().isShowIntentionBulb()) {
      component.showIntentionHintImpl(!showExpanded);
    }
    if (showExpanded) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!editor.isDisposed() && UIUtil.isShowing(editor.getContentComponent())) {
          component.showPopup(false);
        }
      }, project.getDisposed());
    }

    return component;
  }

  @NotNull
  public static IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                         @NotNull PsiFile file,
                                                         @NotNull Editor editor,
                                                         boolean showExpanded,
                                                         @NotNull CachedIntentions cachedIntentions) {
    return showIntentionHint(project, file, editor, showExpanded, getIcon(cachedIntentions),
                             new IntentionPopup(project, editor, file, cachedIntentions));
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposed = true;
    myComponentHint.hide();
    myPanel.hide();
  }

  @Override
  public void editorScrolled() {
    closePopup();
  }

  @Nullable
  @TestOnly
  public IntentionAction getAction(int index) {
    IntentionPopup that = (IntentionPopup)myPopup;
    if (that.myPopup == null || that.myPopup.isDisposed()) {
      return null;
    }
    List<IntentionActionWithTextCaching> values = that.myCachedIntentions.getAllActions();
    if (values.size() <= index) {
      return null;
    }
    return values.get(index).getAction();
  }


  private void showIntentionHintImpl(final boolean delay) {
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
      Point position = getHintPosition();
      if (position != null) {
        hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action, HintManager.ABOVE);
      }
    }
  }

  @Nullable
  private Point getHintPosition() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return new Point();
    Editor editor = myEditor;
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
      Container ancestor = findAncestorCombo(myEditor);
      if (ancestor != null) {
        convertComponent = (JComponent) ancestor;
      }
      else {
        ancestor = SwingUtilities.getAncestorOfClass(JTextField.class, editor.getContentComponent());
        if (ancestor != null) {
          convertComponent = (JComponent) ancestor;
        }
      }

      realPoint = new Point(- (AllIcons.Actions.RealIntentionBulb.getIconWidth() / 2) - 4, - (AllIcons.Actions.RealIntentionBulb
                                                                                                .getIconHeight() / 2));
    }
    else {
      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      if (position.y < visibleArea.y || position.y >= visibleArea.y + visibleArea.height) return null;

      // try to place bulb on the same line
      int yShift = -(NORMAL_BORDER_SIZE + AllIcons.Actions.RealIntentionBulb.getIconHeight());
      if (canPlaceBulbOnTheSameLine(editor)) {
        yShift = -(NORMAL_BORDER_SIZE + (AllIcons.Actions.RealIntentionBulb.getIconHeight() - editor.getLineHeight()) / 2 + 3);
      }
      else if (position.y < visibleArea.y + editor.getLineHeight()) {
        yShift = editor.getLineHeight() - NORMAL_BORDER_SIZE;
      }

      final int xShift = AllIcons.Actions.RealIntentionBulb.getIconWidth();

      realPoint = new Point(Math.max(0,visibleArea.x - xShift), position.y + yShift);
    }

    Point location = SwingUtilities.convertPoint(convertComponent, realPoint, editor.getComponent().getRootPane().getLayeredPane());
    return new Point(location.x, location.y);
  }

  private static boolean canPlaceBulbOnTheSameLine(Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode() || editor.isOneLineMode()) return false;
    if (Registry.is("always.show.intention.above.current.line", false)) return false;
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
                                 @NotNull Icon smartTagIcon,
                                 @NotNull IntentionHintComponent.Popup popup) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myEditor = editor;
    myPopup = popup;
    Disposer.register(this, popup);

    myPanel.setLayout(new BorderLayout());
    myPanel.setOpaque(false);

    IconManager iconManager = IconManager.getInstance();
    myHighlightedIcon = iconManager.createRowIcon(smartTagIcon, AllIcons.General.ArrowDown);
    myInactiveIcon = iconManager.createRowIcon(smartTagIcon, ourInactiveArrowIcon);

    myIconLabel = new JLabel(myInactiveIcon);
    myIconLabel.setOpaque(false);

    myPanel.add(myIconLabel, BorderLayout.CENTER);

    myPanel.setBorder(editor.isOneLineMode() ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER);

    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
          AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
          AnActionEvent event = AnActionEvent.createFromInputEvent(e, ActionPlaces.MOUSE_SHORTCUT, null, SimpleDataContext.getProjectContext(project));
          ActionsCollector.getInstance().record(project, action, event, file.getLanguage());

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
    EditorUtil.disposeWithEditor(myEditor, this);
    DynamicPlugins.INSTANCE.onPluginUnload(this, () -> Disposer.dispose(this));
  }

  public void hide() {
    myDisposed = true;
    Disposer.dispose(this);
  }

  private void onMouseExit(final boolean small) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myPopup.isVisible()) {
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
    myPopup.close();
  }

  private void showPopup(boolean mouseClick) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RelativePoint positionHint = null;
    if (mouseClick && myPanel.isShowing()) {
      final RelativePoint swCorner = RelativePoint.getSouthWestOf(myPanel);
      final int yOffset = canPlaceBulbOnTheSameLine(myEditor) ? 0 : myEditor.getLineHeight() - (myEditor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE);
      positionHint = new RelativePoint(swCorner.getComponent(), new Point(swCorner.getPoint().x, swCorner.getPoint().y + yOffset));
    }
    myPopup.show(this, positionHint);
  }

  private static void recreateMyPopup(@NotNull IntentionPopup that, @NotNull ListPopupStep<IntentionActionWithTextCaching> step) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (that.myPopup != null) {
      Disposer.dispose(that.myPopup);
    }
    if (that.myDisposed || that.myEditor.isDisposed()) {
      that.myPopup = null;
      return;
    }
    that.myPopup = JBPopupFactory.getInstance().createListPopup(step);
    if (that.myPopup instanceof WizardPopup) {
      Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcuts();
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          if (keyboardShortcut.getSecondKeyStroke() == null) {
            ((WizardPopup)that.myPopup).registerAction("activateSelectedElement", keyboardShortcut.getFirstKeyStroke(), new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                that.myPopup.handleSelect(true);
              }
            });
          }
        }
      }

      registerIntentionShortcuts(that);
      registerShowPreviewAction(that);
    }

    boolean committed = PsiDocumentManager.getInstance(that.myFile.getProject()).isCommitted(that.myEditor.getDocument());
    final PsiFile injectedFile = committed ? InjectedLanguageUtil.findInjectedPsiNoCommit(that.myFile, that.myEditor.getCaretModel().getOffset()) : null;
    final Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(that.myEditor, injectedFile);

    final ScopeHighlighter highlighter = new ScopeHighlighter(that.myEditor);
    final ScopeHighlighter injectionHighlighter = new ScopeHighlighter(injectedEditor);

    that.myPopup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        highlighter.dropHighlight();
        injectionHighlighter.dropHighlight();
        that.myPreviewPopupUpdateProcessor.hide();
        that.myPopupShown = false;
      }
    });
    that.myPopup.addListSelectionListener(e -> {
      final Object source = e.getSource();
      highlighter.dropHighlight();
      injectionHighlighter.dropHighlight();

      if (source instanceof DataProvider) {
        final Object selectedItem = PlatformCoreDataKeys.SELECTED_ITEM.getData((DataProvider)source);
        if (selectedItem instanceof IntentionActionWithTextCaching) {
          IntentionAction action = IntentionActionDelegate.unwrap(((IntentionActionWithTextCaching)selectedItem).getAction());
          if (that.myPopup instanceof ListPopupImpl) {
            updatePreviewPopup(that, action, ((ListPopupImpl)that.myPopup).getList().getSelectedIndex());
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
              final PsiElement at = that.myFile.findElementAt(that.myEditor.getCaretModel().getOffset());
              final PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
              if (container != null) {
                highlighter.highlight(container, Collections.singletonList(container));
              }
            }
          }
        }
      }
    });

    if (that.myEditor.isOneLineMode()) {
      // hide popup on combobox popup show
      JComboBox<?> comboBox = findAncestorCombo(that.myEditor);
      if (comboBox != null) {
        that.myOuterComboboxPopupListener = new PopupMenuListenerAdapter() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            that.myHint.hide();
          }
        };

        comboBox.addPopupMenuListener(that.myOuterComboboxPopupListener);
      }
    }

    Disposer.register(that, that.myPopup);
    Disposer.register(that.myPopup, ApplicationManager.getApplication()::assertIsDispatchThread);
  }

  private static JComboBox<?> findAncestorCombo(Editor editor) {
    Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, editor.getContentComponent());
    if (ancestor != null) {
      return (JComboBox<?>)ancestor;
    }
    return null;
  }

  private static void updatePreviewPopup(@NotNull IntentionHintComponent.IntentionPopup that, @NotNull IntentionAction action, int index) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    that.myPreviewPopupUpdateProcessor.setup(that.myPopup, index);
    that.myPreviewPopupUpdateProcessor.updatePopup(action);
  }

  /** Add all intention shortcuts to also be available as actions in the popover */
  private static void registerIntentionShortcuts(@NotNull IntentionPopup that) {
    for (Object object : that.myPopup.getListStep().getValues()) {
      if (object instanceof IntentionActionDelegate) {
        registerIntentionShortcut(that, ((IntentionActionDelegate)object).getDelegate());
      }
    }
  }

  private static void registerIntentionShortcut(@NotNull IntentionPopup that, @NotNull IntentionAction intention) {
    var shortcuts = IntentionShortcutManager.getInstance().getShortcutSet(intention);
    if (shortcuts == null) return;

    for (var shortcut : shortcuts.getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
        ((WizardPopup)that.myPopup).registerAction(getWrappedActionId(intention),
                                                   keyboardShortcut.getFirstKeyStroke(),
                                                   new AbstractAction() {
                                                     @Override
                                                     public void actionPerformed(ActionEvent e) {
                                                       that.close();
                                                       invokeAsAction(intention, that.myEditor, that.myFile);
                                                     }
                                                   });
      }
    }
  }

  private static void registerShowPreviewAction(@NotNull IntentionHintComponent.IntentionPopup that) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AbstractAction action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IntentionPreviewPopupUpdateProcessor processor = that.myPreviewPopupUpdateProcessor;
        boolean shouldShow = !processor.isShown();
        EditorSettingsExternalizable.getInstance().setShowIntentionPreview(shouldShow);
        if (shouldShow) {
          showPreview(that);
        }
        else {
          processor.hide();
          advertisePopup(that, true);
        }
      }
    };
    ((WizardPopup)that.myPopup).registerAction("showIntentionPreview",
            KeymapUtil.getKeyStroke(IntentionPreviewPopupUpdateProcessor.Companion.getShortcutSet()), action);
    advertisePopup(that, true);
  }

  private static void advertisePopup(@NotNull IntentionPopup that, boolean show) {
    ListPopup popup = that.myPopup;
    if (!popup.isDisposed()) {
      popup.setAdText(CodeInsightBundle.message(
        show ? "intention.preview.adv.show.text" : "intention.preview.adv.hide.text",
        IntentionPreviewPopupUpdateProcessor.Companion.getShortcutText()), SwingConstants.LEFT);
    }
  }

  private static void showPreview(@NotNull IntentionHintComponent.IntentionPopup that) {
    that.myPreviewPopupUpdateProcessor.show();
    if (that.myPopup instanceof ListPopupImpl) {
      JList<?> list = ((ListPopupImpl)that.myPopup).getList();
      int selectedIndex = list.getSelectedIndex();
      Object selectedValue = list.getSelectedValue();
      if (selectedValue instanceof IntentionActionWithTextCaching) {
        updatePreviewPopup(that, ((IntentionActionWithTextCaching)selectedValue).getAction(), selectedIndex);
      }
    }
    advertisePopup(that, false);
  }

  private static final class MyComponentHint extends LightweightHint {
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
}
