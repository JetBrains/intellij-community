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

package com.intellij.find;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.editorHeaderActions.*;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.InplaceActionButtonLook;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * @author max, andrey.zaytsev
 */
public class EditorSearchComponent extends EditorHeaderComponent implements DataProvider, SelectionListener, SearchResults.SearchResultsListener {

  private JLabel myMatchInfoLabel;
  private LinkLabel myClickToHighlightLabel;
  private final Project myProject;
  private ActionToolbar myActionsToolbar;
  private JLabel myStateLabel;

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  private final Editor myEditor;

  public JTextComponent getSearchField() {
    return mySearchField;
  }

  private final JBSplitter mySplitPane = new JBSplitter(false);
  private final JPanel myLeftComponent = new JPanel(new BorderLayout());
  private final JPanel myRightComponent = new JPanel(new BorderLayout());

  {
    mySplitPane.setBorder(IdeBorderFactory.createEmptyBorder(1, 0, 2, 0));
    mySplitPane.setHonorComponentsMinimumSize(true);
    mySplitPane.setProportion(0.25f);
    mySplitPane.setAndLoadSplitterProportionKey("FindSplitterProportion");
    mySplitPane.setOpaque(false);
    mySplitPane.getDivider().setOpaque(false);
    myLeftComponent.setOpaque(false);
    myRightComponent.setOpaque(false);

    mySplitPane.setFirstComponent(myLeftComponent);
    mySplitPane.setSecondComponent(myRightComponent);
    add(mySplitPane, BorderLayout.NORTH);
  }

  private JTextComponent mySearchField;
  private JComponent mySearchRootComponent;

  public JTextComponent getReplaceField() {
    return myReplaceField;
  }

  private JTextComponent myReplaceField;
  private JComponent myReplaceRootComponent;

  private final Getter<JTextComponent> mySearchFieldGetter = new Getter<JTextComponent>() {
    @Override
    public JTextComponent get() {
      return mySearchField;
    }
  };

  private final Getter<JTextComponent> myReplaceFieldGetter = new Getter<JTextComponent>() {
    @Override
    public JTextComponent get() {
      return myReplaceField;
    }
  };

  private final Color myDefaultBackground;

  private JButton myReplaceButton;
  private JButton myReplaceAllButton;
  private JButton myExcludeButton;

  private static final Color FOCUS_CATCHER_COLOR = new Color(0x9999ff);

  private JComponent myToolbarComponent;

  private final LivePreviewController myLivePreviewController;
  private final SearchResults mySearchResults;

  private final FindModel myFindModel;
  private JPanel myReplacementPane;

  public JComponent getToolbarComponent() {
    return myToolbarComponent;
  }

  private void updateReplaceButton() {
    if (myReplaceButton != null) {
      myReplaceButton.setEnabled(canReplaceCurrent());
    }
  }

  public void restoreFindModel() {
    final FindModel model = FindManager.getInstance(myProject).getPreviousFindModel();
    if (model != null) {
      myFindModel.copyFrom(model);
      updateUIWithFindModel();
    }
  }

  private static FindModel createDefaultFindModel(Project p, Editor e) {
    FindModel findModel = new FindModel();
    findModel.copyFrom(FindManager.getInstance(p).getFindInFileModel());
    if (e.getSelectionModel().hasSelection()) {
      String selectedText = e.getSelectionModel().getSelectedText();
      if (selectedText != null) {
        findModel.setStringToFind(selectedText);
      }
    }
    findModel.setPromptOnReplace(false);
    return findModel;
  }

  public EditorSearchComponent(@NotNull Editor editor, Project project) {
    this(editor, project, createDefaultFindModel(project, editor));
  }

  @Override
  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY.is(dataId)) {
      return mySearchField.getText();
    }
    if (CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
      return myEditor;
    }
    return null;
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    if (mySearchField.getText().isEmpty()) {
      updateUIWithEmptyResults();
    } else {
      int count = sr.getMatchesCount();
      boolean notTooMuch = count <= mySearchResults.getMatchesLimit();
      myMatchInfoLabel.setText(notTooMuch
                               ? ApplicationBundle.message("editorsearch.matches", count)
                               : ApplicationBundle.message("editorsearch.toomuch", mySearchResults.getMatchesLimit()));
      myClickToHighlightLabel.setVisible(!notTooMuch);
      if (notTooMuch) {
        if (count > 0) {
          setRegularBackground();
        }
        else {
          setNotFoundBackground();
        }
      }
      else {
        setRegularBackground();
      }
    }

    updateExcludeStatus();
  }

  @Override
  public void cursorMoved() {
    updateExcludeStatus();
  }

  @Override
  public void updateFinished() {
  }

  public EditorSearchComponent(@NotNull final Editor editor, final Project project, FindModel findModel) {
    myFindModel = findModel;

    myProject = project;
    myEditor = editor;

    mySearchResults = new SearchResults(myEditor, myProject);
    myLivePreviewController = new LivePreviewController(mySearchResults, this);

    myDefaultBackground = new JTextField().getBackground();

    configureLeadPanel();

    new SwitchToFind(this);
    new SwitchToReplace(this);

    myFindModel.addObserver(new FindModel.FindModelObserver() {
      @Override
      public void findModelChanged(FindModel findModel) {
        String stringToFind = myFindModel.getStringToFind();
        if (!wholeWordsApplicable(stringToFind)) {
          myFindModel.setWholeWordsOnly(false);
        }
        updateUIWithFindModel();
        updateResults(true);
        syncFindModels(FindManager.getInstance(myProject).getFindInFileModel(), myFindModel);
      }
    });

    updateUIWithFindModel();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      initLivePreview();
    }
  }

  private void configureLeadPanel() {
    final int oldCaretPosition = mySearchField != null ? mySearchField.getCaretPosition() : 0;
    JPanel myLeadPanel = new NonOpaquePanel(new BorderLayout());
    myRightComponent.add(myLeadPanel, BorderLayout.WEST);

    Ref<JComponent> ref = Ref.create();
    mySearchField = createTextField(BorderLayout.NORTH, ref, true);
    mySearchRootComponent = ref.get();

    SearchTextField searchTextField = (ref.get() instanceof SearchTextField) ? (SearchTextField)ref.get() : null;
    if (searchTextField != null) {
      setupHistoryToSearchField(searchTextField, FindSettings.getInstance().getRecentFindStrings());
    }

    UIUtil.addUndoRedoActions(mySearchField);

    setupSearchFieldListener();

    if (myActionsToolbar == null) {
      initToolbar();
    }

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.setOpaque(false);
    centerPanel.add(myToolbarComponent, BorderLayout.CENTER);

    myRightComponent.add(centerPanel, BorderLayout.CENTER);

    if (myToolbarComponent instanceof ActionToolbarImpl) {
      new ShowMoreOptions(myToolbarComponent, mySearchField);
    }

    JPanel tailPanel = new NonOpaquePanel(new BorderLayout(5, 0));
    JPanel tailContainer = new NonOpaquePanel(new BorderLayout(5, 0));
    tailContainer.add(tailPanel, BorderLayout.EAST);
    centerPanel.add(tailContainer, BorderLayout.EAST);

    JLabel closeLabel = new JLabel(" ", AllIcons.Actions.Cross, SwingConstants.RIGHT);
    closeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");

    tailPanel.add(closeLabel, BorderLayout.EAST);

    Utils.setSmallerFont(mySearchField);
    mySearchField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (StringUtil.isEmpty(mySearchField.getText())) {
          close();
        }
        else {
          requestFocus(myEditor.getContentComponent());
          addTextToRecent(EditorSearchComponent.this.mySearchField);
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK),
                                         JComponent.WHEN_FOCUSED);
    if (mySearchField instanceof JTextField) {
      mySearchField.registerKeyboardAction(new ActionListener() {
                                             @Override
                                             public void actionPerformed(final ActionEvent e) {
                                               myFindModel.setMultiline(true);
                                             }
                                           }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), JComponent.WHEN_FOCUSED);

    }

    final String initialText = myFindModel.getStringToFind();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        setInitialText(initialText);
        mySearchField.setCaretPosition(oldCaretPosition);
      }
    });

    new RestorePreviousSettingsAction(this, mySearchField);

    new VariantsCompletionAction(this, mySearchFieldGetter); // It registers a shortcut set automatically on construction
    Utils.setSmallerFontForChildren(myToolbarComponent);
  }

  private void setupHistoryToSearchField(SearchTextField field, String[] strings) {
    field.setHistorySize(20);
    field.setHistory(ContainerUtil.reverse(Arrays.asList(strings)));
  }

  private void initToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup("search bar", false);
    actionGroup.add(new PrevOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new NextOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new AddOccurrenceAction(this));
    actionGroup.add(new RemoveOccurrenceAction(this));
    actionGroup.add(new SelectAllAction(this));
    actionGroup.add(new FindAllAction(this));
    actionGroup.add(new ToggleMultiline(this));

    myMatchInfoLabel = new JLabel() {
      @Override
      public Font getFont() {
        Font font = super.getFont();
        return font != null ? font.deriveFont(Font.BOLD) : null;
      }

    };
    myStateLabel = new JLabel("", SwingConstants.RIGHT);


    myClickToHighlightLabel = new LinkLabel("Click to highlight", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        setMatchesLimit(Integer.MAX_VALUE);
        updateResults(true);
      }
    });
    myClickToHighlightLabel.setVisible(false);

    myActionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true);
    myActionsToolbar.setSecondaryActionsTooltip("More Options(" + ShowMoreOptions.SHORT_CUT + ")");

    actionGroup.addAction(new ToggleMatchCase(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleRegex(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleWholeWordsOnlyAction(this)).setAsSecondary(true);

    actionGroup.addAction(new Separator()).setAsSecondary(true);

    actionGroup.addAction(new ToggleInCommentsAction(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleInLiteralsOnlyAction(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleExceptCommentsAction(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleExceptLiteralsAction(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleExceptCommentsAndLiteralsAction(this)).setAsSecondary(true);

    actionGroup.addAction(new Separator()).setAsSecondary(true);

    actionGroup.addAction(new TogglePreserveCaseAction(this)).setAsSecondary(true);
    actionGroup.addAction(new ToggleSelectionOnlyAction(this)).setAsSecondary(true);

    class MyCustomComponentDoNothingAction extends AnAction implements CustomComponentAction {
      private final JComponent c;

      MyCustomComponentDoNothingAction(JComponent c) {
        this.c = c;
        c.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(0, 10, 0, 0)));
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
      }

      @Override
      public JComponent createCustomComponent(Presentation presentation) {
        return c;
      }
    }

    actionGroup.add(new MyCustomComponentDoNothingAction(myMatchInfoLabel));
    actionGroup.add(new MyCustomComponentDoNothingAction(myClickToHighlightLabel));
    actionGroup.add(new MyCustomComponentDoNothingAction(myStateLabel));

    myActionsToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    myToolbarComponent = myActionsToolbar.getComponent();
    myToolbarComponent.setBorder(null);
    myToolbarComponent.setOpaque(false);
  }

  public void selectAllText() {
    mySearchField.selectAll();
  }

  private void setupSearchFieldListener() {
    mySearchField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(javax.swing.event.DocumentEvent documentEvent) {
        searchFieldDocumentChanged();
      }

      @Override
      public void removeUpdate(javax.swing.event.DocumentEvent documentEvent) {
        searchFieldDocumentChanged();
      }

      @Override
      public void changedUpdate(javax.swing.event.DocumentEvent documentEvent) {
        searchFieldDocumentChanged();
      }
    });
  }

  private void searchFieldDocumentChanged() {
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    String text = mySearchField.getText();
    myFindModel.setStringToFind(text);
    if (!StringUtil.isEmpty(text)) {
      updateResults(true);
    }
    else {
      nothingToSearchFor();
    }
    if (mySearchField instanceof JTextArea) {
      adjustRows((JTextArea)mySearchField);
    }
  }

  private static void adjustRows(JTextArea area) {
    area.setRows(Math.max(2, Math.min(3, StringUtil.countChars(area.getText(),'\n')+1)));
  }


  public boolean isRegexp() {
    return myFindModel.isRegularExpressions();
  }

  public void setRegexp(boolean val) {
    myFindModel.setRegularExpressions(val);
  }

  public FindModel getFindModel() {
    return myFindModel;
  }

  private static void syncFindModels(FindModel to, FindModel from) {
    to.setCaseSensitive(from.isCaseSensitive());
    to.setWholeWordsOnly(from.isWholeWordsOnly());
    to.setRegularExpressions(from.isRegularExpressions());
    to.setSearchContext(from.getSearchContext());
    if (from.isReplaceState()) {
      to.setPreserveCase(from.isPreserveCase());
    }
  }

  private void updateUIWithFindModel() {
    boolean needToResetFocus = false;
    myActionsToolbar.updateActionsImmediately();

    if ((myFindModel.isMultiline() && mySearchField instanceof JTextField) || (!myFindModel.isMultiline() && mySearchField instanceof JTextArea)) {
      needToResetFocus = mySearchField.hasFocus();
      myLeftComponent.removeAll();
      myRightComponent.removeAll();
      myReplaceRootComponent = null;
      mySearchRootComponent = null;
      configureLeadPanel();
      if (myReplacementPane != null) {
        myReplacementPane = null;
      }
    }

    String stringToFind = myFindModel.getStringToFind();

    if (!StringUtil.equals(stringToFind, mySearchField.getText())) {
      mySearchField.setText(stringToFind);
    }

    myLivePreviewController.setTrackingSelection(!myFindModel.isGlobal());
    myStateLabel.setText(myFindModel.getStateDescription());

    if (myFindModel.isReplaceState() && myReplacementPane == null) {
      configureReplacementPane();
    } else if (!myFindModel.isReplaceState() && myReplacementPane != null) {

      if (myReplaceRootComponent != null) {
        myLeftComponent.remove(myReplaceRootComponent);
        myReplaceRootComponent = null;
        myReplaceField = null;
      }

      myRightComponent.remove(myReplacementPane);
      myReplacementPane = null;
    }
    if (myFindModel.isReplaceState()) {
      String stringToReplace = myFindModel.getStringToReplace();
      if (!StringUtil.equals(stringToReplace, myReplaceField.getText())) {
        myReplaceField.setText(stringToReplace);
      }
      updateExcludeStatus();
    }

    updateReplaceButton();
    Utils.setSmallerFontForChildren(myToolbarComponent);
    revalidate();
    if (needToResetFocus) mySearchField.requestFocusInWindow();
  }

  private static boolean wholeWordsApplicable(String stringToFind) {
    return !stringToFind.startsWith(" ") &&
           !stringToFind.startsWith("\t") &&
           !stringToFind.endsWith(" ") &&
           !stringToFind.endsWith("\t");
  }

  private void setMatchesLimit(int value) {
    mySearchResults.setMatchesLimit(value);
  }

  private void configureReplacementPane() {
    myReplacementPane = new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

    Ref<JComponent> ref = Ref.create();
    myReplaceField = createTextField(BorderLayout.SOUTH, ref, false);
    myReplaceRootComponent = ref.get();

    SearchTextField searchTextField = ref.get() instanceof SearchTextField ? (SearchTextField)ref.get() : null;
    if (searchTextField != null) {
      setupHistoryToSearchField(searchTextField, FindSettings.getInstance().getRecentReplaceStrings());
    }
    UIUtil.addUndoRedoActions(myReplaceField);

    revalidate();

    DocumentListener replaceFieldListener = new DocumentListener() {
      @Override
      public void insertUpdate(javax.swing.event.DocumentEvent documentEvent) {
        replaceFieldDocumentChanged();
      }

      @Override
      public void removeUpdate(javax.swing.event.DocumentEvent documentEvent) {
        replaceFieldDocumentChanged();
      }

      @Override
      public void changedUpdate(javax.swing.event.DocumentEvent documentEvent) {
        replaceFieldDocumentChanged();
      }
    };
    myReplaceField.getDocument().addDocumentListener(replaceFieldListener);

    if (!getFindModel().isMultiline()) {
      new ReplaceOnEnterAction(this, myReplaceField);
    }

    myReplaceField.setText(myFindModel.getStringToReplace());
    myRightComponent.add(myReplacementPane, BorderLayout.SOUTH);

    myReplaceButton = new JButton("Replace");
    myReplaceButton.setFocusable(false);
    myReplaceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        replaceCurrent();
      }
    });

    myReplaceAllButton = new JButton("Replace all");
    myReplaceAllButton.setFocusable(false);
    myReplaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplaceAll();
      }
    });

    myExcludeButton = new JButton("");
    myExcludeButton.setFocusable(false);
    myExcludeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.exclude();
        moveCursor(SearchResults.Direction.DOWN);
      }
    });

    if (!UISettings.getInstance().DISABLE_MNEMONICS_IN_CONTROLS) {
      myReplaceButton.setMnemonic('p');
      myReplaceAllButton.setMnemonic('a');
      myExcludeButton.setMnemonic('l');
    }

    myReplacementPane.add(myReplaceButton);
    myReplacementPane.add(myReplaceAllButton);
    myReplacementPane.add(myExcludeButton);

    setSmallerFontAndOpaque(myReplaceButton);
    setSmallerFontAndOpaque(myReplaceAllButton);
    setSmallerFontAndOpaque(myExcludeButton);

    Utils.setSmallerFont(myReplaceField);
    new VariantsCompletionAction(this, myReplaceFieldGetter);
    new NextOccurrenceAction(this, myReplaceFieldGetter);
    new PrevOccurrenceAction(this, myReplaceFieldGetter);
  }

  private void replaceFieldDocumentChanged() {
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    myFindModel.setStringToReplace(myReplaceField.getText());
    if (myReplaceField instanceof JTextArea) {
        adjustRows((JTextArea)myReplaceField);
    }
  }

  private boolean canReplaceCurrent() {
    return myLivePreviewController != null && myLivePreviewController.canReplace();
  }

  public void replaceCurrent() {
    if (mySearchResults.getCursor() != null) {
      myLivePreviewController.performReplace();
    }
  }

  private void updateExcludeStatus() {
    if (myExcludeButton != null && mySearchResults != null) {
      FindResult cursor = mySearchResults.getCursor();
      myExcludeButton.setText(cursor == null || !mySearchResults.isExcluded(cursor) ? "Exclude" : "Include");
      myReplaceAllButton.setEnabled(mySearchResults.hasMatches());
      myExcludeButton.setEnabled(cursor != null);
      updateReplaceButton();
    }
  }


  public void showHistory(final boolean byClickingToolbarButton, JTextComponent textField) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
    FindSettings settings = FindSettings.getInstance();
    String[] recent = textField == mySearchField ?  settings.getRecentFindStrings() : settings.getRecentReplaceStrings();
    final boolean toShowAd = textField == mySearchField && textField.getText().isEmpty() && FindManager.getInstance(myProject).getPreviousFindModel() != null;
    Utils.showCompletionPopup(byClickingToolbarButton ? myToolbarComponent : null,
                              new JBList((Object[])ArrayUtil.reverseArray(recent)),
                              "Recent " + (textField == mySearchField ? "Searches" : "Replaces"),
                              textField,
                              toShowAd ? RestorePreviousSettingsAction.getAd() : null);
  }

  private JTextComponent createTextField(Object constraint, Ref<JComponent> componentRef, boolean search) {
    final JTextComponent editorTextField;
    if (myFindModel.isMultiline()) {
      editorTextField = new JTextArea();
      componentRef.set(editorTextField);
      ((JTextArea)editorTextField).setColumns(25);
      ((JTextArea)editorTextField).setRows(3);
      final SearchWrapper wrapper = new SearchWrapper(editorTextField, new ShowHistoryAction(search? mySearchFieldGetter : myReplaceFieldGetter, this));
      myLeftComponent.add(wrapper, constraint);
    }
    else {
      SearchTextField stf = new SearchTextField(true);
      componentRef.set(stf);
      stf.setOpaque(false);
      editorTextField = stf.getTextEditor();
      if (UIUtil.isUnderGTKLookAndFeel()) {
        editorTextField.setOpaque(false);
      }
      myLeftComponent.add(stf, constraint);
    }
    editorTextField.setMinimumSize(new Dimension(200, -1));
    editorTextField.putClientProperty("AuxEditorComponent", Boolean.TRUE);

    editorTextField.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(final FocusEvent e) {
        editorTextField.repaint();
      }

      @Override
      public void focusLost(final FocusEvent e) {
        editorTextField.repaint();
      }
    });
    new CloseOnESCAction(this, editorTextField);
    return editorTextField;
  }


  public void setInitialText(final String initialText) {
    boolean wasEmpty = getTextInField().isEmpty();
    final String text = initialText != null ? initialText : "";
    if (text.contains("\n")) {
      myFindModel.setMultiline(true);
    }
    setTextInField(text);
    if (wasEmpty) {
      mySearchField.selectAll();
    }
  }

  private void requestFocus(Component c) {
    IdeFocusManager.getInstance(myProject).requestFocus(c, true);
  }

  public void searchBackward() {
    moveCursor(SearchResults.Direction.UP);
    addTextToRecent(mySearchField);
  }

  public void searchForward() {
    moveCursor(SearchResults.Direction.DOWN);
    addTextToRecent(mySearchField);
  }

  public void addTextToRecent(JTextComponent textField) {
    final String text = textField.getText();
    if (text.length() > 0) {
      if (textField == mySearchField) {
        FindSettings.getInstance().addStringToFind(text);
        if (mySearchRootComponent instanceof SearchTextField) {
          ((SearchTextField)mySearchRootComponent).addCurrentTextToHistory();
        }
      } else {
        FindSettings.getInstance().addStringToReplace(text);
        if (myReplaceRootComponent instanceof SearchTextField) {
          ((SearchTextField)myReplaceRootComponent).addCurrentTextToHistory();
        }
      }
    }
  }

  @Override
  public void selectionChanged(SelectionEvent e) {
    updateResults(false);
  }

  private void moveCursor(SearchResults.Direction direction) {
    myLivePreviewController.moveCursor(direction);
  }

  private static void setSmallerFontAndOpaque(final JComponent component) {
    Utils.setSmallerFont(component);
    component.setOpaque(false);
  }

  @Override
  public void requestFocus() {
    mySearchField.setSelectionStart(0);
    mySearchField.setSelectionEnd(mySearchField.getText().length());
    requestFocus(mySearchField);
  }

  public void close() {
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), false);

    myLivePreviewController.dispose();
    myEditor.setHeaderComponent(null);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    initLivePreview();
  }

  private void initLivePreview() {
    myLivePreviewController.on();

    myLivePreviewController.setUserActivityDelay(0);
    updateResults(false);
    myLivePreviewController.setUserActivityDelay(LivePreviewController.USER_ACTIVITY_TRIGGERING_DELAY);

    mySearchResults.addListener(this);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    myLivePreviewController.off();
    mySearchResults.removeListener(this);

    addTextToRecent(mySearchField);
    if (myReplaceField != null) {
      addTextToRecent(myReplaceField);
    }
  }

  private void updateResults(final boolean allowedToChangedEditorSelection) {
    final String text = myFindModel.getStringToFind();
    if (text.length() == 0) {
      nothingToSearchFor();
    }
    else {

      if (myFindModel.isRegularExpressions()) {
        try {
          Pattern.compile(text);
        }
        catch (Exception e) {
          setNotFoundBackground();
          myClickToHighlightLabel.setVisible(false);
          mySearchResults.clear();
          myMatchInfoLabel.setText("Incorrect regular expression");
          return;
        }
      }


      final FindManager findManager = FindManager.getInstance(myProject);
      if (allowedToChangedEditorSelection) {
        findManager.setFindWasPerformed();
        FindModel copy = new FindModel();
        copy.copyFrom(myFindModel);
        copy.setReplaceState(false);
        findManager.setFindNextModel(copy);
      }
      if (myLivePreviewController != null) {
        myLivePreviewController.updateInBackground(myFindModel, allowedToChangedEditorSelection);
      }
    }
  }

  private void nothingToSearchFor() {
    updateUIWithEmptyResults();
    if (mySearchResults != null) {
      mySearchResults.clear();
    }
  }

  private void updateUIWithEmptyResults() {
    setRegularBackground();
    myMatchInfoLabel.setText("");
    myClickToHighlightLabel.setVisible(false);
  }

  private void setRegularBackground() {
    mySearchField.setBackground(myDefaultBackground);
  }

  private void setNotFoundBackground() {
    mySearchField.setBackground(LightColors.RED);
  }

  public String getTextInField() {
    return mySearchField.getText();
  }

  public void setTextInField(final String text) {
    mySearchField.setText(text);
    myFindModel.setStringToFind(text);
  }

  public boolean hasMatches() {
    return mySearchResults != null && mySearchResults.hasMatches();
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

  public void selectAllOccurrences() {
    FindUtil.selectSearchResultsInEditor(myEditor, mySearchResults.getOccurrences().iterator(), -1);
  }

  public void removeOccurrence() {
    mySearchResults.prevOccurrence(true);
  }

  public void addNextOccurrence() {
    mySearchResults.nextOccurrence(true);
  }

  public void clearUndoInTextFields() {
    UIUtil.resetUndoRedoActions(mySearchField);
    UIUtil.resetUndoRedoActions(myReplaceField);
  }

  private static class SearchWrapper extends NonOpaquePanel implements PropertyChangeListener, FocusListener{
    private final JComponent myToWrap;

    public SearchWrapper(JComponent toWrap, AnAction showHistoryAction) {
      myToWrap = toWrap;
      setBorder(JBUI.Borders.empty(6, 6, 6, 8));
      setLayout(new BorderLayout(JBUI.scale(4), 0));
      myToWrap.addPropertyChangeListener("background", this);
      myToWrap.addFocusListener(this);
      myToWrap.setBorder(null);
      myToWrap.setOpaque(false);
      JBScrollPane scrollPane = new JBScrollPane(myToWrap,
                                                 ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                 ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.getVerticalScrollBar().setBackground(UIUtil.TRANSPARENT_COLOR);
      scrollPane.getViewport().setBorder(null);
      scrollPane.getViewport().setOpaque(false);
      scrollPane.setBorder(JBUI.Borders.empty(0, 0, 0, 2));
      scrollPane.setOpaque(false);
      ActionButton button =
        new ActionButton(showHistoryAction, showHistoryAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, new Dimension(JBUI.scale(16), JBUI.scale(16)));
      button.setLook(new InplaceActionButtonLook());
      JPanel p = new NonOpaquePanel(new BorderLayout());
      p.add(button, BorderLayout.NORTH);
      add(p, BorderLayout.WEST);
      add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      repaint();
    }

    @Override
    public void focusGained(FocusEvent e) {
      repaint();
    }

    @Override
    public void focusLost(FocusEvent e) {
      repaint();
    }

    @Override
    public void paint(Graphics graphics) {
      Graphics2D g = (Graphics2D)graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        Rectangle r = new Rectangle(getSize());
        r.x += JBUI.scale(4);
        r.y += JBUI.scale(3);
        r.width -= JBUI.scale(9);
        r.height -= JBUI.scale(6);
        int arcSize = Math.min(25, r.height - 1) - 6;
        g.setColor(myToWrap.getBackground());
        boolean hasFocus = myToWrap.hasFocus();
        g.fillRoundRect(r.x, r.y + 1, r.width, r.height - 2, arcSize, arcSize);
        g.setColor(myToWrap.isEnabled() ? Gray._100 : Gray._83);

        if (hasFocus) {
          DarculaUIUtil.paintSearchFocusRing(g, r, arcSize + 6);
        }
        else {
          g.drawRoundRect(r.x, r.y, r.width, r.height - 1, arcSize, arcSize);
        }
      }
      finally {
        g.dispose();
      }
      super.paint(graphics);
    }
  }
}
