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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DefaultCustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightColors;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ArrayUtil;
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
import java.util.regex.Pattern;

/**
 * @author max, andrey.zaytsev
 */
public class EditorSearchComponent extends EditorHeaderComponent implements DataProvider, SelectionListener, SearchResults.SearchResultsListener {

  private final Project myProject;

  private JPanel myLeftPanel;
  private JTextComponent mySearchTextComponent;
  private Wrapper mySearchFieldWrapper;

  private JTextComponent myReplaceTextComponent;
  private Wrapper myReplaceFieldWrapper;


  private JPanel myRightPanel;
  private ActionToolbarImpl mySearchActionsToolbar1;
  private ActionToolbarImpl mySearchActionsToolbar2;
  private JLabel myMatchInfoLabel;
  private LinkLabel<Object> myClickToHighlightLabel;

  private ActionToolbarImpl myReplaceActionsToolbar1;
  private ActionToolbarImpl myReplaceActionsToolbar2;
  private JPanel myReplaceToolbarWrapper;

  @NotNull
  private final Editor myEditor;

  public JTextComponent getSearchTextComponent() {
    return mySearchTextComponent;
  }

  public JTextComponent getReplaceTextComponent() {
    return myReplaceTextComponent;
  }

  private final Color myDefaultBackground;

  private final LivePreviewController myLivePreviewController;
  private final SearchResults mySearchResults;

  private final FindModel myFindModel;

  public EditorSearchComponent(@NotNull Editor editor, Project project) {
    this(editor, project, createDefaultFindModel(project, editor));
  }

  public EditorSearchComponent(@NotNull final Editor editor, final Project project, FindModel findModel) {
    myFindModel = findModel;

    myProject = project;
    myEditor = editor;

    mySearchResults = new SearchResults(myEditor, myProject);
    myLivePreviewController = new LivePreviewController(mySearchResults, this);

    myDefaultBackground = new JTextField().getBackground();
    initUI();

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
    updateMultiLineStateIfNeed();
  }

  private static FindModel createDefaultFindModel(Project project, Editor editor) {
    FindModel findModel = new FindModel();
    findModel.copyFrom(FindManager.getInstance(project).getFindInFileModel());
    if (editor.getSelectionModel().hasSelection()) {
      String selectedText = editor.getSelectionModel().getSelectedText();
      if (selectedText != null) {
        findModel.setStringToFind(selectedText);
      }
    }
    findModel.setPromptOnReplace(false);
    return findModel;
  }


  @Override
  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY.is(dataId)) {
      return mySearchTextComponent.getText();
    }
    if (CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
      return myEditor;
    }
    return null;
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    if (mySearchTextComponent.getText().isEmpty()) {
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
    myReplaceActionsToolbar1.updateActionsImmediately();
  }

  @Override
  public void cursorMoved() {
    myReplaceActionsToolbar1.updateActionsImmediately();
  }

  @Override
  public void updateFinished() {
  }



  private void initUI() {
    myLeftPanel = new NonOpaquePanel(new BorderLayout());
    myLeftPanel.add(mySearchFieldWrapper = new Wrapper(), BorderLayout.NORTH);
    myLeftPanel.add(myReplaceFieldWrapper = new Wrapper(), BorderLayout.CENTER);

    updateSearchComponent();
    updateReplaceComponent();
    initSearchToolbars();
    initReplaceToolBars();

    Wrapper searchToolbarWrapper1 = new NonOpaquePanel(new BorderLayout());
    searchToolbarWrapper1.add(mySearchActionsToolbar1, BorderLayout.WEST);
    Wrapper searchToolbarWrapper2 = new Wrapper(mySearchActionsToolbar2);
    mySearchActionsToolbar2.setBorder(JBUI.Borders.empty(0, 16, 0, 0));
    JPanel searchPair = new NonOpaquePanel(new BorderLayout()).setVerticalSizeReferent(mySearchFieldWrapper);
    searchPair.add(searchToolbarWrapper1, BorderLayout.WEST);
    searchPair.add(searchToolbarWrapper2, BorderLayout.CENTER);
    JLabel closeLabel = new JLabel(null, AllIcons.Actions.Cross, SwingConstants.RIGHT);
    closeLabel.setBorder(JBUI.Borders.empty(5, 5, 5, 5));
    closeLabel.setVerticalAlignment(SwingConstants.TOP);
    closeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        close();
      }
    });

    closeLabel.setToolTipText("Close search bar (Escape)");
    searchPair.add(new Wrapper.North(closeLabel), BorderLayout.EAST);

    Wrapper replaceToolbarWrapper1 = new Wrapper(myReplaceActionsToolbar1).setVerticalSizeReferent(myReplaceFieldWrapper);
    Wrapper replaceToolbarWrapper2 = new Wrapper(myReplaceActionsToolbar2).setVerticalSizeReferent(myReplaceFieldWrapper);
    myReplaceActionsToolbar2.setBorder(JBUI.Borders.empty(0, 16, 0, 0));

    myReplaceToolbarWrapper = new NonOpaquePanel(new BorderLayout());
    myReplaceToolbarWrapper.add(replaceToolbarWrapper1, BorderLayout.WEST);
    myReplaceToolbarWrapper.add(replaceToolbarWrapper2, BorderLayout.CENTER);

    searchToolbarWrapper1.setHorizontalSizeReferent(replaceToolbarWrapper1);

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
  }

  private void updateSearchComponent() {
    final int oldCaretPosition = mySearchTextComponent != null ? mySearchTextComponent.getCaretPosition() : 0;
    String oldText = mySearchTextComponent != null ? mySearchTextComponent.getText() : myFindModel.getStringToFind();

    if (!updateTextComponent(true)) {
      return;
    }

    if (oldText != null) {
      mySearchTextComponent.setText(oldText);
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
                                               requestFocus(myEditor.getContentComponent());
                                               addTextToRecent(EditorSearchComponent.this.mySearchTextComponent);
                                             }
                                           }
                                         }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK),
                                                 JComponent.WHEN_FOCUSED);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        mySearchTextComponent.setCaretPosition(oldCaretPosition);
      }
    });

    new RestorePreviousSettingsAction(this, mySearchTextComponent);
    new VariantsCompletionAction(mySearchTextComponent); // It registers a shortcut set automatically on construction
  }

  private void searchFieldDocumentChanged() {
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    String text = mySearchTextComponent.getText();
    myFindModel.setStringToFind(text);
    if (!StringUtil.isEmpty(text)) {
      updateResults(true);
    }
    else {
      nothingToSearchFor();
    }
    if (mySearchTextComponent instanceof JTextArea) {
      adjustRows((JTextArea)mySearchTextComponent);
    }
    updateMultiLineStateIfNeed();
  }

  private void updateMultiLineStateIfNeed() {
    myFindModel.setMultiline(mySearchTextComponent.getText().contains("\n") || myReplaceTextComponent.getText().contains("\n"));
  }


  private void updateReplaceComponent() {
    final int oldCaretPosition = myReplaceTextComponent != null ? myReplaceTextComponent.getCaretPosition() : 0;
    String oldText = myReplaceTextComponent != null ? myReplaceTextComponent.getText() : myFindModel.getStringToReplace();

    if (!updateTextComponent(false)) {
      return;
    }
    if (oldText != null) {
      myReplaceTextComponent.setText(oldText);
    }

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

    if (!getFindModel().isMultiline()) {
      new ReplaceOnEnterAction(this, myReplaceTextComponent);
    }

    //myReplaceTextComponent.setText(myFindModel.getStringToReplace());
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myReplaceTextComponent.setCaretPosition(oldCaretPosition);
      }
    });

    new VariantsCompletionAction(myReplaceTextComponent);
    new NextOccurrenceAction(this, myReplaceFieldWrapper);
    new PrevOccurrenceAction(this, myReplaceFieldWrapper);
    myReplaceFieldWrapper.revalidate();
    myReplaceFieldWrapper.repaint();
  }

  private void replaceFieldDocumentChanged() {
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    myFindModel.setStringToReplace(myReplaceTextComponent.getText());
    if (myReplaceTextComponent instanceof JTextArea) {
      adjustRows((JTextArea)myReplaceTextComponent);
    }
    updateMultiLineStateIfNeed();
  }

  private static void setupHistoryToSearchField(SearchTextField field, String[] strings) {
    field.setHistorySize(20);
    field.setHistory(ContainerUtil.reverse(Arrays.asList(strings)));
  }

  private void initSearchToolbars() {
    DefaultActionGroup actionGroup1 = new DefaultActionGroup("search bar 1", false);
    mySearchActionsToolbar1 = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup1, true);
    mySearchActionsToolbar1.setForceMinimumSize(true);
    mySearchActionsToolbar1.setReservePlaceAutoPopupIcon(false);
    mySearchActionsToolbar1.setSecondaryButtonPopupStateModifier(new ActionToolbarImpl.PopupStateModifier() {
      @Override
      public int getModifiedPopupState() {
        return ActionButtonComponent.PUSHED;
      }

      @Override
      public boolean willModify() {
        return myFindModel.getSearchContext() != FindModel.SearchContext.ANY;
      }
    });
    mySearchActionsToolbar1.setSecondaryActionsTooltip("More Options(" + ShowMoreOptions.SHORT_CUT + ")");


    actionGroup1.add(new PrevOccurrenceAction(this, mySearchFieldWrapper));
    actionGroup1.add(new NextOccurrenceAction(this, mySearchFieldWrapper));
    actionGroup1.add(new FindAllAction(this));
    actionGroup1.addSeparator();
    actionGroup1.add(new AddOccurrenceAction(this));
    actionGroup1.add(new RemoveOccurrenceAction(this));
    actionGroup1.add(new SelectAllAction(this));
    //actionGroup1.addSeparator();
    //actionGroup1.add(new ToggleMultiline(this));//todo get rid of it!
    actionGroup1.addSeparator();

    actionGroup1.addAction(new ToggleInCommentsAction(this)).setAsSecondary(true);
    actionGroup1.addAction(new ToggleInLiteralsOnlyAction(this)).setAsSecondary(true);
    actionGroup1.addAction(new ToggleExceptCommentsAction(this)).setAsSecondary(true);
    actionGroup1.addAction(new ToggleExceptLiteralsAction(this)).setAsSecondary(true);
    actionGroup1.addAction(new ToggleExceptCommentsAndLiteralsAction(this)).setAsSecondary(true);

    DefaultActionGroup actionGroup2 = new DefaultActionGroup("search bar 2", false);
    mySearchActionsToolbar2 = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup2, true);
    actionGroup2.add(new ToggleMatchCase(this));
    actionGroup2.add(new ToggleRegex(this));
    actionGroup2.add(new ToggleWholeWordsOnlyAction(this));

    myMatchInfoLabel = new JLabel() {
      @Override
      public Font getFont() {
        Font font = super.getFont();
        return font != null ? font.deriveFont(Font.BOLD) : null;
      }

    };
    myMatchInfoLabel.setBorder(JBUI.Borders.empty(2, 20, 0, 20));

    myClickToHighlightLabel = new LinkLabel<Object>("Click to highlight", null, new LinkListener<Object>() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        setMatchesLimit(Integer.MAX_VALUE);
        updateResults(true);
      }
    });
    myClickToHighlightLabel.setVisible(false);

    mySearchActionsToolbar2 = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup2, true);
    actionGroup2.add(new DefaultCustomComponentAction(myMatchInfoLabel));
    actionGroup2.add(new DefaultCustomComponentAction(myClickToHighlightLabel));

    mySearchActionsToolbar1.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    mySearchActionsToolbar2.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    mySearchActionsToolbar1.setBorder(null);
    mySearchActionsToolbar2.setBorder(null);
    mySearchActionsToolbar1.setOpaque(false);
    mySearchActionsToolbar2.setOpaque(false);

    new ShowMoreOptions(mySearchActionsToolbar1, mySearchFieldWrapper);
    Utils.setSmallerFontForChildren(mySearchActionsToolbar1);
    Utils.setSmallerFontForChildren(mySearchActionsToolbar2);
  }

  private void initReplaceToolBars() {
    DefaultActionGroup actionGroup1 = new DefaultActionGroup("replace bar 1", false);
    myReplaceActionsToolbar1 = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup1, true);
    myReplaceActionsToolbar1.setForceMinimumSize(true);
    myReplaceActionsToolbar1.setReservePlaceAutoPopupIcon(false);
    final JButton myReplaceButton = new JButton("Replace");
    myReplaceButton.setFocusable(false);
    myReplaceButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        replaceCurrent();
      }
    });

    final JButton myReplaceAllButton = new JButton("Replace all");
    myReplaceAllButton.setFocusable(false);
    myReplaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myLivePreviewController.performReplaceAll();
      }
    });

    final JButton myExcludeButton = new JButton("");
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

    actionGroup1.addAction(new DefaultCustomComponentAction(myReplaceButton){
      @Override
      public void update(AnActionEvent e) {
        myReplaceButton.setEnabled(canReplaceCurrent());
      }
    });
    actionGroup1.addAction(new DefaultCustomComponentAction(myReplaceAllButton) {
      @Override
      public void update(AnActionEvent e) {
        myReplaceAllButton.setEnabled(mySearchResults != null && mySearchResults.hasMatches());
      }
    });
    actionGroup1.addAction(new DefaultCustomComponentAction(myExcludeButton) {
      @Override
      public void update(AnActionEvent e) {
        FindResult cursor = mySearchResults != null ? mySearchResults.getCursor() : null;
        myExcludeButton.setEnabled(cursor != null);
        myExcludeButton.setText(cursor != null && mySearchResults.isExcluded(cursor) ? "Include" : "Exclude");
      }
    });

    myReplaceActionsToolbar1.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    myReplaceActionsToolbar1.setBorder(null);
    myReplaceActionsToolbar1.setOpaque(false);
    DefaultActionGroup actionGroup2 = new DefaultActionGroup("replace bar 2", false);
    myReplaceActionsToolbar2 = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup2, true);
    actionGroup2.addAction(new TogglePreserveCaseAction(this));
    actionGroup2.addAction(new ToggleSelectionOnlyAction(this));
    myReplaceActionsToolbar2.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    myReplaceActionsToolbar2.setBorder(null);
    myReplaceActionsToolbar2.setOpaque(false);
    Utils.setSmallerFontForChildren(myReplaceActionsToolbar1);
    Utils.setSmallerFontForChildren(myReplaceActionsToolbar2);
  }

  private static void adjustRows(JTextArea area) {
    area.setRows(Math.max(2, Math.min(3, StringUtil.countChars(area.getText(),'\n')+1)));
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
    boolean needToResetSearchFocus = mySearchTextComponent.hasFocus();
    boolean needToResetReplaceFocus = myReplaceTextComponent.hasFocus();
    updateSearchComponent();
    updateReplaceComponent();
    if (myFindModel.isReplaceState()) {
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
    mySearchActionsToolbar1.updateActionsImmediately();
    mySearchActionsToolbar2.updateActionsImmediately();
    myReplaceActionsToolbar1.updateActionsImmediately();
    myReplaceActionsToolbar2.updateActionsImmediately();
    myReplaceToolbarWrapper.revalidate();
    revalidate();
    repaint();

    myLivePreviewController.setTrackingSelection(!myFindModel.isGlobal());

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

  private boolean canReplaceCurrent() {
    return myLivePreviewController != null && myLivePreviewController.canReplace();
  }

  public void replaceCurrent() {
    if (mySearchResults.getCursor() != null) {
      myLivePreviewController.performReplace();
    }
  }

  public void showHistory(final boolean byClickingToolbarButton, JTextComponent textField) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
    FindSettings settings = FindSettings.getInstance();
    String[] recent = textField == mySearchTextComponent ? settings.getRecentFindStrings() : settings.getRecentReplaceStrings();
    final boolean toShowAd = textField == mySearchTextComponent && textField.getText().isEmpty() && FindManager.getInstance(myProject).getPreviousFindModel() != null;
    Utils.showCompletionPopup(byClickingToolbarButton ? mySearchActionsToolbar1 : null,
                              new JBList((Object[])ArrayUtil.reverseArray(recent)),
                              "Recent " + (textField == mySearchTextComponent ? "Searches" : "Replaces"),
                              textField,
                              toShowAd ? RestorePreviousSettingsAction.getAd() : null);
  }

  private boolean updateTextComponent(final boolean search) {
    JTextComponent oldComponent = search ? mySearchTextComponent : myReplaceTextComponent;
    Color oldBackground = oldComponent != null ? oldComponent.getBackground() : null;
    Wrapper wrapper = search ? mySearchFieldWrapper : myReplaceFieldWrapper;
    boolean multiline = myFindModel.isMultiline();
    if (multiline && oldComponent instanceof JTextArea) return false;
    if (!multiline && oldComponent instanceof JTextField) return false;

    final JTextComponent textComponent;
    if (multiline) {
      textComponent = new JTextArea();
      ((JTextArea)textComponent).setColumns(25);
      ((JTextArea)textComponent).setRows(2);
      wrapper.setContent(new SearchWrapper(textComponent, new ShowHistoryAction(textComponent, this)));
    }
    else {
      SearchTextField searchTextField = new SearchTextField(true);
      searchTextField.setOpaque(false);
      textComponent = searchTextField.getTextEditor();
      searchTextField.getTextEditor().setColumns(25);
      if (UIUtil.isUnderGTKLookAndFeel()) {
        textComponent.setOpaque(false);
      }
      setupHistoryToSearchField(searchTextField, search
                                                 ? FindSettings.getInstance().getRecentFindStrings()
                                                 : FindSettings.getInstance().getRecentReplaceStrings());
      textComponent.registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          final String text = textComponent.getText();
          myFindModel.setMultiline(true);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (search) {
                mySearchTextComponent.setText(text+"\n");
              } else {
                myReplaceTextComponent.setText(text+"\n");
              }
            }
          });
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), JComponent.WHEN_FOCUSED);
      wrapper.setContent(searchTextField);
    }

    if (search) {
      mySearchTextComponent = textComponent;
    } else {
      myReplaceTextComponent = textComponent;
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
    new CloseOnESCAction(this, textComponent);
    return true;
  }

  private void requestFocus(Component c) {
    IdeFocusManager.getInstance(myProject).requestFocus(c, true);
  }

  public void searchBackward() {
    moveCursor(SearchResults.Direction.UP);
    addTextToRecent(mySearchTextComponent);
  }

  public void searchForward() {
    moveCursor(SearchResults.Direction.DOWN);
    addTextToRecent(mySearchTextComponent);
  }

  public void addTextToRecent(JTextComponent textField) {
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

  @Override
  public void selectionChanged(SelectionEvent e) {
    updateResults(false);
  }

  private void moveCursor(SearchResults.Direction direction) {
    myLivePreviewController.moveCursor(direction);
  }

  @Override
  public void requestFocus() {
    mySearchTextComponent.setSelectionStart(0);
    mySearchTextComponent.setSelectionEnd(mySearchTextComponent.getText().length());
    requestFocus(mySearchTextComponent);
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

    addTextToRecent(mySearchTextComponent);
    if (myReplaceTextComponent != null) {
      addTextToRecent(myReplaceTextComponent);
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
    mySearchTextComponent.setBackground(myDefaultBackground);
  }

  private void setNotFoundBackground() {
    mySearchTextComponent.setBackground(LightColors.RED);
  }

  public String getTextInField() {
    return mySearchTextComponent.getText();
  }

  public void setTextInField(final String text) {
    mySearchTextComponent.setText(text);
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
    UIUtil.resetUndoRedoActions(mySearchTextComponent);
    UIUtil.resetUndoRedoActions(myReplaceTextComponent);
  }
}
