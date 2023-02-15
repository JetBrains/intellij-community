// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.*;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
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
import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.icons.RowIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.List;

/**
 * Shows a light bulb icon in the editor if some intention is available.
 * <ul>
 * <li>Hovering over the light bulb icon draws a border around the light bulb.
 * <li>Clicking the light bulb opens a popup menu that lists the available intentions.
 * </ul>
 *
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 * @author Konstantin Bulenkov
 */
public final class IntentionHintComponent implements Disposable, ScrollAwareHint {

  private static final Logger LOG = Logger.getInstance(IntentionHintComponent.class);

  private static final Icon ourInactiveArrowIcon = IconManager.getInstance().createEmptyIcon(AllIcons.General.ArrowDown);

  private static final Alarm ourAlarm = new Alarm();

  private final IntentionPopup myPopup;

  private final Editor myEditor;

  private final RowIcon myHighlightedIcon;
  private final JLabel myIconLabel;

  private final RowIcon myInactiveIcon;

  private final MyComponentHint myComponentHint;
  private boolean myDisposed; // accessed in EDT only
  private final JPanel myPanel = new JPanel() {
    @Override
    public synchronized void addMouseListener(MouseListener l) {
      // avoid this (transparent) panel consuming mouse click events
    }
  };

  private IntentionHintComponent(@NotNull Project project,
                                 @NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull Icon smartTagIcon,
                                 @NotNull IntentionPopup popup) {
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

    myPanel.setBorder(LightBulb.getInactiveBorder(editor.isOneLineMode()));

    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
          AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS);
          DataContext projectContext = SimpleDataContext.getProjectContext(project);
          AnActionEvent event = AnActionEvent.createFromInputEvent(e, ActionPlaces.MOUSE_SHORTCUT, null, projectContext);
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

  public static @NotNull IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                                  @NotNull PsiFile file,
                                                                  @NotNull Editor editor,
                                                                  boolean showExpanded,
                                                                  @NotNull CachedIntentions cachedIntentions) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionPopup intentionPopup = new IntentionPopup(project, editor, file, cachedIntentions);
    IntentionHintComponent component = new IntentionHintComponent(project, file, editor, LightBulb.getIcon(cachedIntentions), intentionPopup);

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

  public boolean isVisible() {
    return myPanel.isVisible();
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

  public void hide() {
    myDisposed = true;
    Disposer.dispose(this);
  }

  /**
   * Hides this component if it is visible and bound to the supplied editor
   *
   * @param editor editor to check against
   * @return true if hidden successfully; false if it's not displayed already, or bound to another editor
   */
  public boolean hideIfDisplayedForEditor(@NotNull Editor editor) {
    if (isDisposed() || !isVisible() || editor != myEditor) return false;
    hide();
    return true;
  }

  public boolean hasVisibleLightBulbOrPopup() {
    return !isDisposed()
           && isVisible()
           && (myComponentHint.isVisible() || myPopup.isVisible() || ApplicationManager.getApplication().isUnitTestMode());
  }

  @TestOnly
  public CachedIntentions getCachedIntentions() {
    return myPopup.myCachedIntentions;
  }

  private void showIntentionHintImpl(boolean delay) {
    int offset = myEditor.getCaretModel().getOffset();

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
      Point position = LightBulb.getPosition(myEditor);
      if (position != null) {
        hintManager.showQuestionHint(myEditor, position, offset, offset, myComponentHint, action, HintManager.ABOVE);
      }
    }
  }

  private void onMouseExit(boolean small) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myPopup.isVisible()) {
      myIconLabel.setIcon(myInactiveIcon);
      myPanel.setBorder(LightBulb.getInactiveBorder(small));
    }
  }

  private void onMouseEnter(boolean small) {
    myIconLabel.setIcon(myHighlightedIcon);
    myPanel.setBorder(LightBulb.getActiveBorder(small));

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
      RelativePoint swCorner = RelativePoint.getSouthWestOf(myPanel);
      int yOffset = LightBulb.canPlaceBulbOnTheSameLine(myEditor) ? 0 :
                    myEditor.getLineHeight() - LightBulb.getBorderSize(myEditor.isOneLineMode());
      positionHint = new RelativePoint(swCorner.getComponent(), new Point(swCorner.getPoint().x, swCorner.getPoint().y + yOffset));
    }
    myPopup.show(this, positionHint);
  }

  private static void recreateMyPopup(@NotNull IntentionPopup that, @NotNull ListPopupStep<IntentionActionWithTextCaching> step) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (that.myListPopup != null) {
      Disposer.dispose(that.myListPopup);
    }
    if (that.myDisposed || that.myEditor.isDisposed()) {
      that.myListPopup = null;
      return;
    }
    that.myListPopup = JBPopupFactory.getInstance().createListPopup(step);
    if (that.myListPopup instanceof WizardPopup) {
      Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcuts();
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
          if (keyboardShortcut.getSecondKeyStroke() == null) {
            ((WizardPopup)that.myListPopup).registerAction(
              "activateSelectedElement", keyboardShortcut.getFirstKeyStroke(), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  that.myListPopup.handleSelect(true);
                }
              }
            );
          }
        }
      }

      registerIntentionShortcuts(that);
      registerShowPreviewAction(that);
    }

    boolean committed = PsiDocumentManager.getInstance(that.myFile.getProject()).isCommitted(that.myEditor.getDocument());
    PsiFile injectedFile = committed
                           ? InjectedLanguageUtil.findInjectedPsiNoCommit(that.myFile, that.myEditor.getCaretModel().getOffset())
                           : null;
    Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(that.myEditor, injectedFile);

    ScopeHighlighter highlighter = new ScopeHighlighter(that.myEditor);
    ScopeHighlighter injectionHighlighter = new ScopeHighlighter(injectedEditor);

    ListPopupImpl list = ObjectUtils.tryCast(that.myListPopup, ListPopupImpl.class);

    var selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Object source = e.getSource();
        highlighter.dropHighlight();
        injectionHighlighter.dropHighlight();

        if (source instanceof DataProvider) {
          Object selectedItem = PlatformCoreDataKeys.SELECTED_ITEM.getData((DataProvider)source);
          if (selectedItem instanceof IntentionActionWithTextCaching) {
            IntentionAction action = IntentionActionDelegate.unwrap(((IntentionActionWithTextCaching)selectedItem).getAction());
            if (list != null) {
              updatePreviewPopup(that, action, list.getOriginalSelectedIndex());
            }
            highlightOnHover(selectedItem);
          }
        }
      }

      private void highlightOnHover(Object selectedItem) {
        if (!(selectedItem instanceof IntentionActionWithTextCaching)) return;

        IntentionAction action = IntentionActionDelegate.unwrap(((IntentionActionWithTextCaching)selectedItem).getAction());
        if (action instanceof SuppressIntentionActionFromFix) {
          if (injectedFile != null && ((SuppressIntentionActionFromFix)action).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
            PsiElement at = injectedFile.findElementAt(injectedEditor.getCaretModel().getOffset());
            PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
            if (container != null) {
              injectionHighlighter.highlight(container, Collections.singletonList(container));
            }
          }
          else {
            PsiElement at = that.myFile.findElementAt(that.myEditor.getCaretModel().getOffset());
            PsiElement container = ((SuppressIntentionActionFromFix)action).getContainer(at);
            if (container != null) {
              highlighter.highlight(container, Collections.singletonList(container));
            }
          }
        }
        else if (action instanceof CustomizableIntentionAction) {
          var ranges = ((CustomizableIntentionAction)action).getRangesToHighlight(that.myEditor, that.myFile);
          for (var range : ranges) {
            TextRange rangeInFile = range.getRangeInFile();
            PsiFile file = range.getContainingFile();
            if (injectedFile != null && file.getViewProvider() == injectedFile.getViewProvider()) {
              injectionHighlighter.addHighlights(List.of(rangeInFile), range.getHighlightKey());
            }
            else if (!InjectedLanguageManager.getInstance(that.myProject).isInjectedFragment(file)) {
              highlighter.addHighlights(List.of(rangeInFile), range.getHighlightKey());
            }
          }
        }
      }
    };
    that.myListPopup.addListSelectionListener(selectionListener);

    that.myListPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        if (list != null) {
          selectionListener.highlightOnHover(list.getList().getSelectedValue());
        }
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        highlighter.dropHighlight();
        injectionHighlighter.dropHighlight();
        that.myPreviewPopupUpdateProcessor.hide();
        that.myPopupShown = false;
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

    Disposer.register(that, that.myListPopup);
    Disposer.register(that.myListPopup, ApplicationManager.getApplication()::assertIsDispatchThread);
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
    that.myPreviewPopupUpdateProcessor.setup(that.myListPopup, index);
    that.myPreviewPopupUpdateProcessor.updatePopup(action);
  }

  /** Add all intention shortcuts to also be available as actions in the popover */
  private static void registerIntentionShortcuts(@NotNull IntentionPopup that) {
    for (Object object : that.myListPopup.getListStep().getValues()) {
      if (object instanceof IntentionActionDelegate) {
        registerIntentionShortcut(that, ((IntentionActionDelegate)object).getDelegate());
      }
    }
  }

  private static void registerIntentionShortcut(@NotNull IntentionPopup that, @NotNull IntentionAction intention) {
    var shortcuts = IntentionShortcutManager.getInstance().getShortcutSet(intention);
    if (shortcuts == null) return;

    for (var shortcut : shortcuts.getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
        ((WizardPopup)that.myListPopup).registerAction(
          IntentionShortcutUtils.getWrappedActionId(intention), keyboardShortcut.getFirstKeyStroke(), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              that.close();
              IntentionShortcutUtils.invokeAsAction(intention, that.myEditor, that.myFile);
            }
          }
        );
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
          processor.activate();
          showPreview(that);
        }
        else {
          processor.hide();
        }
      }
    };
    KeyStroke keyStroke = KeymapUtil.getKeyStroke(IntentionPreviewPopupUpdateProcessor.Companion.getShortcutSet());
    ((WizardPopup)that.myListPopup).registerAction("showIntentionPreview", keyStroke, action);
    advertisePopup(that);
  }

  private static void advertisePopup(@NotNull IntentionPopup that) {
    ListPopup popup = that.myListPopup;
    if (!popup.isDisposed()) {
      popup.setAdText(CodeInsightBundle.message(
        "intention.preview.adv.toggle.text",
        IntentionPreviewPopupUpdateProcessor.Companion.getShortcutText()), SwingConstants.LEFT);
    }
  }

  private static void showPreview(@NotNull IntentionHintComponent.IntentionPopup that) {
    that.myPreviewPopupUpdateProcessor.show();
    if (that.myListPopup instanceof ListPopupImpl) {
      JList<?> list = ((ListPopupImpl)that.myListPopup).getList();
      int selectedIndex = list.getSelectedIndex();
      Object selectedValue = list.getSelectedValue();
      if (selectedValue instanceof IntentionActionWithTextCaching) {
        updatePreviewPopup(that, ((IntentionActionWithTextCaching)selectedValue).getAction(), selectedIndex);
      }
    }
  }

  private static final class MyComponentHint extends LightweightHint {
    private boolean myVisible;
    private boolean myShouldDelay;

    private MyComponentHint(JComponent component) {
      super(component);
    }

    @Override
    public void show(@NotNull JComponent parentComponent,
                     int x,
                     int y,
                     JComponent focusBackComponent,
                     @NotNull HintHint hintHint) {
      myVisible = true;
      if (myShouldDelay) {
        ourAlarm.cancelAllRequests();
        ourAlarm.addRequest(() -> showImpl(parentComponent, x, y, focusBackComponent), 500);
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
      ourAlarm.cancelAllRequests();
    }

    @Override
    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    private void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  /** The light bulb icon, optionally surrounded by a border. */
  private static class LightBulb {

    private static final int NORMAL_BORDER_SIZE = 6;
    private static final int SMALL_BORDER_SIZE = 4;

    private static final Border INACTIVE_BORDER =
      BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE, NORMAL_BORDER_SIZE);
    private static final Border INACTIVE_BORDER_SMALL =
      BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE, SMALL_BORDER_SIZE);

    static @NotNull Icon getIcon(CachedIntentions cachedIntentions) {
      boolean showRefactoring = !ExperimentalUI.isNewUI() && ContainerUtil.exists(
        cachedIntentions.getInspectionFixes(),
        descriptor -> IntentionActionDelegate.unwrap(descriptor.getAction()) instanceof BaseRefactoringIntentionAction
      );
      if (showRefactoring) return AllIcons.Actions.RefactoringBulb;

      boolean showQuickFix = ContainerUtil.exists(
        cachedIntentions.getErrorFixes(),
        descriptor -> IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction())
      );
      if (showQuickFix) return AllIcons.Actions.QuickfixBulb;

      return AllIcons.Actions.IntentionBulb;
    }

    static Border getInactiveBorder(boolean small) {
      return small ? INACTIVE_BORDER_SMALL : INACTIVE_BORDER;
    }

    private static Border createActiveBorder() {
      return BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(getBorderColor(), 1),
        BorderFactory.createEmptyBorder(NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1, NORMAL_BORDER_SIZE - 1)
      );
    }

    static Border getActiveBorder(boolean small) {
      return small ? createActiveBorderSmall() : createActiveBorder();
    }

    private static Border createActiveBorderSmall() {
      return BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(getBorderColor(), 1),
        BorderFactory.createEmptyBorder(SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1, SMALL_BORDER_SIZE - 1)
      );
    }

    static int getBorderSize(boolean small) {
      return small ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE;
    }

    private static Color getBorderColor() {
      return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
    }

    /** Returns the position of the light bulb, relative to {@link Editor#getComponent()}. */
    static @Nullable Point getPosition(Editor editor) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return new Point();

      LOG.assertTrue(editor.getComponent().isDisplayable());

      return editor.isOneLineMode()
             ? getPositionOneLine(editor)
             : getPositionMultiLine(editor);
    }

    private static @NotNull Point getPositionOneLine(Editor editor) {
      JComponent convertComponent = editor.getContentComponent();

      // place the light bulb at the corner of the surrounding component
      JComboBox<?> ancestorCombo = findAncestorCombo(editor);
      if (ancestorCombo != null) {
        convertComponent = ancestorCombo;
      }
      else {
        JTextField ancestorTextField = (JTextField)SwingUtilities.getAncestorOfClass(JTextField.class, editor.getContentComponent());
        if (ancestorTextField != null) {
          convertComponent = ancestorTextField;
        }
      }

      Point realPoint = new Point(-(EmptyIcon.ICON_16.getIconWidth() / 2) - 4, -(EmptyIcon.ICON_16.getIconHeight() / 2));
      Point p = SwingUtilities.convertPoint(convertComponent, realPoint, editor.getComponent().getRootPane().getLayeredPane());
      return new Point(p.x, p.y);
    }

    private static @Nullable Point getPositionMultiLine(Editor editor) {
      Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      VisualPosition visualPosition = editor.offsetToVisualPosition(editor.getCaretModel().getOffset());
      Point lineStart = editor.visualPositionToXY(new VisualPosition(visualPosition.line, 0));
      if (lineStart.y < visibleArea.y || lineStart.y >= visibleArea.y + visibleArea.height) return null;

      // try to place bulb on the same line
      int yShift = -(NORMAL_BORDER_SIZE + EmptyIcon.ICON_16.getIconHeight());
      if (canPlaceBulbOnTheSameLine(editor)) {
        yShift = -(NORMAL_BORDER_SIZE + (EmptyIcon.ICON_16.getIconHeight() - editor.getLineHeight()) / 2 + 3);
      }
      else if (lineStart.y < visibleArea.y + editor.getLineHeight()) {
        yShift = editor.getLineHeight() - NORMAL_BORDER_SIZE;
      }

      int xShift = EmptyIcon.ICON_16.getIconWidth();

      Point realPoint = new Point(Math.max(0, visibleArea.x - xShift), lineStart.y + yShift);
      Point p = SwingUtilities.convertPoint(editor.getContentComponent(), realPoint, editor.getComponent().getRootPane().getLayeredPane());
      return new Point(p.x, p.y);
    }

    private static boolean canPlaceBulbOnTheSameLine(Editor editor) {
      if (ApplicationManager.getApplication().isUnitTestMode() || editor.isOneLineMode()) return false;
      if (Registry.is("always.show.intention.above.current.line", false)) return false;

      int visualCaretLine = editor.offsetToVisualPosition(editor.getCaretModel().getOffset()).line;
      int textColumn = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, visualCaretLine);
      if (textColumn == -1) return false;

      int textX = editor.visualPositionToXY(new VisualPosition(visualCaretLine, textColumn)).x;
      int borderWidth = editor.isOneLineMode() ? SMALL_BORDER_SIZE : NORMAL_BORDER_SIZE;
      return textX > borderWidth + EmptyIcon.ICON_16.getIconWidth() + borderWidth;
    }
  }

  static class IntentionPopup implements Disposable.Parent {
    private final @NotNull CachedIntentions myCachedIntentions;
    private final @NotNull Editor myEditor;
    private final PsiFile myFile;
    private final Project myProject;
    private final IntentionPreviewPopupUpdateProcessor myPreviewPopupUpdateProcessor;
    private PopupMenuListener myOuterComboboxPopupListener;
    private IntentionHintComponent myHint;
    private ListPopup myListPopup;
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

    private boolean isVisible() {
      return myListPopup != null && SwingUtilities.getWindowAncestor(myListPopup.getContent()) != null;
    }

    private void show(@NotNull IntentionHintComponent component, @Nullable RelativePoint positionHint) {
      if (myDisposed || myEditor.isDisposed() || (myListPopup != null && myListPopup.isDisposed()) || myPopupShown) return;

      if (myListPopup == null) {
        assert myHint == null;
        myHint = component;
        recreateMyPopup(this, new IntentionListStep(this, myEditor, myFile, myProject, myCachedIntentions));
      }
      else {
        assert myHint == component;
      }

      if (positionHint != null) {
        myListPopup.show(positionHint);
      }
      else {
        myListPopup.showInBestPositionFor(myEditor);
      }

      if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
        ApplicationManager.getApplication().invokeLater(() -> showPreview(this));
      }

      IntentionsCollector.reportShownIntentions(myFile.getProject(), myListPopup, myFile.getLanguage());
      myPopupShown = true;
    }

    private void close() {
      myListPopup.cancel();
      myPopupShown = false;
    }

    void cancelled(@NotNull IntentionListStep step) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (myListPopup.getListStep() == step && !myDisposed) {
        Disposer.dispose(myHint);
      }
    }

    /**
     * Hide preview temporarily when submenu is shown
     */
    void hidePreview() {
      myPreviewPopupUpdateProcessor.hideTemporarily();
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
}
