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

package com.intellij.find;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.editorHeaderActions.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.impl.livePreview.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
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


  public Editor getEditor() {
    return myEditor;
  }

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

  private MyUndoProvider mySearchUndo;
  private MyUndoProvider myReplaceUndo;

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

  public static final Color COMPLETION_BACKGROUND_COLOR = new Color(235, 244, 254);
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

  public EditorSearchComponent(Editor editor, Project project) {
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
      if (count <= mySearchResults.getMatchesLimit()) {
        myClickToHighlightLabel.setVisible(false);

        if (count > 0) {
          setRegularBackground();
          if (count > 1) {
            myMatchInfoLabel.setText(count + " matches");
          }
          else {
            myMatchInfoLabel.setText("1 match");
          }
        }
        else {
          setNotFoundBackground();
          myMatchInfoLabel.setText("No matches ");
          boldMatchInfo();
        }
      }
      else {
        setRegularBackground();
        myMatchInfoLabel.setText("More than " + mySearchResults.getMatchesLimit() + " matches");
        myClickToHighlightLabel.setVisible(true);
        boldMatchInfo();
      }
    }

    updateExcludeStatus();
  }

  @Override
  public void cursorMoved(boolean toChangeSelection) {
    updateExcludeStatus();
  }

  @Override
  public void updateFinished() {
  }

  @Override
  public void editorChanged(SearchResults sr, Editor oldEditor) {  }

  public EditorSearchComponent(final Editor editor, final Project project, FindModel findModel) {
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
    JPanel myLeadPanel = createLeadPane();
    myRightComponent.add(myLeadPanel, BorderLayout.WEST);

    if (mySearchUndo != null) {
      mySearchUndo.dispose();
    }

    Ref<JComponent> ref = Ref.create();
    mySearchField = createTextField(BorderLayout.NORTH, ref);
    mySearchRootComponent = ref.get();

    SearchTextField searchTextField = (ref.get() instanceof SearchTextField) ? (SearchTextField)ref.get() : null;
    if (searchTextField != null) {
      setupHistoryToSearchField(searchTextField, FindSettings.getInstance().getRecentFindStrings());
    }

    mySearchUndo = new MyUndoProvider(mySearchField);

    setupSearchFieldListener();

    if (myActionsToolbar == null) {
      initToolbar();
    }

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.setOpaque(false);
    centerPanel.add(myToolbarComponent, BorderLayout.CENTER);

    myRightComponent.add(centerPanel, BorderLayout.CENTER);

    if (secondaryActionsAvailable()) {
      if (myToolbarComponent instanceof ActionToolbarImpl) {
        new ShowMoreOptions(myToolbarComponent, mySearchField);
      }
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

    final String initialText = myFindModel.getStringToFind();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        setInitialText(initialText);
      }
    });

    new RestorePreviousSettingsAction(this, mySearchField);

    new VariantsCompletionAction(this, mySearchFieldGetter); // It registers a shortcut set automatically on construction
    Utils.setSmallerFontForChildren(myToolbarComponent);
  }

  private void setupHistoryToSearchField(SearchTextField field, String[] strings) {
    field.setHistorySize(strings.length);
    field.setHistory(ContainerUtil.reverse(Arrays.asList(strings)));
  }

  private void initToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup("search bar", false);
    actionGroup.add(new ShowHistoryAction(mySearchFieldGetter, this));
    actionGroup.add(new PrevOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new NextOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new FindAllAction(this));
    actionGroup.add(new ToggleMultiline(this));
    actionGroup.add(new ToggleMatchCase(this));
    actionGroup.add(new ToggleRegex(this));

    myMatchInfoLabel = new JLabel();

    myClickToHighlightLabel = new LinkLabel("Click to highlight", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        setMatchesLimit(Integer.MAX_VALUE);
        updateResults(true);
      }
    });
    myClickToHighlightLabel.setVisible(false);

    myActionsToolbar = ActionManager.getInstance().createActionToolbar("SearchBar", actionGroup, true);
    myActionsToolbar.setSecondaryActionsTooltip("More Options(" + ShowMoreOptions.SHORT_CUT + ")");

    actionGroup.addAction(new ToggleWholeWordsOnlyAction(this));
    if (secondaryActionsAvailable()) {
      actionGroup.addAction(new ToggleInCommentsAction(this)).setAsSecondary(true);
      actionGroup.addAction(new ToggleInLiteralsOnlyAction(this)).setAsSecondary(true);
    }
    actionGroup.addAction(new TogglePreserveCaseAction(this));
    actionGroup.addAction(new ToggleSelectionOnlyAction(this));

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

    myActionsToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    myToolbarComponent = myActionsToolbar.getComponent();
    myToolbarComponent.setBorder(null);
    myToolbarComponent.setOpaque(false);
  }

  private boolean secondaryActionsAvailable() {
    return FindManagerImpl.ourHasSearchInCommentsAndLiterals;
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
    to.setInCommentsOnly(from.isInCommentsOnly());
    to.setInStringLiteralsOnly(from.isInStringLiteralsOnly());
    if (from.isReplaceState()) {
      to.setPreserveCase(from.isPreserveCase());
    }
  }

  private void updateUIWithFindModel() {

    myActionsToolbar.updateActionsImmediately();

    if ((myFindModel.isMultiline() && mySearchField instanceof JTextField) || (!myFindModel.isMultiline() && mySearchField instanceof JTextArea)) {
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

    if (myReplaceUndo != null) {
      myReplaceUndo.dispose();
    }

    Ref<JComponent> ref = Ref.create();
    myReplaceField = createTextField(BorderLayout.SOUTH, ref);
    myReplaceRootComponent = ref.get();

    SearchTextField searchTextField = ref.get() instanceof SearchTextField ? (SearchTextField)ref.get() : null;
    if (searchTextField != null) {
      setupHistoryToSearchField(searchTextField, FindSettings.getInstance().getRecentReplaceStrings());
    }
    myReplaceUndo = new MyUndoProvider(myReplaceField);

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


    ActionGroup actionsGroup = new DefaultActionGroup(new ShowHistoryAction(myReplaceFieldGetter, this));
    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("ReplaceBar", actionsGroup, true);
    tb.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    final JComponent tbComponent = tb.getComponent();
    tbComponent.setOpaque(false);
    tbComponent.setBorder(null);
    myReplacementPane.add(tbComponent);

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

  private static JPanel createLeadPane() {
    return new NonOpaquePanel(new BorderLayout());
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

  private String gerRestoreFindModelAd() {
    return "Use " + KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0) + " to restore your last search/replace settings";
  }

  private void paintBorderOfTextField(Graphics g) {
    if (!(UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderGTKLookAndFeel() || UIUtil.isUnderNimbusLookAndFeel()) &&
        isFocusOwner()) {
      final Rectangle bounds = getBounds();
      g.setColor(FOCUS_CATCHER_COLOR);
      g.drawRect(0, 0, bounds.width - 1, bounds.height - 1);
    }
  }

  private JTextComponent createTextField(Object constraint, Ref<JComponent> componentRef) {
    final JTextComponent editorTextField;
    if (myFindModel.isMultiline()) {
      editorTextField = new JTextArea("") {
        @Override
        protected void paintBorder(final Graphics g) {
          super.paintBorder(g);
          paintBorderOfTextField(g);
        }
      };
      ((JTextArea)editorTextField).setColumns(25);
      ((JTextArea)editorTextField).setRows(3);
      final JScrollPane scrollPane = new JBScrollPane(editorTextField,
                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      myLeftComponent.add(scrollPane, constraint);
      componentRef.set(scrollPane);
    }
    else {
      SearchTextField stf = new SearchTextField(true);
      stf.setOpaque(false);
      editorTextField = stf.getTextEditor();
      if (UIUtil.isUnderGTKLookAndFeel()) {
        editorTextField.setOpaque(false);
      }
      myLeftComponent.add(stf, constraint);
      componentRef.set(stf);
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
    final String text = initialText != null ? initialText : "";
    if (text.contains("\n")) {
      myFindModel.setMultiline(true);
    }
    setTextInField(text);
    mySearchField.selectAll();
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
    if (myEditor.getSelectionModel().hasSelection()) {
      myEditor.getCaretModel().moveToOffset(myEditor.getSelectionModel().getSelectionStart());
      myEditor.getSelectionModel().removeSelection();
    }
    IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), false);

    myLivePreviewController.dispose();

    if (mySearchUndo != null) {
      mySearchUndo.dispose();
    }
    if (myReplaceUndo != null){
      myReplaceUndo.dispose();
    }
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
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.PLAIN));
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
          myMatchInfoLabel.setText("Incorrect regular expression");
          boldMatchInfo();
          myClickToHighlightLabel.setVisible(false);
          mySearchResults.clear();
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

  private void boldMatchInfo() {
    Font font = myMatchInfoLabel.getFont();
    if (!font.isBold()) {
      myMatchInfoLabel.setFont(font.deriveFont(Font.BOLD));
    }
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

  private static class MyUndoProvider extends TextComponentUndoProvider {
    private boolean myEnabled = true;
    public MyUndoProvider(JTextComponent textComponent) {
      super(textComponent);
      textComponent.getDocument().addDocumentListener(new com.intellij.ui.DocumentAdapter() {
        @Override
        protected void textChanged(javax.swing.event.DocumentEvent e) {
          myEnabled = true;
        }
      });
    }

    @Override
    protected boolean canUndo() {
      return super.canUndo() && myEnabled;
    }

    @Override
    protected boolean canRedo() {
      return super.canRedo() && myEnabled;
    }

    public void disable() {
      myEnabled = false;
      myUndoManager.discardAllEdits();
    }
  }

  public void clearUndoInTextFields() {
    myReplaceUndo.disable();
    mySearchUndo.disable();
  }
}
