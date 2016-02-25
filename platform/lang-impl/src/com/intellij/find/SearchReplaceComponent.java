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
package com.intellij.find;

import com.intellij.find.editorHeaderActions.ContextAwareShortcutProvider;
import com.intellij.find.editorHeaderActions.ShowMoreOptions;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.find.editorHeaderActions.VariantsCompletionAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.EventListener;
import java.util.List;

public class SearchReplaceComponent extends EditorHeaderComponent implements DataProvider {
  private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

  private final MyTextComponentWrapper mySearchFieldWrapper;
  private JTextComponent mySearchTextComponent;

  private final MyTextComponentWrapper myReplaceFieldWrapper;
  private JTextComponent myReplaceTextComponent;

  private final JPanel myLeftPanel;
  private final JPanel myRightPanel;

  private final DefaultActionGroup mySearchFieldActions;
  private final ActionToolbarImpl mySearchActionsToolbar1;
  private final ActionToolbarImpl mySearchActionsToolbar2;
  private final ActionToolbarImpl.PopupStateModifier mySearchToolbar1PopupStateModifier;

  private final DefaultActionGroup myReplaceFieldActions;
  private final ActionToolbarImpl myReplaceActionsToolbar1;
  private final ActionToolbarImpl myReplaceActionsToolbar2;
  private final JPanel myReplaceToolbarWrapper;

  private final Project myProject;
  private final JComponent myTargetComponent;

  private final Runnable myCloseAction;
  private final Runnable myReplaceAction;

  private final DataProvider myDataProviderDelegate;

  private boolean myMultilineMode;
  private String myStatusText = "";

  @NotNull
  public static Builder buildFor(@Nullable Project project, @NotNull JComponent component) {
    return new Builder(project, component);
  }

  private SearchReplaceComponent(@Nullable Project project,
                                 @NotNull JComponent targetComponent,
                                 @NotNull DefaultActionGroup searchToolbar1Actions,
                                 @NotNull final BooleanGetter searchToolbar1ModifiedFlagGetter,
                                 @NotNull DefaultActionGroup searchToolbar2Actions,
                                 @NotNull DefaultActionGroup searchFieldActions,
                                 @NotNull DefaultActionGroup replaceToolbar1Actions,
                                 @NotNull DefaultActionGroup replaceToolbar2Actions,
                                 @NotNull DefaultActionGroup replaceFieldActions,
                                 @Nullable Runnable replaceAction,
                                 @Nullable Runnable closeAction,
                                 @Nullable DataProvider dataProvider) {
    myProject = project;
    myTargetComponent = targetComponent;
    mySearchFieldActions = searchFieldActions;
    myReplaceFieldActions = replaceFieldActions;
    myReplaceAction = replaceAction;
    myCloseAction = closeAction;

    mySearchToolbar1PopupStateModifier = new ActionToolbarImpl.PopupStateModifier() {
      @Override
      public int getModifiedPopupState() {
        return ActionButtonComponent.PUSHED;
      }

      @Override
      public boolean willModify() {
        return searchToolbar1ModifiedFlagGetter.get();
      }
    };

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

    myLeftPanel = new NonOpaquePanel(new BorderLayout());
    myLeftPanel.add(mySearchFieldWrapper, BorderLayout.NORTH);
    myLeftPanel.add(myReplaceFieldWrapper, BorderLayout.CENTER);

    mySearchActionsToolbar1 = createSearchToolbar1(searchToolbar1Actions);
    Wrapper searchToolbarWrapper1 = new NonOpaquePanel(new BorderLayout());
    searchToolbarWrapper1.add(mySearchActionsToolbar1, BorderLayout.WEST);
    mySearchActionsToolbar2 = createSearchToolbar2(searchToolbar2Actions);
    Wrapper searchToolbarWrapper2 = new Wrapper(mySearchActionsToolbar2);
    mySearchActionsToolbar2.setBorder(JBUI.Borders.emptyLeft(16));
    JPanel searchPair = new NonOpaquePanel(new BorderLayout()).setVerticalSizeReferent(mySearchFieldWrapper);
    searchPair.add(searchToolbarWrapper1, BorderLayout.WEST);
    searchPair.add(searchToolbarWrapper2, BorderLayout.CENTER);

    myReplaceActionsToolbar1 = createReplaceToolbar1(replaceToolbar1Actions);
    Wrapper replaceToolbarWrapper1 = new Wrapper(myReplaceActionsToolbar1).setVerticalSizeReferent(myReplaceFieldWrapper);
    myReplaceActionsToolbar2 = createReplaceToolbar2(replaceToolbar2Actions);
    Wrapper replaceToolbarWrapper2 = new Wrapper(myReplaceActionsToolbar2).setVerticalSizeReferent(myReplaceFieldWrapper);
    myReplaceActionsToolbar2.setBorder(JBUI.Borders.emptyLeft(16));
    myReplaceToolbarWrapper = new NonOpaquePanel(new BorderLayout());
    myReplaceToolbarWrapper.add(replaceToolbarWrapper1, BorderLayout.WEST);
    myReplaceToolbarWrapper.add(replaceToolbarWrapper2, BorderLayout.CENTER);

    searchToolbarWrapper1.setHorizontalSizeReferent(replaceToolbarWrapper1);

    JLabel closeLabel = new JLabel(null, AllIcons.Actions.Cross, SwingConstants.RIGHT);
    closeLabel.setBorder(JBUI.Borders.empty(5));
    closeLabel.setVerticalAlignment(SwingConstants.TOP);
    closeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });
    closeLabel.setToolTipText("Close search bar (Escape)");
    searchPair.add(new Wrapper.North(closeLabel), BorderLayout.EAST);

    myRightPanel = new NonOpaquePanel(new BorderLayout());
    myRightPanel.add(searchPair, BorderLayout.NORTH);
    myRightPanel.add(myReplaceToolbarWrapper, BorderLayout.CENTER);

    OnePixelSplitter splitter = new OnePixelSplitter(false, .25F);
    splitter.setFirstComponent(myLeftPanel);
    splitter.setSecondComponent(myRightPanel);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setAndLoadSplitterProportionKey("FindSplitterProportion");
    splitter.setOpaque(false);
    splitter.getDivider().setOpaque(false);
    add(splitter, BorderLayout.CENTER);

    update("", "", false, false);

    // it's assigned after all action updates so that actions don't get access to uninitialized components
    myDataProviderDelegate = dataProvider;
  }

  public void resetUndoRedoActions() {
    UIUtil.resetUndoRedoActions(mySearchTextComponent);
    UIUtil.resetUndoRedoActions(myReplaceTextComponent);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    addTextToRecent(mySearchTextComponent);
    if (myReplaceTextComponent != null) {
      addTextToRecent(myReplaceTextComponent);
    }
  }

  public void requestFocusInTheSearchFieldAndSelectContent (Project project) {
    mySearchTextComponent.setSelectionStart(0);
    mySearchTextComponent.setSelectionEnd(mySearchTextComponent.getText().length());
    IdeFocusManager.getInstance(project).requestFocus(mySearchTextComponent, true);
  }

  public void setStatusText(@NotNull String status) {
    myStatusText = status;
  }

  @NotNull
  public String getStatusText() {
    return myStatusText;
  }

  public void replace() {
    if (myReplaceAction != null) {
      myReplaceAction.run();
    }
  }

  public void close() {
    if (myCloseAction != null ) {
      myCloseAction.run();
    }
  }

  public void setRegularBackground() {
    mySearchTextComponent.setBackground(UIUtil.getTextFieldBackground());
  }

  public void setNotFoundBackground() {
    mySearchTextComponent.setBackground(LightColors.RED);
  }

  @Override
  public Insets getInsets() {
    Insets insets = super.getInsets();
    if (UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel()) {
      insets.top += 1;
      insets.bottom += 2;
    }
    return insets;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY.is(dataId)) {
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
      multilineStateChanged();
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
    final int oldCaretPosition = mySearchTextComponent != null ? mySearchTextComponent.getCaretPosition() : 0;
    boolean wasNull = mySearchTextComponent == null;
    if (!updateTextComponent(true)) {
      if (!mySearchTextComponent.getText().equals(textToSet)) {
        mySearchTextComponent.setText(textToSet);
      }
      return;
    }

    if (!mySearchTextComponent.getText().equals(textToSet)) {
      mySearchTextComponent.setText(textToSet);
      if (wasNull) {
        mySearchTextComponent.selectAll();
      }
    }
    mySearchTextComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            searchFieldDocumentChanged();
          }
        });
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
                                                 }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK),
                                                 JComponent.WHEN_FOCUSED);
    if (!wasNull) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          mySearchTextComponent.setCaretPosition(Math.min(oldCaretPosition, mySearchTextComponent.getText().length()));
        }
      });
    }

    new VariantsCompletionAction(mySearchTextComponent); // It registers a shortcut set automatically on construction
  }

  private void updateReplaceComponent(@NotNull String textToSet) {
    final int oldCaretPosition = myReplaceTextComponent != null ? myReplaceTextComponent.getCaretPosition() : 0;
    if (!updateTextComponent(false)) {
      return;
    }
    myReplaceTextComponent.setText(textToSet);

    myReplaceTextComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            replaceFieldDocumentChanged();
          }
        });
      }
    });

    if (!isMultiline()) {
      installReplaceOnEnterAction(myReplaceTextComponent);
    }

    //myReplaceTextComponent.setText(myFindModel.getStringToReplace());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myReplaceTextComponent.setCaretPosition(oldCaretPosition);
      }
    });

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
    if (replaceMode) {
      if (myReplaceFieldWrapper.getParent() == null) {
        myLeftPanel.add(myReplaceFieldWrapper, BorderLayout.CENTER);
      }
      if (myReplaceToolbarWrapper.getParent() == null) {
        myRightPanel.add(myReplaceToolbarWrapper, BorderLayout.CENTER);
      }
      if (needToResetReplaceFocus) {
        myReplaceTextComponent.requestFocusInWindow();
      }
    } else {
      if (myReplaceFieldWrapper.getParent() != null) {
        myLeftPanel.remove(myReplaceFieldWrapper);
      }
      if (myReplaceToolbarWrapper.getParent() != null) {
        myRightPanel.remove(myReplaceToolbarWrapper);
      }
    }
    if (needToResetSearchFocus) mySearchTextComponent.requestFocusInWindow();
    updateBindings();
    updateActions();
    revalidate();
    repaint();
  }

  public void updateActions() {
    mySearchActionsToolbar1.updateActionsImmediately();
    mySearchActionsToolbar2.updateActionsImmediately();
    myReplaceActionsToolbar1.updateActionsImmediately();
    myReplaceActionsToolbar2.updateActionsImmediately();
  }

  public void addTextToRecent(@NotNull JTextComponent textField) {
    final String text = textField.getText();
    if (text.length() > 0) {
      if (textField == mySearchTextComponent) {
        FindSettings.getInstance().addStringToFind(text);
        if (mySearchFieldWrapper.getTargetComponent() instanceof SearchTextField) {
          ((SearchTextField)mySearchFieldWrapper.getTargetComponent()).addCurrentTextToHistory();
        }
      } else {
        FindSettings.getInstance().addStringToReplace(text);
        if (myReplaceFieldWrapper.getTargetComponent() instanceof SearchTextField) {
          ((SearchTextField)myReplaceFieldWrapper.getTargetComponent()).addCurrentTextToHistory();
        }
      }
    }
  }

  private boolean updateTextComponent(boolean search) {
    JTextComponent oldComponent = search ? mySearchTextComponent : myReplaceTextComponent;
    Color oldBackground = oldComponent != null ? oldComponent.getBackground() : null;
    final MyTextComponentWrapper wrapper = search ? mySearchFieldWrapper : myReplaceFieldWrapper;
    if (isMultiline() && oldComponent instanceof JTextArea) return false;
    if (!isMultiline() && oldComponent instanceof JTextField) return false;

    final JTextComponent textComponent;
    if (isMultiline()) {
      SearchTextArea textArea = new SearchTextArea(search);
      textComponent = textArea.getTextArea();
      ((JTextArea)textComponent).setColumns(25);
      ((JTextArea)textComponent).setRows(2);
      wrapper.setContent(textArea);
    }
    else {
      SearchTextField searchTextField = new SearchTextField(true);
      searchTextField.setOpaque(false);
      textComponent = searchTextField.getTextEditor();
      searchTextField.getTextEditor().setColumns(25);
      if (UIUtil.isUnderGTKLookAndFeel()) {
        textComponent.setOpaque(false);
      }
      searchTextField.setHistorySize(20);
      searchTextField.setHistory(ContainerUtil.reverse(Arrays.asList(search ? FindSettings.getInstance().getRecentFindStrings()
                                                                            : FindSettings.getInstance().getRecentReplaceStrings())));
      textComponent.registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          final String text = textComponent.getText();
          setMultilineInternal(true);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ObjectUtils.assertNotNull(wrapper.getTextComponent()).setText(text + "\n");
            }
          });
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), JComponent.WHEN_FOCUSED);
      wrapper.setContent(searchTextField);
    }

    UIUtil.addUndoRedoActions(textComponent);
    Utils.setSmallerFont(textComponent);

    textComponent.putClientProperty("AuxEditorComponent", Boolean.TRUE);
    if (oldBackground != null) {
      textComponent.setBackground(oldBackground);
    }
    textComponent.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(final FocusEvent e) {
        textComponent.repaint();
      }

      @Override
      public void focusLost(final FocusEvent e) {
        textComponent.repaint();
      }
    });
    installCloseOnEscapeAction(textComponent);
    return true;
  }

  private void searchFieldDocumentChanged() {
    if (mySearchTextComponent instanceof JTextArea) {
      adjustRows((JTextArea)mySearchTextComponent);
    }
    myEventDispatcher.getMulticaster().searchFieldDocumentChanged();
  }

  private void replaceFieldDocumentChanged() {
    if (myReplaceTextComponent instanceof JTextArea) {
      adjustRows((JTextArea)myReplaceTextComponent);
    }
    myEventDispatcher.getMulticaster().replaceFieldDocumentChanged();
  }

  private void multilineStateChanged() {
    myEventDispatcher.getMulticaster().multilineStateChanged();
  }

  private static void adjustRows(@NotNull JTextArea area) {
    area.setRows(Math.max(2, Math.min(3, StringUtil.countChars(area.getText(),'\n')+1)));
  }


  private void installCloseOnEscapeAction(@NotNull JTextComponent c) {
    ActionListener action = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        close();
      }
    };
    c.registerKeyboardAction(action, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    if (KeymapUtil.isEmacsKeymap()) {
      c.registerKeyboardAction(action, KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);
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
    updateBindings(mySearchActionsToolbar1, mySearchFieldWrapper);
    updateBindings(mySearchActionsToolbar2, mySearchFieldWrapper);

    updateBindings(myReplaceFieldActions, myReplaceFieldWrapper);
    updateBindings(myReplaceActionsToolbar1, myReplaceToolbarWrapper);
    updateBindings(myReplaceActionsToolbar2, myReplaceToolbarWrapper);
  }

  private void updateBindings(@NotNull DefaultActionGroup group, @NotNull JComponent shortcutHolder) {
    updateBindings(ContainerUtil.immutableList(group.getChildActionsOrStubs()), shortcutHolder);
  }

  private void updateBindings(@NotNull ActionToolbarImpl toolbar, @NotNull JComponent shortcutHolder) {
    updateBindings(toolbar.getActions(true), shortcutHolder);
  }

  private void updateBindings(@NotNull List<? extends AnAction> actions, @NotNull JComponent shortcutHolder) {
    DataContext context = DataManager.getInstance().getDataContext(this);
    for (AnAction action : actions) {
      ShortcutSet shortcut = null;
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
  private ActionToolbarImpl createSearchToolbar1(@NotNull DefaultActionGroup group) {
    ActionToolbarImpl toolbar = createToolbar(group);
    toolbar.setForceMinimumSize(true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setSecondaryButtonPopupStateModifier(mySearchToolbar1PopupStateModifier);
    toolbar.setSecondaryActionsTooltip("More Options(" + ShowMoreOptions.SHORT_CUT + ")");
    new ShowMoreOptions(toolbar, mySearchFieldWrapper);
    return toolbar;
  }

  @NotNull
  private ActionToolbarImpl createSearchToolbar2(@NotNull DefaultActionGroup group) {
    return createToolbar(group);
  }

  @NotNull
  private ActionToolbarImpl createReplaceToolbar1(@NotNull DefaultActionGroup group) {
    ActionToolbarImpl toolbar = createToolbar(group);
    toolbar.setForceMinimumSize(true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    return toolbar;
  }

  @NotNull
  private ActionToolbarImpl createReplaceToolbar2(@NotNull DefaultActionGroup group) {
    return createToolbar(group);
  }

  @NotNull
  private ActionToolbarImpl createToolbar(@NotNull ActionGroup group) {
    return tweakToolbar((ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true));
  }

  @NotNull
  private ActionToolbarImpl tweakToolbar(@NotNull ActionToolbarImpl toolbar) {
    toolbar.setTargetComponent(this);
    toolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    toolbar.setBorder(null);
    toolbar.setOpaque(false);
    Utils.setSmallerFontForChildren(toolbar);
    return toolbar;
  }


  public interface Listener extends EventListener {
    void searchFieldDocumentChanged();

    void replaceFieldDocumentChanged();

    void multilineStateChanged();
  }

  public static class Builder {
    private final Project myProject;
    private final JComponent myTargetComponent;

    private DataProvider myDataProvider;

    private Runnable myReplaceAction;
    private Runnable myCloseAction;

    private DefaultActionGroup mySearchActions      = new DefaultActionGroup("search bar 1", false);
    private DefaultActionGroup myExtraSearchActions = new DefaultActionGroup("search bar 2", false);
    private DefaultActionGroup mySearchFieldActions = new DefaultActionGroup("search field actions", false);
    private BooleanGetter mySearchToolbarModifiedFlagGetter = BooleanGetter.FALSE;

    private DefaultActionGroup myReplaceActions      = new DefaultActionGroup("replace bar 1", false);
    private DefaultActionGroup myExtraReplaceActions = new DefaultActionGroup("replace bar 1", false);
    private DefaultActionGroup myReplaceFieldActions = new DefaultActionGroup("replace field actions", false);

    private Builder(@Nullable Project project, @NotNull JComponent component) {
      myProject = project;
      myTargetComponent = component;
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
    public Builder addSearchFieldActions(@NotNull AnAction... actions) {
      mySearchFieldActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addReplaceFieldActions(@NotNull AnAction... actions) {
      myReplaceFieldActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addPrimarySearchActions(@NotNull AnAction... actions) {
      mySearchActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addSecondarySearchActions(@NotNull AnAction... actions) {
      for (AnAction action : actions) {
        mySearchActions.addAction(action).setAsSecondary(true);
      }
      return this;
    }

    @NotNull
    public Builder withSecondarySearchActionsIsModifiedGetter(@NotNull BooleanGetter getter) {
      mySearchToolbarModifiedFlagGetter = getter;
      return this;
    }

    @NotNull
    public Builder addExtraSearchActions(@NotNull AnAction... actions) {
      myExtraSearchActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addPrimaryReplaceActions(@NotNull AnAction... actions) {
      myReplaceActions.addAll(actions);
      return this;
    }

    @NotNull
    public Builder addExtraReplaceAction(@NotNull AnAction... actions) {
      myExtraReplaceActions.addAll(actions);
      return this;
    }

    @NotNull
    public SearchReplaceComponent build() {
      return new SearchReplaceComponent(myProject,
                                        myTargetComponent,
                                        mySearchActions,
                                        mySearchToolbarModifiedFlagGetter,
                                        myExtraSearchActions,
                                        mySearchFieldActions,
                                        myReplaceActions,
                                        myExtraReplaceActions,
                                        myReplaceFieldActions,
                                        myReplaceAction,
                                        myCloseAction,
                                        myDataProvider);
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
}
