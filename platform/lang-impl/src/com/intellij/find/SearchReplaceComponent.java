// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.find.editorHeaderActions.*;
import com.intellij.icons.AllIcons;
import com.intellij.icons.ExpUiIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.SwingUndoUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.function.Predicate;

import static java.awt.FlowLayout.CENTER;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

public final class SearchReplaceComponent extends EditorHeaderComponent implements DataProvider {
  public static final int RIGHT_PANEL_WEST_OFFSET = 13;
  private static final float MAX_LEFT_PANEL_PROP = 0.9F;
  private static final float DEFAULT_PROP = 0.33F;
  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  private final MyTextComponentWrapper mySearchFieldWrapper;
  private JTextComponent mySearchTextComponent;

  private final MyTextComponentWrapper myReplaceFieldWrapper;
  private JTextComponent myReplaceTextComponent;

  private final DefaultActionGroup mySearchFieldActions;
  private final ActionToolbarImpl mySearchActionsToolbar;
  private final List<AnAction> myEmbeddedSearchActions = new ArrayList<>();
  private final List<Component> myExtraSearchButtons = new ArrayList<>();

  private final DefaultActionGroup myReplaceFieldActions;
  private final ActionToolbarImpl myReplaceActionsToolbar;
  private final List<AnAction> myEmbeddedReplaceActions = new ArrayList<>();
  private final List<Component> myExtraReplaceButtons = new ArrayList<>();

  private final JPanel myReplaceToolbarWrapper;

  private final Project myProject;
  private final JComponent myTargetComponent;
  @Nullable private OnePixelSplitter mySplitter;

  private final Runnable myCloseRunnable;
  private final Runnable myReplaceRunnable;

  private final DataProvider myDataProviderDelegate;

  private final boolean myMultilineEnabled;
  private boolean myMultilineMode;
  private final boolean myAddSearchResultsToGlobalSearch;
  private final SearchComponentMode myMode;

  @NotNull private @NlsContexts.Label String myStatusText = "";
  @NotNull private Color myStatusColor = ExperimentalUI.isNewUI() ? UIUtil.getLabelInfoForeground() : UIUtil.getLabelForeground();
  private final AnAction modeAction = new ModeAction();
  private static final Color EDITOR_BACKGROUND = JBColor.lazy(() -> EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());

  @Nullable private final ShortcutSet findActionShortcutSet;
  @Nullable private final ShortcutSet replaceActionShortcutSet;

  private final CloseAction myCloseAction = new CloseAction();

  @NotNull
  public static Builder buildFor(@Nullable Project project, @NotNull JComponent component) {
    return new Builder(project, component);
  }

  private SearchReplaceComponent(@Nullable Project project,
                                 @NotNull JComponent targetComponent,
                                 @NotNull DefaultActionGroup searchToolbar1Actions,
                                 @NotNull DefaultActionGroup searchToolbar2Actions,
                                 @NotNull DefaultActionGroup searchFieldActions,
                                 @NotNull DefaultActionGroup replaceToolbar1Actions,
                                 @NotNull DefaultActionGroup replaceToolbar2Actions,
                                 @NotNull DefaultActionGroup replaceFieldActions,
                                 @Nullable Runnable replaceRunnable,
                                 @Nullable Runnable closeRunnable,
                                 @Nullable DataProvider dataProvider,
                                 boolean showOnlySearchPanel,
                                 boolean maximizeLeftPanelOnResize,
                                 boolean multilineEnabled,
                                 boolean addSearchResultsToGlobalSearch,
                                 SearchComponentMode mode,
                                 boolean showSeparator) {
    myProject = project;
    myTargetComponent = targetComponent;
    mySearchFieldActions = searchFieldActions;
    myReplaceFieldActions = replaceFieldActions;
    myReplaceRunnable = replaceRunnable;
    myCloseRunnable = closeRunnable;
    myMultilineEnabled = multilineEnabled;
    myAddSearchResultsToGlobalSearch = addSearchResultsToGlobalSearch;
    myMode = mode;

    boolean isNewUI = ExperimentalUI.isNewUI();

    ActionManager actionManager = ActionManager.getInstance();
    findActionShortcutSet = actionManager.getAction(IdeActions.ACTION_FIND).getShortcutSet();
    replaceActionShortcutSet = actionManager.getAction(IdeActions.ACTION_REPLACE).getShortcutSet();

    for (AnAction child : searchToolbar2Actions.getChildren(null)) {
      if (child instanceof Embeddable) {
        myEmbeddedSearchActions.add(child);
        ShortcutSet shortcutSet = ActionUtil.getMnemonicAsShortcut(child);
        if (shortcutSet != null) child.registerCustomShortcutSet(shortcutSet, this);
      }
    }
    for (AnAction action : myEmbeddedSearchActions) {
      searchToolbar2Actions.remove(action);
    }
    for (AnAction child : replaceToolbar2Actions.getChildren(null)) {
      if (child instanceof Embeddable) {
        myEmbeddedReplaceActions.add(child);
        ShortcutSet shortcutSet = ActionUtil.getMnemonicAsShortcut(child);
        if (shortcutSet != null) child.registerCustomShortcutSet(shortcutSet, this);
      }
    }
    for (AnAction action : myEmbeddedReplaceActions) {
      replaceToolbar2Actions.remove(action);
    }

    mySearchFieldWrapper = new MyTextComponentWrapper() {
      @Override
      public void setContent(JComponent wrapped) {
        super.setContent(wrapped);
        mySearchTextComponent = unwrapTextComponent(wrapped);
      }
    };
    myReplaceFieldWrapper = new MyTextComponentWrapper() {
      @Override
      public void setContent(JComponent wrapped) {
        super.setContent(wrapped);
        myReplaceTextComponent = unwrapTextComponent(wrapped);
      }
    };
    myReplaceFieldWrapper.setBorder(JBUI.Borders.emptyTop(1));

    JPanel leftPanel = new JPanel(new GridBagLayout());
    leftPanel.setBackground(JBUI.CurrentTheme.Editor.BORDER_COLOR);
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1;
    constraints.weighty = 1;
    leftPanel.add(mySearchFieldWrapper, constraints);
    constraints.gridy++;
    leftPanel.add(myReplaceFieldWrapper, constraints);

    if (showSeparator) {
      leftPanel.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 0, 1));
    }

    searchToolbar1Actions.addAll(searchToolbar2Actions.getChildren(null));
    replaceToolbar1Actions.addAll(replaceToolbar2Actions.getChildren(null));

    JPanel searchPair = new NonOpaquePanel(new BorderLayout());
    searchPair.setBorder(isNewUI ? JBUI.Borders.emptyTop(3) : JBUI.Borders.empty());

    if (closeRunnable != null) {
      if (isNewUI) {
        searchToolbar1Actions.add(myCloseAction);
      }
      else {
        JLabel closeLabel = new JLabel(null, AllIcons.Actions.Close, SwingConstants.RIGHT);
        closeLabel.setBorder(JBUI.Borders.empty(2));
        closeLabel.addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(final MouseEvent e) {
            if (!e.isPopupTrigger() && SwingUtilities.isLeftMouseButton(e)) {
              close();
            }
          }
        });
        closeLabel.setToolTipText(FindBundle.message("tooltip.close.search.bar.escape"));
        searchPair.add(new Wrapper(closeLabel), BorderLayout.EAST);
      }
    }

    mySearchActionsToolbar = createToolbar(searchToolbar1Actions);
    mySearchActionsToolbar.setForceShowFirstComponent(true);
    searchPair.add(mySearchActionsToolbar, BorderLayout.CENTER);

    if (ExperimentalUI.isNewUI()) {
      mySearchActionsToolbar.setBackground(EDITOR_BACKGROUND);
      searchPair.setBackground(EDITOR_BACKGROUND);
    }

    myReplaceActionsToolbar = createReplaceToolbar1(replaceToolbar1Actions);
    myReplaceActionsToolbar.setBorder(JBUI.Borders.empty());
    myReplaceActionsToolbar.setOpaque(!isNewUI);
    Wrapper replaceToolbarWrapper1 = new Wrapper(myReplaceActionsToolbar);
    myReplaceToolbarWrapper = new NonOpaquePanel(new BorderLayout());
    myReplaceToolbarWrapper.add(replaceToolbarWrapper1, BorderLayout.WEST);
    myReplaceToolbarWrapper.setBorder(isNewUI ? JBUI.Borders.emptyTop(10) : JBUI.Borders.emptyTop(3));

    JPanel rightPanel = new NonOpaquePanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    rightPanel.add(searchPair);
    rightPanel.add(myReplaceToolbarWrapper);
    float initialProportion = maximizeLeftPanelOnResize? MAX_LEFT_PANEL_PROP : DEFAULT_PROP;

    if (isNewUI && myReplaceRunnable != null) {
      ActionToolbar modeToolbar = createToolbar(new DefaultActionGroup(modeAction));
      modeToolbar.setReservePlaceAutoPopupIcon(false);
      JComponent modeToolbarComponent = modeToolbar.getComponent();
      modeToolbarComponent.setBorder(JBUI.Borders.empty());
      modeToolbarComponent.setOpaque(false);

      JPanel modePanel = JBUI.Panels.simplePanel().addToTop(modeToolbar.getComponent());
      modePanel.setOpaque(true);
      modePanel.setBackground(EDITOR_BACKGROUND);
      modePanel.setBorder(JBUI.Borders.compound(JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 0, 1),
                                                JBUI.Borders.empty(7, 3)));
      add(modePanel, BorderLayout.WEST);
    }

    if (showOnlySearchPanel) {
      add(leftPanel, BorderLayout.CENTER);
    }
    else {
      if (maximizeLeftPanelOnResize){
        mySplitter = new OnePixelSplitter(false, initialProportion, initialProportion, initialProportion);
      } else {
        mySplitter = new OnePixelSplitter(false, initialProportion);
      }
      mySplitter.setFirstComponent(leftPanel);
      mySplitter.setSecondComponent(rightPanel);
      if (ExperimentalUI.isNewUI()) {
        mySearchActionsToolbar.setBackground(EDITOR_BACKGROUND);
        mySplitter.setBackground(EDITOR_BACKGROUND);
        mySplitter.setOpaque(true);
      } else {
        mySplitter.setOpaque(false);
      }
      mySplitter.getDivider().setOpaque(false);
      add(mySplitter, BorderLayout.CENTER);

      if (maximizeLeftPanelOnResize) {
        rightPanel.setLayout(new FlowLayout(CENTER, 0, 0));
        rightPanel.setBorder(JBUI.Borders.emptyLeft(RIGHT_PANEL_WEST_OFFSET));
        rightPanel.setMinimumSize(new Dimension(mySearchActionsToolbar.getActions().size()
                                                * ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width + RIGHT_PANEL_WEST_OFFSET, 0));
        mySearchActionsToolbar.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            var minWidth = 0;
            for(var component: rightPanel.getComponents()){
              minWidth += component.getPreferredSize().width;
            }
            rightPanel.setMinimumSize(new Dimension(minWidth, 0));
            mySplitter.updateUI();
          }
        });
        mySplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE);
        mySplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
        mySplitter.setResizeEnabled(false);
        mySplitter.setHonorComponentsMinimumSize(true);
        mySplitter.setHonorComponentsPreferredSize(false);
      }
      else {
        rightPanel.setBorder(JBUI.Borders.emptyLeft(6));
        mySplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        mySplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
        mySplitter.setHonorComponentsMinimumSize(true);
        mySplitter.setHonorComponentsPreferredSize(true);
        mySplitter.setAndLoadSplitterProportionKey("FindSplitterProportion");
      }
    }

    update("", "", false, false);

    // it's assigned after all action updates so that actions don't get access to uninitialized components
    myDataProviderDelegate = dataProvider;
    // A workaround to suppress editor-specific TabAction
    new TransferFocusAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)), this);
    new TransferFocusBackwardAction()
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)), this);

    if (SystemInfo.isMac) {
      DefaultActionGroup touchbarActions = new DefaultActionGroup();
      touchbarActions.add(new PrevOccurrenceAction());
      touchbarActions.add(new NextOccurrenceAction());
      Touchbar.setActions(this, touchbarActions);
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    addTextToRecent(mySearchTextComponent);
    if (myReplaceTextComponent != null) {
      addTextToRecent(myReplaceTextComponent);
    }
  }

  public void requestFocusInTheSearchFieldAndSelectContent(Project project) {
    mySearchTextComponent.selectAll();
    IdeFocusManager.getInstance(project).requestFocus(mySearchTextComponent, true);
    if (myReplaceTextComponent != null) {
      myReplaceTextComponent.selectAll();
    }
  }

  public void setStatusText(@NotNull @NlsContexts.Label String status) {
    myStatusText = status;
  }

  @NotNull
  public @NlsContexts.Label String getStatusText() {
    return myStatusText;
  }

  @NotNull
  public Color getStatusColor() {
    return myStatusColor;
  }

  public void replace() {
    if (myReplaceRunnable != null) {
      myReplaceRunnable.run();
    }
  }

  public void close() {
    if (myCloseRunnable != null) {
      myCloseRunnable.run();
    }
  }

  public void setRegularBackground() {
    mySearchTextComponent.setForeground(UIUtil.getTextFieldForeground());
    myStatusColor = ExperimentalUI.isNewUI() ? UIUtil.getLabelInfoForeground() : UIUtil.getLabelForeground();
  }

  public void setNotFoundBackground() {
    mySearchTextComponent.setForeground(JBColor.namedColor("SearchField.errorForeground", JBColor.RED));
    myStatusColor = NamedColorUtil.getErrorForeground();
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId)) {
      return mySearchTextComponent.getText();
    }
    return myDataProviderDelegate != null ? myDataProviderDelegate.getData(dataId) : null;
  }

  public Project getProject() {
    return myProject;
  }

  public void addListener(@NotNull Listener listener) {
    myEventDispatcher.addListener(listener);
  }

  public boolean isMultiline() {
    return myMultilineMode;
  }

  private void setMultilineInternal(boolean multiline) {
    boolean stateChanged = multiline != myMultilineMode;
    myMultilineMode = multiline;
    if (stateChanged) {
      myEventDispatcher.getMulticaster().multilineStateChanged();
    }
  }

  @NotNull
  public JTextComponent getSearchTextComponent() {
    return mySearchTextComponent;
  }

  @NotNull
  public JTextComponent getReplaceTextComponent() {
    return myReplaceTextComponent;
  }


  private void updateSearchComponent(@NotNull String textToSet) {
    if (!updateTextComponent(true)) {
      replaceTextInTextComponentEnsuringSelection(textToSet, mySearchTextComponent);
      return;
    }

    mySearchTextComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().searchFieldDocumentChanged());
      }
    });

    mySearchTextComponent.registerKeyboardAction(new ActionListener() {
                                                   @Override
                                                   public void actionPerformed(final ActionEvent e) {
                                                     if (StringUtil.isEmpty(mySearchTextComponent.getText())) {
                                                       close();
                                                     }
                                                     else {
                                                       IdeFocusManager.getInstance(myProject).requestFocus(myTargetComponent, true);
                                                       addTextToRecent(mySearchTextComponent);
                                                     }
                                                   }
                                                 }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac
                                                                                              ? META_DOWN_MASK : CTRL_DOWN_MASK),
                                                 JComponent.WHEN_FOCUSED);
    // make sure Enter is consumed by search text field, even if 'next occurrence' action is disabled
    // this is needed to e.g. avoid triggering a default button in containing dialog (see IDEA-128057)
    mySearchTextComponent.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {}
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);

    new VariantsCompletionAction(mySearchTextComponent); // It registers a shortcut set automatically on construction
  }

  private static void replaceTextInTextComponentEnsuringSelection(@NotNull String textToSet, JTextComponent component) {
    String existingText = component.getText();
    if (!existingText.equals(textToSet)) {
      component.setText(textToSet);
      // textToSet should be selected even if we have no selection before (if we have the selection then setText will remain it)
      if (component.getSelectionStart() == component.getSelectionEnd()) component.selectAll();
    }
  }

  private void updateReplaceComponent(@NotNull String textToSet) {
    if (!updateTextComponent(false)) {
      replaceTextInTextComponentEnsuringSelection(textToSet, myReplaceTextComponent);
      return;
    }
    myReplaceTextComponent.setText(textToSet);

    myReplaceTextComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> myEventDispatcher.getMulticaster().replaceFieldDocumentChanged());
      }
    });

    if (!isMultiline()) {
      installReplaceOnEnterAction(myReplaceTextComponent);
    }

    new VariantsCompletionAction(myReplaceTextComponent);
    myReplaceFieldWrapper.revalidate();
    myReplaceFieldWrapper.repaint();
  }

  public void update(@NotNull String findText, @NotNull String replaceText, boolean replaceMode, boolean multiline) {
    setMultilineInternal(multiline);
    boolean needToResetSearchFocus = mySearchTextComponent != null && mySearchTextComponent.hasFocus();
    boolean needToResetReplaceFocus = myReplaceTextComponent != null && myReplaceTextComponent.hasFocus();
    updateSearchComponent(findText);
    updateReplaceComponent(replaceText);
    myReplaceFieldWrapper.setVisible(replaceMode);
    myReplaceToolbarWrapper.setVisible(replaceMode);

    if (needToResetReplaceFocus) myReplaceTextComponent.requestFocusInWindow();
    if (needToResetSearchFocus) mySearchTextComponent.requestFocusInWindow();
    updateBindings();
    updateActions();
    List<Component> focusOrder = new ArrayList<>();
    focusOrder.add(mySearchTextComponent);
    focusOrder.add(myReplaceTextComponent);
    focusOrder.addAll(myExtraSearchButtons);
    focusOrder.addAll(myExtraReplaceButtons);
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new ListFocusTraversalPolicy(focusOrder));
    revalidate();
    repaint();
  }

  public void updateActions() {
    mySearchActionsToolbar.updateActionsImmediately();
    myReplaceActionsToolbar.updateActionsImmediately();
    JComponent textComponent = mySearchFieldWrapper.getTargetComponent();
    if (textComponent instanceof SearchTextArea) {
      ((SearchTextArea)textComponent).updateExtraActions();
      ((SearchTextArea)textComponent).setMultilineEnabled(myMultilineEnabled);
    }
    textComponent = myReplaceFieldWrapper.getTargetComponent();
    if (textComponent instanceof SearchTextArea) ((SearchTextArea)textComponent).updateExtraActions();
  }

  public void addTextToRecent(@NotNull JTextComponent textField) {
    if (myProject.isDisposed()) return;
    final String text = textField.getText();
    FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);
    if (textField == mySearchTextComponent) {
      if (myAddSearchResultsToGlobalSearch) {
        findInProjectSettings.addStringToFind(text);
      }
      if (mySearchFieldWrapper.getTargetComponent() instanceof SearchTextField) {
        ((SearchTextField)mySearchFieldWrapper.getTargetComponent()).addCurrentTextToHistory();
      }
    }
    else {
      findInProjectSettings.addStringToReplace(text);
      if (myReplaceFieldWrapper.getTargetComponent() instanceof SearchTextField) {
        ((SearchTextField)myReplaceFieldWrapper.getTargetComponent()).addCurrentTextToHistory();
      }
    }
  }

  private boolean updateTextComponent(boolean search) {
    JTextComponent oldComponent = search ? mySearchTextComponent : myReplaceTextComponent;
    if (oldComponent != null) return false;
    final MyTextComponentWrapper wrapper = search ? mySearchFieldWrapper : myReplaceFieldWrapper;

    @NotNull JTextComponent innerTextComponent;
    @NotNull JComponent outerComponent;

    if (myMode instanceof SearchTextFieldMode mode) {
      outerComponent = new SearchTextField(
        mode.searchHistoryEnabled,
        mode.clearSearchActionEnabled,
        mode.searchHistoryEnabled ? this.toString() : null);
      innerTextComponent = ((SearchTextField)outerComponent).getTextEditor();
      innerTextComponent.setBorder(BorderFactory.createEmptyBorder());
    }
    else {
      innerTextComponent = new JBTextArea() {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
          Dimension defaultSize = super.getPreferredScrollableViewportSize();
          if (mySplitter != null &&
              mySplitter.getSecondComponent() != null &&
              Registry.is("ide.find.expand.search.field.on.typing", true)) {
            Dimension preferredSize = getPreferredSize();
            Dimension minimumSize = getMinimumSize();
            int spaceForLeftPanel =
              mySplitter.getWidth() - mySplitter.getSecondComponent().getPreferredSize().width - mySplitter.getDividerWidth();
            int allSearchTextAreaIcons = JBUI.scale(180);
            int w = spaceForLeftPanel - allSearchTextAreaIcons;
            w = Math.max(w, minimumSize.width);
            return new Dimension(Math.min(Math.max(defaultSize.width, preferredSize.width), w), defaultSize.height);
          }
          return defaultSize;
        }
      };
      ((JBTextArea)innerTextComponent).setRows(isMultiline() ? 2 : 1);
      ((JBTextArea)innerTextComponent).setColumns(12);
      innerTextComponent.setMinimumSize(new Dimension(150, 0));
      outerComponent = new SearchTextArea(((JBTextArea)innerTextComponent), search);
      if (search) {
        myExtraSearchButtons.clear();
        myExtraSearchButtons
          .addAll(((SearchTextArea)outerComponent).setExtraActions(myEmbeddedSearchActions.toArray(AnAction.EMPTY_ARRAY)));
        ((SearchTextArea)outerComponent).setMultilineEnabled(myMultilineEnabled);
      }
      else {
        myExtraReplaceButtons.clear();
        myExtraReplaceButtons
          .addAll(((SearchTextArea)outerComponent).setExtraActions(myEmbeddedReplaceActions.toArray(AnAction.EMPTY_ARRAY)));
      }
    }

    SwingUndoUtil.addUndoRedoActions(innerTextComponent);
    wrapper.setContent(outerComponent);

    if (search) {
      innerTextComponent.getAccessibleContext().setAccessibleName(FindBundle.message("find.search.accessible.name"));
    }
    else {
      innerTextComponent.getAccessibleContext().setAccessibleName(FindBundle.message("find.replace.accessible.name"));
    }
    // Display empty text only when focused
    innerTextComponent.putClientProperty(
      TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JTextComponent>)(c -> c.getText().isEmpty() && c.isFocusOwner()));

    innerTextComponent.putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, Boolean.TRUE);
    innerTextComponent.setBackground(UIUtil.getTextFieldBackground());
    JComponent finalTextComponent = innerTextComponent;
    innerTextComponent.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(final FocusEvent e) {
        finalTextComponent.repaint();
      }

      @Override
      public void focusLost(final FocusEvent e) {
        finalTextComponent.repaint();
      }
    });

    myCloseAction.registerOn(outerComponent);
    return true;
  }

  private class CloseAction extends DumbAwareAction implements LightEditCompatible, RightAlignedToolbarAction {
    private final ShortcutSet shortcut = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_ESCAPE);
    private CloseAction() {
      getTemplatePresentation().setText(FindBundle.message("find.close.button.name"));
      getTemplatePresentation().setIcon(ExperimentalUI.isNewUI() ? ExpUiIcons.General.Close : AllIcons.Actions.Close);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      close();
    }

    private void registerOn(@NotNull JComponent component) {
      registerCustomShortcutSet(shortcut, component);
    }
  }

  private void installReplaceOnEnterAction(@NotNull JTextComponent c) {
    ActionListener action = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        replace();
      }
    };
    c.registerKeyboardAction(action, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
  }

  private void updateBindings() {
    updateBindings(mySearchFieldActions, mySearchFieldWrapper);
    updateBindings(mySearchActionsToolbar, mySearchFieldWrapper);
    updateBindings(Collections.singletonList(modeAction), mySearchFieldWrapper);

    updateBindings(myReplaceFieldActions, myReplaceFieldWrapper);
    updateBindings(myReplaceActionsToolbar, myReplaceToolbarWrapper);
  }

  private void updateBindings(@NotNull DefaultActionGroup group, @NotNull JComponent shortcutHolder) {
    updateBindings(List.of(group.getChildActionsOrStubs()), shortcutHolder);
  }

  private void updateBindings(@NotNull ActionToolbarImpl toolbar, @NotNull JComponent shortcutHolder) {
    updateBindings(toolbar.getActions(), shortcutHolder);
  }

  private void updateBindings(@NotNull List<? extends AnAction> actions, @NotNull JComponent shortcutHolder) {
    DataContext context = DataManager.getInstance().getDataContext(this);
    for (AnAction action : actions) {
      ShortcutSet shortcut = null;
      if (action instanceof DefaultActionGroup) {
        updateBindings((DefaultActionGroup)action, shortcutHolder);
      }

      if (action instanceof ContextAwareShortcutProvider) {
        shortcut = ((ContextAwareShortcutProvider)action).getShortcut(context);
      }
      else if (action instanceof ShortcutProvider) {
        shortcut = ((ShortcutProvider)action).getShortcut();
      }
      if (shortcut != null) {
        action.registerCustomShortcutSet(shortcut, shortcutHolder);
      }
    }
  }


  @NotNull
  private ActionToolbarImpl createReplaceToolbar1(@NotNull DefaultActionGroup group) {
    ActionToolbarImpl toolbar = createToolbar(group);
    toolbar.setForceMinimumSize(true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    return toolbar;
  }

  @NotNull
  private ActionToolbarImpl createToolbar(@NotNull ActionGroup group) {
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true);
    toolbar.setTargetComponent(this);
    toolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    Utils.setSmallerFontForChildren(toolbar);
    return toolbar;
  }

  public interface Listener extends EventListener {
    void searchFieldDocumentChanged();

    void replaceFieldDocumentChanged();

    void multilineStateChanged();

    default void toggleSearchReplaceMode() {}
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public static final class Builder {
    private final Project myProject;
    private final JComponent myTargetComponent;

    private DataProvider myDataProvider;

    private Runnable myReplaceAction;
    private Runnable myCloseAction;

    private final DefaultActionGroup mySearchActions = DefaultActionGroup.createFlatGroup(() -> "search bar 1");
    private final DefaultActionGroup myExtraSearchActions = DefaultActionGroup.createFlatGroup(() -> "search bar 2");
    private final DefaultActionGroup mySearchFieldActions = DefaultActionGroup.createFlatGroup(() -> "search field actions");

    private final DefaultActionGroup myReplaceActions = DefaultActionGroup.createFlatGroup(() -> "replace bar 1");
    private final DefaultActionGroup myExtraReplaceActions = DefaultActionGroup.createFlatGroup(() -> "replace bar 1");
    private final DefaultActionGroup myReplaceFieldActions = DefaultActionGroup.createFlatGroup(() -> "replace field actions");

    private boolean myShowOnlySearchPanel = false;
    private boolean myMaximizeLeftPanelOnResize = false;
    private boolean myMultilineEnabled = true;
    private boolean myAddSearchResultsToGlobalSearch = true;
    private boolean myShowSeparator = true;

    private SearchComponentMode myMode;

    private Builder(@Nullable Project project, @NotNull JComponent component) {
      myProject = project;
      myTargetComponent = component;
      myMode = new TextAreaMode();
    }

    @NotNull
    public Builder withDataProvider(@NotNull DataProvider provider) {
      myDataProvider = provider;
      return this;
    }

    @NotNull
    public Builder withReplaceAction(@NotNull Runnable action) {
      myReplaceAction = action;
      return this;
    }

    @NotNull
    public Builder withCloseAction(@NotNull Runnable action) {
      myCloseAction = action;
      return this;
    }

    @NotNull
    public Builder addSearchFieldActions(AnAction @NotNull ... actions) {
      mySearchFieldActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addReplaceFieldActions(AnAction @NotNull ... actions) {
      myReplaceFieldActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addPrimarySearchActions(AnAction @NotNull ... actions) {
      mySearchActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addSecondarySearchActions(AnAction @NotNull ... actions) {
      for (AnAction action : actions) {
        mySearchActions.addAction(action).setAsSecondary(true);
      }
      return this;
    }

    @NotNull
    public Builder addExtraSearchActions(AnAction @NotNull ... actions) {
      myExtraSearchActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addPrimaryReplaceActions(AnAction @NotNull ... actions) {
      myReplaceActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addExtraReplaceAction(AnAction @NotNull ... actions) {
      myExtraReplaceActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder withShowOnlySearchPanel() {
      myShowOnlySearchPanel = true;
      return this;
    }

    @NotNull
    public Builder withMaximizeLeftPanelOnResize() {
      myMaximizeLeftPanelOnResize = true;
      return this;
    }

    @NotNull
    public SearchReplaceComponent build() {
      return new SearchReplaceComponent(myProject,
                                        myTargetComponent,
                                        mySearchActions,
                                        myExtraSearchActions,
                                        mySearchFieldActions,
                                        myReplaceActions,
                                        myExtraReplaceActions,
                                        myReplaceFieldActions,
                                        myReplaceAction,
                                        myCloseAction,
                                        myDataProvider,
                                        myShowOnlySearchPanel,
                                        myMaximizeLeftPanelOnResize,
                                        myMultilineEnabled,
                                        myAddSearchResultsToGlobalSearch,
                                        myMode,
                                        myShowSeparator);
    }

    @NotNull
    public Builder withMultilineEnabled(boolean b) {
      myMultilineEnabled = b;
      return this;
    }

    @NotNull
    public Builder withAddSearchResultsToGlobalSearch(boolean b) {
      myAddSearchResultsToGlobalSearch = b;
      return this;
    }

    /**
     * @deprecated use {@link #withUseSearchField(boolean, boolean)} instead to specify explicitly search field look and features
     */
    @NotNull
    @Deprecated(forRemoval = true)
    public Builder withUseSearchField(boolean b) {
      return withUseSearchField(true, true);
    }

    @NotNull
    public Builder withUseSearchField(boolean enableSearchHistory, boolean enableClearSearchAction) {
      myMode = new SearchTextFieldMode(enableSearchHistory, enableClearSearchAction);
      return this;
    }

    @NotNull
    public Builder withoutSeparator() {
      myShowSeparator = false;
      return this;
    }
  }

  private interface SearchComponentMode { }
  private static class TextAreaMode implements SearchComponentMode { }
  private static class SearchTextFieldMode implements SearchComponentMode {
    public final boolean searchHistoryEnabled;
    public final boolean clearSearchActionEnabled;

    private SearchTextFieldMode(boolean searchHistoryEnabled, boolean clearSearchActionEnabled) {
      this.searchHistoryEnabled = searchHistoryEnabled;
      this.clearSearchActionEnabled = clearSearchActionEnabled;
    }
  }

  private static class MyTextComponentWrapper extends Wrapper {
    @Nullable
    public JTextComponent getTextComponent() {
      JComponent wrapped = getTargetComponent();
      return wrapped != null ? unwrapTextComponent(wrapped) : null;
    }

    @NotNull
    protected static JTextComponent unwrapTextComponent(@NotNull JComponent wrapped) {
      if (wrapped instanceof SearchTextField) {
        return ((SearchTextField)wrapped).getTextEditor();
      }
      if (wrapped instanceof SearchTextArea) {
        return ((SearchTextArea)wrapped).getTextArea();
      }
      throw new AssertionError();
    }
  }

  private class TransferFocusAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Component focusOwner = IdeFocusManager.getInstance(myProject).getFocusOwner();
      if (UIUtil.isAncestor(SearchReplaceComponent.this, focusOwner)) focusOwner.transferFocus();
    }
  }

  private class TransferFocusBackwardAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Component focusOwner = IdeFocusManager.getInstance(myProject).getFocusOwner();
      if (UIUtil.isAncestor(SearchReplaceComponent.this, focusOwner)) focusOwner.transferFocusBackward();
    }
  }

  private class ModeAction extends DumbAwareAction implements ContextAwareShortcutProvider {
    private ModeAction() {
      getTemplatePresentation().setIcon(AllIcons.General.ChevronRight);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      DataContext context = e.getDataContext();
      SearchSession search = SearchSession.KEY.getData(context);

      boolean replaceMode = search != null && search.getFindModel().isReplaceState();

      Presentation presentation = e.getPresentation();
      presentation.setIcon(replaceMode ? AllIcons.General.ChevronDown : AllIcons.General.ChevronRight);
      presentation.setText(FindBundle.message(replaceMode ? "find.tooltip.switch.to.find" : "find.tooltip.switch.to.replace"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myEventDispatcher.getMulticaster().toggleSearchReplaceMode();
    }


    @Override
    public @Nullable ShortcutSet getShortcut(@NotNull DataContext context) {
      SearchSession search = SearchSession.KEY.getData(context);
      return search != null && search.getFindModel().isReplaceState() ? findActionShortcutSet : replaceActionShortcutSet;
    }
  }
}
