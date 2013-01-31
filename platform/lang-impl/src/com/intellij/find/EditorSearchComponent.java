/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.find.impl.livePreview.LiveOccurrence;
import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewControllerBase;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LightColors;
import com.intellij.ui.TextComponentUndoProvider;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;

/**
 * @author max, andrey.zaytsev
 */
public class EditorSearchComponent extends EditorHeaderComponent implements DataProvider, SelectionListener, SearchResults.SearchResultsListener,
                                                             LivePreviewControllerBase.ReplaceListener {
  private static final int MATCHES_LIMIT = 10000;

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

  private JTextComponent mySearchField;
  private JTextComponent myReplaceField;
  private TextComponentUndoProvider mySearchUndo;
  private TextComponentUndoProvider myReplaceUndo;

  private Getter<JTextComponent> mySearchFieldGetter = new Getter<JTextComponent>() {
    @Override
    public JTextComponent get() {
      return mySearchField;
    }
  };

  private Getter<JTextComponent> myReplaceFieldGetter = new Getter<JTextComponent>() {
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
  private DocumentAdapter myDocumentListener;

  private MyLivePreviewController myLivePreviewController;
  private LivePreview myLivePreview;
  private boolean mySuppressUpdate = false;
  private boolean myListeningSelection = false;
  private SearchResults mySearchResults;

  private final FindModel myFindModel;
  private JPanel myReplacementPane;

  public JComponent getToolbarComponent() {
    return myToolbarComponent;
  }

  @Override
  public void replacePerformed(LiveOccurrence occurrence, String replacement, Editor editor) {  }

  @Override
  public void replaceAllPerformed(Editor e) {  }

  private void updateReplaceButton() {
    if (myReplaceButton != null) {
      myReplaceButton.setEnabled(mySearchResults != null && mySearchResults.getCursor() != null &&
                                 !myLivePreviewController.isReplaceDenied() && (mySearchResults.getFindModel().isGlobal() ||
                                                                                !mySearchResults.getEditor().getSelectionModel()
                                                                                  .hasBlockSelection()));
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
    if (PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
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
          myMatchInfoLabel.setText("No matches");
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
  public void editorChanged(SearchResults sr, Editor oldEditor) {  }

  public EditorSearchComponent(final Editor editor, final Project project, FindModel findModel) {
    myFindModel = findModel;

    myProject = project;
    myEditor = editor;

    mySearchResults = new SearchResults(myEditor, myProject);

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
    add(myLeadPanel, BorderLayout.WEST);

    if (mySearchUndo != null) {
      mySearchUndo.dispose();
    }
    mySearchField = createTextField(myLeadPanel);
    mySearchUndo = new TextComponentUndoProvider(mySearchField);

    setupSearchFieldListener();

    if (myActionsToolbar == null) {
      initToolbar();
    }

    myLeadPanel.add(myToolbarComponent);

    if (secondaryActionsAvailable()) {
      if (myToolbarComponent instanceof ActionToolbarImpl) {
        new ShowMoreOptions(myToolbarComponent, mySearchField);
      }
    }


    JPanel tailPanel = new NonOpaquePanel(new BorderLayout(5, 0));
    JPanel tailContainer = new NonOpaquePanel(new BorderLayout(5, 0));
    tailContainer.add(tailPanel, BorderLayout.EAST);
    add(tailContainer, BorderLayout.CENTER);

    myMatchInfoLabel = new JLabel();
    setSmallerFontAndOpaque(myMatchInfoLabel);


    myClickToHighlightLabel = new LinkLabel("Click to highlight", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        setMatchesLimit(Integer.MAX_VALUE);
        updateResults(true);
      }
    });
    setSmallerFontAndOpaque(myClickToHighlightLabel);
    myClickToHighlightLabel.setVisible(false);

    JLabel closeLabel = new JLabel(" ", AllIcons.Actions.Cross, SwingConstants.RIGHT);
    closeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");

    JPanel labelsPanel = new NonOpaquePanel(new FlowLayout());

    labelsPanel.add(myMatchInfoLabel);
    labelsPanel.add(myClickToHighlightLabel);
    tailPanel.add(labelsPanel, BorderLayout.CENTER);
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

  private void initToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup("search bar", false);
    actionGroup.add(new ShowHistoryAction(mySearchFieldGetter, this));
    actionGroup.add(new PrevOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new NextOccurrenceAction(this, mySearchFieldGetter));
    actionGroup.add(new FindAllAction(this));
    actionGroup.add(new ToggleMultiline(this));
    actionGroup.add(new ToggleMatchCase(this));
    actionGroup.add(new ToggleRegex(this));

    myActionsToolbar = ActionManager.getInstance().createActionToolbar("SearchBar", actionGroup, true);
    myActionsToolbar.setSecondaryActionsTooltip("More Options(" + ShowMoreOptions.SHORT_CUT + ")");

    actionGroup.addAction(new ToggleWholeWordsOnlyAction(this));
    if (secondaryActionsAvailable()) {
      actionGroup.addAction(new ToggleInCommentsAction(this)).setAsSecondary(true);
      actionGroup.addAction(new ToggleInLiteralsOnlyAction(this)).setAsSecondary(true);
    }
    actionGroup.addAction(new TogglePreserveCaseAction(this));
    actionGroup.addAction(new ToggleSelectionOnlyAction(this));


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
    setMatchesLimit(MATCHES_LIMIT);
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
      removeAll();
      configureLeadPanel();
      if (myReplacementPane != null) {
        myReplacementPane = null;
      }
    } 

    String stringToFind = myFindModel.getStringToFind();

    if (!StringUtil.equals(stringToFind, mySearchField.getText())) {
      mySearchField.setText(stringToFind);
    }

    setTrackingSelection(!myFindModel.isGlobal());

    if (myFindModel.isReplaceState() && myReplacementPane == null) {
      configureReplacementPane();
    } else if (!myFindModel.isReplaceState() && myReplacementPane != null) {
      remove(myReplacementPane);
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
  }

  private static boolean wholeWordsApplicable(String stringToFind) {
    return !stringToFind.startsWith(" ") &&
           !stringToFind.startsWith("\t") &&
           !stringToFind.endsWith(" ") &&
           !stringToFind.endsWith("\t");
  }

  private void setMatchesLimit(int value) {
    if (mySearchResults != null) {
      mySearchResults.setMatchesLimit(value);
    }
  }

  private void configureReplacementPane() {
    myReplacementPane = createLeadPane();

    if (myReplaceUndo != null) {
      myReplaceUndo.dispose();
    }
    myReplaceField = createTextField(myReplacementPane);
    myReplaceUndo = new TextComponentUndoProvider(myReplaceField);

    //if (myToolbarComponent instanceof ActionToolbarImpl) {
    //  new ShowMoreOptions(myToolbarComponent, myReplaceField);
    //}

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
    add(myReplacementPane, BorderLayout.SOUTH);

    myReplaceButton = new JButton("Replace");
    myReplaceButton.setFocusable(false);
    myReplaceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        replaceCurrent();
      }
    });
    myReplaceButton.setMnemonic('p');

    myReplaceAllButton = new JButton("Replace all");
    myReplaceAllButton.setFocusable(false);
    myReplaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplaceAll();
      }
    });
    myReplaceAllButton.setMnemonic('a');

    myExcludeButton = new JButton("");
    myExcludeButton.setFocusable(false);
    myExcludeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.exclude();
        moveCursor(SearchResults.Direction.DOWN);
      }
    });
    myExcludeButton.setMnemonic('l');


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
    setMatchesLimit(MATCHES_LIMIT);
    myFindModel.setStringToReplace(myReplaceField.getText());
  }

  public void replaceCurrent() {
    if (mySearchResults.getCursor() != null) {
      myLivePreviewController.performReplace();
    }
  }

  private void updateExcludeStatus() {
    if (myExcludeButton != null && mySearchResults != null) {
      LiveOccurrence cursor = mySearchResults.getCursor();
      myExcludeButton.setText(cursor == null || !mySearchResults.isExcluded(cursor) ? "Exclude" : "Include");
      myReplaceAllButton.setEnabled(mySearchResults.hasMatches());
      myExcludeButton.setEnabled(cursor != null);
      updateReplaceButton();
    }
  }

  private void setTrackingSelection(boolean b) {
    if (b) {
      if (!myListeningSelection) {
        myEditor.getSelectionModel().addSelectionListener(this);
      }
    } else {
      if (myListeningSelection) {
        myEditor.getSelectionModel().removeSelectionListener(this);
      }
    }
    myListeningSelection = b;
  }

  private static JPanel createLeadPane() {
    return new NonOpaquePanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
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

  private JTextComponent createTextField(JPanel leadPanel) {
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
      leadPanel.add(scrollPane);
    }
    else {
      editorTextField = new JTextField("") {
        @Override
        protected void paintBorder(final Graphics g) {
          super.paintBorder(g);
          paintBorderOfTextField(g);
        }
      };
      ((JTextField)editorTextField).setColumns(25);
      if (UIUtil.isUnderGTKLookAndFeel()) {
        editorTextField.setOpaque(false);
      }
      leadPanel.add(editorTextField);
    }
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

  private void addTextToRecent(JTextComponent textField) {
    final String text = textField.getText();
    if (text.length() > 0) {
      if (textField == mySearchField) {
        FindSettings.getInstance().addStringToFind(text);
      } else {
        FindSettings.getInstance().addStringToReplace(text);
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
    mySearchResults.dispose();
    myLivePreview.cleanUp();
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
    myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        if (!mySuppressUpdate) {
          myLivePreview.inSmartUpdate();
          updateResults(false);
        } else {
          mySuppressUpdate = false;
        }
      }
    };

    myEditor.getDocument().addDocumentListener(myDocumentListener);


    setMatchesLimit(MATCHES_LIMIT);

    updateResults(false);

    myLivePreview = new LivePreview(mySearchResults);

    myLivePreviewController = new MyLivePreviewController();
    myLivePreviewController.setReplaceListener(this);
    mySearchResults.addListener(this);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (myDocumentListener != null) {
      myEditor.getDocument().removeDocumentListener(myDocumentListener);
      myDocumentListener = null;
    }
    myLivePreview.cleanUp();
    myLivePreview.dispose();
    setTrackingSelection(false);
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
    myMatchInfoLabel.setFont(myMatchInfoLabel.getFont().deriveFont(Font.BOLD));
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
    return myLivePreview != null && myLivePreview.hasMatches();
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

  private class MyLivePreviewController extends LivePreviewControllerBase {
    public MyLivePreviewController() {
      super(EditorSearchComponent.this.mySearchResults, EditorSearchComponent.this.myLivePreview);
    }

    @Override
    public void getFocusBack() {
      if (myFindModel != null && myFindModel.isReplaceState()) {
        requestFocus(myReplaceField);
      } else {
        requestFocus(mySearchField);
      }
    }

    public void performReplace() {
      mySuppressUpdate = true;
      String replacement = getStringToReplace(myEditor, mySearchResults.getCursor());
      final TextRange textRange = performReplace(mySearchResults.getCursor(), replacement, myEditor);
      if (textRange == null) {
        mySuppressUpdate = false;
      }
      //getFocusBack();
      addTextToRecent(myReplaceField) ;
    }

    public void exclude() {
      mySearchResults.exclude(mySearchResults.getCursor());
    }

    public void performReplaceAll() {
      performReplaceAll(myEditor);
    }
  }
}
