// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.find;

import com.intellij.BundleBase;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.find.editorHeaderActions.*;
import com.intellij.find.impl.HelpID;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.DefaultCustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author max, andrey.zaytsev
 */
public class EditorSearchSession implements SearchSession,
                                            DataProvider,
                                            SelectionListener,
                                            SearchResults.SearchResultsListener,
                                            SearchReplaceComponent.Listener {
  public static final DataKey<EditorSearchSession> SESSION_KEY = DataKey.create("EditorSearchSession");
  public static final Logger SELECTION_UPDATE_LOGGER = Logger.getInstance("com.intellij.find.selection");

  private final Editor myEditor;
  private final LivePreviewController myLivePreviewController;
  private final SearchResults mySearchResults;
  @NotNull
  private final FindModel myFindModel;
  private final SearchReplaceComponent myComponent;
  private RangeMarker myStartSessionSelectionMarker;
  private RangeMarker myStartSessionCaretMarker;
  private String myStartSelectedText;
  private boolean mySelectionUpdatedFromSearchResults;

  private final ActionLink myClickToHighlightLabel = new ActionLink(FindBundle.message("link.click.to.highlight"), e -> {
    setMatchesLimit(Integer.MAX_VALUE);
    updateResults(true);
  });
  private final Disposable myDisposable = Disposer.newDisposable(EditorSearchSession.class.getName());

  public EditorSearchSession(@NotNull Editor editor, Project project) {
    this(editor, project, createDefaultFindModel(project, editor));
  }

  public EditorSearchSession(@NotNull final Editor editor, Project project, @NotNull FindModel findModel) {
    assert !editor.isDisposed();

    myClickToHighlightLabel.setVisible(false);

    myFindModel = findModel;

    myEditor = editor;
    saveInitialSelection();

    mySearchResults = new SearchResults(myEditor, project);
    myLivePreviewController = new LivePreviewController(mySearchResults, this, myDisposable);

    myComponent = SearchReplaceComponent
      .buildFor(project, myEditor.getContentComponent())
      .addPrimarySearchActions(createPrimarySearchActions())
      .addExtraSearchActions(new ToggleMatchCase(),
                             new ToggleWholeWordsOnlyAction(),
                             new ToggleRegex(),
                             new DefaultCustomComponentAction(() -> myClickToHighlightLabel))
      .addSearchFieldActions(new RestorePreviousSettingsAction())
      .addPrimaryReplaceActions(new ReplaceAction(),
                                new ReplaceAllAction(),
                                new ExcludeAction())
      .addExtraReplaceAction(new TogglePreserveCaseAction())
      .addReplaceFieldActions(new PrevOccurrenceAction(false),
                              new NextOccurrenceAction(false))
      .withDataProvider(this)
      .withCloseAction(this::close)
      .withReplaceAction(this::replaceCurrent)
      .build();

    myComponent.addListener(this);
    new UiNotifyConnector(myComponent, new Activatable() {
      @Override
      public void showNotify() {
        initLivePreview();
      }

      @Override
      public void hideNotify() {
        myLivePreviewController.off();
        mySearchResults.removeListener(EditorSearchSession.this);
      }
    });

    new SwitchToFind(getComponent());
    new SwitchToReplace(getComponent());

    myFindModel.addObserver(new FindModel.FindModelObserver() {
      boolean myReentrantLock = false;
      boolean myIsGlobal = myFindModel.isGlobal();
      boolean myIsReplace = myFindModel.isReplaceState();
      @Override
      public void findModelChanged(FindModel findModel1) {
        if (myReentrantLock) return;
        try {
          myReentrantLock = true;
          String stringToFind = myFindModel.getStringToFind();
          if (!wholeWordsApplicable(stringToFind)) {
            myFindModel.setWholeWordsOnly(false);
          }
          if (myIsGlobal != myFindModel.isGlobal() || myIsReplace != myFindModel.isReplaceState()) {
            if (myFindModel.getStringToFind().isEmpty() && myFindModel.isGlobal()) {
              myFindModel.setStringToFind(StringUtil.notNullize(myEditor.getSelectionModel().getSelectedText()));
            }
            if (!myFindModel.isGlobal()) {
              if (myFindModel.getStringToFind().equals(myStartSelectedText)) {
                myFindModel.setStringToFind("");
              } else {
                restoreInitialCaretPositionAndSelection();
              }
            }
            myIsGlobal = myFindModel.isGlobal();
            myIsReplace = myFindModel.isReplaceState();
          }
          EditorSearchSession.this.updateUIWithFindModel();
          mySearchResults.clear();
          EditorSearchSession.this.updateResults(true);
          FindUtil.updateFindInFileModel(EditorSearchSession.this.getProject(), myFindModel, !ConsoleViewUtil.isConsoleViewEditor(editor));
        } finally {
          myReentrantLock = false;
        }
      }
    });

    updateUIWithFindModel();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      initLivePreview();
    }
    updateMultiLineStateIfNeeded();

    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == myEditor) {
          Disposer.dispose(myDisposable);
          myLivePreviewController.dispose();
          myStartSessionSelectionMarker.dispose();
          myStartSessionCaretMarker.dispose();
        }
      }
    }, myDisposable);

    myEditor.getSelectionModel().addSelectionListener(this, myDisposable);

    FindUsagesCollector.triggerUsedOptionsStats(project, FindUsagesCollector.FIND_IN_FILE, findModel);
  }

  protected AnAction[] createPrimarySearchActions() {
    if (ExperimentalUI.isNewUI()) {
      return new AnAction[] {
        new StatusTextAction(),
        new PrevOccurrenceAction(),
        new NextOccurrenceAction(),
        createFilterGroup(),
        createMoreGroup()
      };
    }
    else {
      return new AnAction[]{
        new StatusTextAction(),
        new PrevOccurrenceAction(),
        new NextOccurrenceAction(),
        new FindAllAction(),
        new Separator(),
        new AddOccurrenceAction(),
        new RemoveOccurrenceAction(),
        new SelectAllAction(),
        new Separator(),
        new ToggleSelectionOnlyAction(),
        new ShowFilterPopupGroup()
      };
    }
  }

  private static AnAction createFilterGroup() {
    DefaultActionGroup group = new ShowFilterPopupGroup() {
      @Override
      protected boolean enableLiveIndicator(@NotNull FindModel model) {
        return super.enableLiveIndicator(model) || !model.isGlobal();
      }
    };

    group.add(new Separator(ApplicationBundle.message("editorsearch.filter.search.scope")), Constraints.FIRST);
    group.add(new ToggleSelectionOnlyAction(), Constraints.FIRST);
    return group;
  }

  private static AnAction createMoreGroup() {
    DefaultActionGroup group = new DefaultActionGroup(
      new FindAllAction(),
      new Separator(ApplicationBundle.message("editorsearch.more.multiple.cursors")),
      new AddOccurrenceAction(),
      new RemoveOccurrenceAction(),
      new SelectAllAction()
    );

    group.setPopup(true);
    group.getTemplatePresentation().setText(ApplicationBundle.message("editorsearch.more.popup"));
    group.getTemplatePresentation().setIcon(AllIcons.Actions.More);
    group.getTemplatePresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);
    return group;
  }

  private void saveInitialSelection() {
    if (mySelectionUpdatedFromSearchResults) return;
    SelectionModel selectionModel = myEditor.getSelectionModel();
    Document document = myEditor.getDocument();
    myStartSessionSelectionMarker = document.createRangeMarker(
      selectionModel.getSelectionStart(),
      selectionModel.getSelectionEnd()
    );
    myStartSessionCaretMarker = document.createRangeMarker(
      myEditor.getCaretModel().getOffset(),
      myEditor.getCaretModel().getOffset()
    );
    myStartSelectedText = selectionModel.getSelectedText();
  }

  public Editor getEditor() {
    return myEditor;
  }

  @Nullable
  public static EditorSearchSession get(@Nullable Editor editor) {
    JComponent headerComponent = editor != null ? editor.getHeaderComponent() : null;
    SearchReplaceComponent searchReplaceComponent = ObjectUtils.tryCast(headerComponent, SearchReplaceComponent.class);
    return searchReplaceComponent != null ? SESSION_KEY.getData(searchReplaceComponent) : null;
  }

  @NotNull
  public static EditorSearchSession start(@NotNull Editor editor, @Nullable Project project) {
    EditorSearchSession session = new EditorSearchSession(editor, project);
    editor.setHeaderComponent(session.getComponent());
    return session;
  }

  @NotNull
  public static EditorSearchSession start(@NotNull Editor editor, @NotNull FindModel findModel, @Nullable Project project) {
    EditorSearchSession session = new EditorSearchSession(editor, project, findModel);
    editor.setHeaderComponent(session.getComponent());
    return session;
  }

  @NotNull
  @Override
  public SearchReplaceComponent getComponent() {
    return myComponent;
  }

  public Project getProject() {
    return myComponent.getProject();
  }

  @NotNull
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
  public Object getData(@NotNull @NonNls final String dataId) {
    if (SearchSession.KEY.is(dataId)) {
      return this;
    }
    if (SESSION_KEY.is(dataId)) {
      return this;
    }
    if (CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.is(dataId)) {
      return myEditor;
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return myFindModel.isReplaceState() ? HelpID.REPLACE_IN_EDITOR : HelpID.FIND_IN_EDITOR;
    }
    return null;
  }

  @Override
  public void searchResultsUpdated(@NotNull SearchResults sr) {
    if (sr.getFindModel() == null) return;
    if (myComponent.getSearchTextComponent().getText().isEmpty()) {
      updateUIWithEmptyResults();
    } else {
      int matches = sr.getMatchesCount();
      boolean tooManyMatches = matches > mySearchResults.getMatchesLimit();
      String status;
      if (matches == 0 && !sr.getFindModel().isGlobal() && !myEditor.getSelectionModel().hasSelection()) {
        status = ApplicationBundle.message("editorsearch.noselection");
        myComponent.setRegularBackground();
      } else {
        int cursorIndex = sr.getCursorVisualIndex();
        status = tooManyMatches
                 ? ApplicationBundle.message("editorsearch.toomuch", mySearchResults.getMatchesLimit())
                 : cursorIndex != -1
                   ? ApplicationBundle.message("editorsearch.current.cursor.position", cursorIndex, matches)
                   : ApplicationBundle.message("editorsearch.matches", matches);
        if (!tooManyMatches && matches <= 0) {
          myComponent.setNotFoundBackground();
        }
        else {
          myComponent.setRegularBackground();
        }
      }
      myComponent.setStatusText(status);
      myClickToHighlightLabel.setVisible(tooManyMatches);
    }
    myComponent.updateActions();
  }

  @Override
  public void cursorMoved() {
    myComponent.updateActions();
  }

  @Override
  public void searchFieldDocumentChanged() {
    if (myEditor.isDisposed()) return;
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    String text = myComponent.getSearchTextComponent().getText();
    myFindModel.setStringToFind(text);
    updateResults(true);
    updateMultiLineStateIfNeeded();
  }

  private void updateMultiLineStateIfNeeded() {
    myFindModel.setMultiline(myComponent.getSearchTextComponent().getText().contains("\n") ||
                             myComponent.getReplaceTextComponent().getText().contains("\n"));
  }

  @Override
  public void replaceFieldDocumentChanged() {
    setMatchesLimit(LivePreviewController.MATCHES_LIMIT);
    myFindModel.setStringToReplace(myComponent.getReplaceTextComponent().getText());
    updateMultiLineStateIfNeeded();
  }

  @Override
  public void multilineStateChanged() {
    myFindModel.setMultiline(myComponent.isMultiline());
  }

  @Override
  public void toggleSearchReplaceMode() {
    myFindModel.setReplaceState(!myFindModel.isReplaceState());
  }

  @NotNull
  @Override
  public FindModel getFindModel() {
    return myFindModel;
  }

  @Override
  public boolean hasMatches() {
    return mySearchResults.hasMatches();
  }

  @Override
  public boolean isSearchInProgress() {
    return mySearchResults.isUpdating();
  }

  @Override
  public void searchForward() {
    moveCursor(SearchResults.Direction.DOWN);
    addTextToRecent(myComponent.getSearchTextComponent());
  }

  @Override
  public void searchBackward() {
    moveCursor(SearchResults.Direction.UP);
    addTextToRecent(myComponent.getSearchTextComponent());
  }

  public boolean isLast(boolean forward) {
    return myLivePreviewController.isLast(forward ? SearchResults.Direction.DOWN : SearchResults.Direction.UP);
  }

  private void updateUIWithFindModel() {
    myComponent.update(myFindModel.getStringToFind(),
                       myFindModel.getStringToReplace(),
                       myFindModel.isReplaceState(),
                       myFindModel.isMultiline());
    updateEmptyText();
    myLivePreviewController.setTrackingSelection(!myFindModel.isGlobal());
  }

  private void updateEmptyText() {
    if (myComponent.getSearchTextComponent() instanceof ComponentWithEmptyText) {
      ComponentWithEmptyText cweText = (ComponentWithEmptyText)myComponent.getSearchTextComponent();
      String emptyText = StringUtil.capitalize(getEmptyText());
      cweText.getEmptyText().setText(emptyText);
    }

    if (ExperimentalUI.isNewUI() && myFindModel.isReplaceState() && myComponent.getReplaceTextComponent() instanceof ComponentWithEmptyText) {
      ComponentWithEmptyText cweText = (ComponentWithEmptyText)myComponent.getReplaceTextComponent();
      String emptyText = myFindModel.getStringToReplace().isEmpty() ? ApplicationBundle.message("editorsearch.replace.hint") : "";
      cweText.getEmptyText().setText(emptyText);
    }
  }

  private static void checkOption(List<? super String> chosenOptions, boolean state, String key) {
    if (state) chosenOptions.add(StringUtil.toLowerCase(FindBundle.message(key).replace(BundleBase.MNEMONIC_STRING, "")));
  }

  @NotNull
  private @NlsContexts.StatusText String getEmptyText() {
    if (!myFindModel.getStringToFind().isEmpty()) return "";
    if (myFindModel.isGlobal()) {
      SmartList<String> chosenOptions = new SmartList<>();
      checkOption(chosenOptions, myFindModel.isCaseSensitive(), "find.case.sensitive");
      checkOption(chosenOptions, myFindModel.isWholeWordsOnly() && !myFindModel.isRegularExpressions(), "find.whole.words");
      checkOption(chosenOptions, myFindModel.isRegularExpressions(), "find.regex");
      if (chosenOptions.isEmpty()) {
        return ExperimentalUI.isNewUI() ? ApplicationBundle.message("editorsearch.search.hint") : "";
      }
      if (chosenOptions.size() == 1) {
        return FindBundle.message("emptyText.used.option", chosenOptions.get(0));
      }
      return FindBundle.message("emptyText.used.options", chosenOptions.get(0), chosenOptions.get(1));
    }

    String text = getEditor().getSelectionModel().getSelectedText();
    if (text != null && text.contains("\n")) {
      boolean replaceState = myFindModel.isReplaceState();
      AnAction action = ActionManager.getInstance().getAction(
        replaceState ? IdeActions.ACTION_REPLACE : IdeActions.ACTION_TOGGLE_FIND_IN_SELECTION_ONLY);
      Shortcut shortcut = ArrayUtil.getFirstElement(action.getShortcutSet().getShortcuts());
      if (shortcut != null) {
        return ApplicationBundle.message("editorsearch.in.selection.with.hint", KeymapUtil.getShortcutText(shortcut));
      }
    }
    return ApplicationBundle.message("editorsearch.in.selection");
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

  private void replaceCurrent() {
    if (mySearchResults.getCursor() != null) {
      try {
        myLivePreviewController.performReplace();
      }
      catch (FindManager.MalformedReplacementStringException e) {
        Messages.showErrorDialog(myComponent, e.getMessage(), FindBundle.message("find.replace.invalid.replacement.string.title"));
      }
    }
  }

  public void addTextToRecent(JTextComponent textField) {
    myComponent.addTextToRecent(textField);
  }

  @Override
  public void selectionChanged(@NotNull SelectionEvent e) {
    saveInitialSelection();
    updateEmptyText();
  }

  @Override
  public void beforeSelectionUpdate() {
    mySelectionUpdatedFromSearchResults = true;
  }

  @Override
  public void afterSelectionUpdate() {
    mySelectionUpdatedFromSearchResults = false;
  }

  private void moveCursor(SearchResults.Direction direction) {
    myLivePreviewController.moveCursor(direction);
  }

  @Override
  public void close() {
    IdeFocusManager.getInstance(getProject()).requestFocus(myEditor.getContentComponent(), false);

    myLivePreviewController.dispose();
    myEditor.setHeaderComponent(null);
  }

  private void initLivePreview() {
    if (myEditor.isDisposed()) return;

    myLivePreviewController.on();

    myLivePreviewController.setUserActivityDelay(0);
    updateResults(false);
    myLivePreviewController.setUserActivityDelay(LivePreviewController.USER_ACTIVITY_TRIGGERING_DELAY);

    mySearchResults.addListener(this);
  }

  private void updateResults(final boolean allowedToChangedEditorSelection) {
    final String text = myFindModel.getStringToFind();
    if (text.isEmpty()) {
      nothingToSearchFor(allowedToChangedEditorSelection);
    }
    else {

      if (myFindModel.isRegularExpressions()) {
        try {
          Pattern.compile(text);
        }
        catch (PatternSyntaxException e) {
          myComponent.setNotFoundBackground();
          myClickToHighlightLabel.setVisible(false);
          mySearchResults.clear();
          myComponent.setStatusText(FindBundle.message(INCORRECT_REGEXP_MESSAGE_KEY));
          return;
        }
        if (text.matches("\\|+")) {
          nothingToSearchFor(allowedToChangedEditorSelection);
          myComponent.setStatusText(ApplicationBundle.message("editorsearch.empty.string.matches"));
          return;
        }
      }


      final FindManager findManager = FindManager.getInstance(getProject());
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

  private void nothingToSearchFor(boolean allowedToChangedEditorSelection) {
    updateUIWithEmptyResults();
    mySearchResults.clear();
    if (allowedToChangedEditorSelection
        && !ClientProperty.isTrue(myComponent.getSearchTextComponent(), SearchTextArea.JUST_CLEARED_KEY)) {
      restoreInitialCaretPositionAndSelection();
    }
  }

  private void restoreInitialCaretPositionAndSelection() {
    int originalSelectionStart = Math.min(myStartSessionSelectionMarker.getStartOffset(), myEditor.getDocument().getTextLength());
    int originalSelectionEnd = Math.min(myStartSessionSelectionMarker.getEndOffset(), myEditor.getDocument().getTextLength());
    try {
      if (myFindModel.isGlobal()) {
        mySelectionUpdatedFromSearchResults = true;
      }
      myEditor.getSelectionModel().setSelection(originalSelectionStart, originalSelectionEnd);
    } finally {
      mySelectionUpdatedFromSearchResults = false;
    }
    myEditor.getCaretModel().moveToOffset(Math.min(myStartSessionCaretMarker.getEndOffset(), myEditor.getDocument().getTextLength()));
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    logSelectionUpdate();
  }

  public static void logSelectionUpdate() {
    if (SELECTION_UPDATE_LOGGER.isDebugEnabled()) {
      SELECTION_UPDATE_LOGGER.debug(new Throwable());
    }
  }

  private void updateUIWithEmptyResults() {
    myComponent.setRegularBackground();
    myComponent.setStatusText(ApplicationBundle.message("editorsearch.matches", 0));
    myClickToHighlightLabel.setVisible(false);
  }

  public String getTextInField() {
    return myComponent.getSearchTextComponent().getText();
  }

  public void setTextInField(final String text) {
    myComponent.getSearchTextComponent().setText(text);
    myFindModel.setStringToFind(text);
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
    myComponent.resetUndoRedoActions();
  }


  private abstract static class ButtonAction extends DumbAwareAction implements CustomComponentAction, ActionListener {
    private final @NlsActions.ActionText String myTitle;
    private final char myMnemonic;

    ButtonAction(@NotNull @NlsActions.ActionText String title, char mnemonic) {
      myTitle = title;
      myMnemonic = mnemonic;
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      JButton button = new JButton(myTitle);
      button.setFocusable(false);
      if (!UISettings.getInstance().getDisableMnemonicsInControls()) {
        button.setMnemonic(myMnemonic);
      }
      button.setBorder(new DarculaButtonPainter() {
        @Override
        public Insets getBorderInsets(Component c) {
          return JBUI.insets(1);
        }
      });
      button.addActionListener(this);
      button.setContentAreaFilled(!ExperimentalUI.isNewUI());
      return button;
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
      JButton button = (JButton)e.getPresentation().getClientProperty(COMPONENT_KEY);
      if (button != null) {
        update(button);
      }
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      onClick();
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
      onClick();
    }

    protected abstract void update(@NotNull JButton button);

    protected abstract void onClick();
  }

  private class ReplaceAction extends ButtonAction implements LightEditCompatible {
    ReplaceAction() {
      super(ApplicationBundle.message("editorsearch.replace.action.text"), 'p');
    }

    @Override
    protected void update(@NotNull JButton button) {
      button.setEnabled(mySearchResults.hasMatches());
    }

    @Override
    protected void onClick() {
      replaceCurrent();
    }
  }

  private class ReplaceAllAction extends ButtonAction implements LightEditCompatible {
    ReplaceAllAction() {
      super(ApplicationBundle.message("editorsearch.replace.all.action.text"), 'a');
    }

    @Override
    protected void update(@NotNull JButton button) {
      button.setEnabled(mySearchResults.hasMatches());
    }

    @Override
    protected void onClick() {
      myLivePreviewController.performReplaceAll();
    }
  }

  private class ExcludeAction extends ButtonAction implements LightEditCompatible {
    ExcludeAction() {
      super("", 'l');
    }

    @Override
    protected void update(@NotNull JButton button) {
      FindResult cursor = mySearchResults.getCursor();
      button.setEnabled(cursor != null);
      button.setText(cursor != null && mySearchResults.isExcluded(cursor) ? FindBundle.message("button.include")
                                                                          : FindBundle.message("button.exclude"));
    }

    @Override
    protected void onClick() {
      myLivePreviewController.exclude();
      moveCursor(SearchResults.Direction.DOWN);
    }
  }
}