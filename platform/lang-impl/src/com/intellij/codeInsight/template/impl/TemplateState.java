// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.macro.TemplateCompletionProcessor;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TemplateState extends TemplateStateBase implements Disposable {
  private static final Logger LOG = Logger.getInstance(TemplateState.class);
  private Project myProject;

  private TemplateImpl myPrevTemplate;

  private RangeMarker myTemplateRange;
  private final List<RangeHighlighter> myTabStopHighlighters = new ArrayList<>();
  private int myCurrentVariableNumber = -1;
  private int myCurrentSegmentNumber = -1;
  private boolean ourLookupShown;

  private boolean myDocumentChangesTerminateTemplate = true;
  private boolean myDocumentChanged;

  @Nullable private LookupListener myLookupListener;

  private final List<TemplateEditingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private DocumentListener myEditorDocumentListener;
  private boolean myTemplateIndented;
  @Nullable private PairProcessor<? super String, ? super String> myProcessor;
  private boolean mySelectionCalculated;
  private boolean myStarted;

  TemplateState(@NotNull Project project, @NotNull final Editor editor) {
    super(editor);
    myProject = project;
  }

  private void initListeners() {
    if (isDisposed()) return;
    myEditorDocumentListener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent e) {
        if (CommandProcessor.getInstance().getCurrentCommand() != null && !UndoManager.getInstance(myProject).isUndoOrRedoInProgress()) {
          myDocumentChanged = true;
        }
      }
    };
    myLookupListener = new LookupListener() {
      @Override
      public void itemSelected(@NotNull LookupEvent event) {
        if (isCaretOutsideCurrentSegment(null)) {
          if (isCaretInsideOrBeforeNextVariable()) {
            nextTab();
          }
          else {
            gotoEnd(true);
          }
        }
      }
    };
    LookupManager.getInstance(myProject).addPropertyChangeListener(evt -> {
      if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())) {
        Lookup lookup = (Lookup)evt.getNewValue();
        if (lookup != null) {
          lookup.addLookupListener(myLookupListener);
        }
      }
    }, this);

    if (getEditor() != null) {
      installCaretListener(getEditor());
    }
    getDocument().addDocumentListener(myEditorDocumentListener, this);
    myProject.getMessageBus().connect(this).subscribe(CommandListener.TOPIC, new CommandListener() {
      boolean started;

      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        if (!UndoManager.getInstance(myProject).isUndoOrRedoInProgress()) {
          myDocumentChangesTerminateTemplate = isCaretOutsideCurrentSegment(event.getCommandName());
          started = true;
        }
      }

      @Override
      public void beforeCommandFinished(@NotNull CommandEvent event) {
        if (started && !isDisposed() && !UndoManager.getInstance(myProject).isUndoOrRedoInProgress()) {
          Runnable runnable = () -> afterChangedUpdate();
          final LookupImpl lookup = getEditor() != null ? (LookupImpl)LookupManager.getActiveLookup(getEditor()) : null;
          if (lookup != null) {
            lookup.performGuardedChange(runnable);
          }
          else {
            runnable.run();
          }
        }
      }
    });
  }

  private void installCaretListener(@NotNull Editor editor) {
    CaretListener listener = new CaretListener() {
      @Override
      public void caretAdded(@NotNull CaretEvent e) {
        if (!isInteractiveModeSupported()) {
          finishTemplate(false);
        }
      }

      @Override
      public void caretRemoved(@NotNull CaretEvent e) {
        if (!isInteractiveModeSupported()) {
          finishTemplate(false);
        }
      }
    };

    editor.getCaretModel().addCaretListener(listener, this);
  }

  private boolean isCaretInsideOrBeforeNextVariable() {
    if (getEditor() != null && myCurrentVariableNumber >= 0) {
      int nextVar = getNextVariableNumber(myCurrentVariableNumber);
      TextRange nextVarRange = nextVar < 0 ? null : getVariableRange(getTemplate().getVariableNameAt(nextVar));
      if (nextVarRange == null) return false;

      int caretOffset = getEditor().getCaretModel().getOffset();
      if (nextVarRange.containsOffset(caretOffset)) return true;

      TextRange currentVarRange = getVariableRange(getTemplate().getVariableNameAt(myCurrentVariableNumber));
      return currentVarRange != null && currentVarRange.getEndOffset() < caretOffset && caretOffset < nextVarRange.getStartOffset();
    }
    return false;
  }

  private boolean isCaretOutsideCurrentSegment(String commandName) {
    if (getEditor() != null && myCurrentSegmentNumber >= 0) {
      final int offset = getEditor().getCaretModel().getOffset();
      boolean hasSelection = getEditor().getSelectionModel().hasSelection();

      final int segmentStart = getSegments().getSegmentStart(myCurrentSegmentNumber);
      if (offset < segmentStart ||
          !hasSelection && offset == segmentStart && ActionsBundle.actionText(IdeActions.ACTION_EDITOR_BACKSPACE).equals(commandName)) return true;

      final int segmentEnd = getSegments().getSegmentEnd(myCurrentSegmentNumber);
      if (offset > segmentEnd ||
          !hasSelection && offset == segmentEnd && ActionsBundle.actionText(IdeActions.ACTION_EDITOR_DELETE).equals(commandName)) return true;
    }
    return false;
  }

  private boolean isInteractiveModeSupported() {
    return getEditor() != null && getEditor().getCaretModel().getCaretCount() <= 1 && !(getEditor() instanceof ImaginaryEditor);
  }

  @Override
  public synchronized void dispose() {
    if (myLookupListener != null) {
      final LookupImpl lookup = getEditor() != null ? (LookupImpl)LookupManager.getActiveLookup(getEditor()) : null;
      if (lookup != null) {
        lookup.removeLookupListener(myLookupListener);
      }
      myLookupListener = null;
    }

    myEditorDocumentListener = null;

    myProcessor = null;
    getProperties().clear();
    myListeners.clear();

    //Avoid the leak of the editor
    releaseAll();
    setDocument(null);
  }

  public boolean isToProcessTab() {
    if (isCaretOutsideCurrentSegment(null)) {
      return false;
    }
    if (ourLookupShown) {
      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(getEditor());
      if (lookup != null && !lookup.isFocused()) {
        return true;
      }
    }

    return !ourLookupShown;
  }

  private void setCurrentVariableNumber(int variableNumber) {
    myCurrentVariableNumber = variableNumber;
    final boolean isFinished = isFinished();
    if (getDocument() != null) {
      ((DocumentEx)getDocument()).setStripTrailingSpacesEnabled(isFinished);
    }
    myCurrentSegmentNumber = isFinished ? -1 : getCurrentSegmentNumber();
  }

  @Nullable
  public TextRange getCurrentVariableRange() {
    int number = getCurrentSegmentNumber();
    if (number == -1) return null;
    return new TextRange(getSegments().getSegmentStart(number), getSegments().getSegmentEnd(number));
  }

  @Nullable
  public TextRange getVariableRange(String variableName) {
    int segment = getTemplate().getVariableSegmentNumber(variableName);
    if (segment < 0) return null;

    return new TextRange(getSegments().getSegmentStart(segment), getSegments().getSegmentEnd(segment));
  }

  public int getSegmentsCount() {
    return getSegments().getSegmentsCount();
  }

  public TextRange getSegmentRange(int segment) {
    return new TextRange(getSegments().getSegmentStart(segment), getSegments().getSegmentEnd(segment));
  }

  public boolean isFinished() {
    return myCurrentVariableNumber < 0;
  }

  private void releaseAll() {
    if (getSegments() != null) {
      getSegments().removeAll();
      setSegments(null);
    }
    if (myTemplateRange != null) {
      myTemplateRange.dispose();
      myTemplateRange = null;
    }
    myPrevTemplate = getTemplate();
    setTemplate(null);
    myProject = null;
    releaseEditor();
  }

  private void releaseEditor() {
    if (getEditor() != null) {
      for (RangeHighlighter segmentHighlighter : myTabStopHighlighters) {
        segmentHighlighter.dispose();
      }
      myTabStopHighlighters.clear();
      setEditor(null);
    }
  }

  void start(@NotNull TemplateImpl template,
             @Nullable PairProcessor<? super String, ? super String> processor,
             @Nullable Map<String, String> predefinedVarValues) {
    LOG.assertTrue(!myStarted, "Already started");
    myStarted = true;

    PsiFile file = getPsiFile();
    setTemplate(template);
    myProcessor = processor;

    MyBasicUndoableAction undoableAction = new MyBasicUndoableAction(this, getDocument());
    UndoManager.getInstance(myProject).undoableActionPerformed(undoableAction);
    Disposer.register(this, undoableAction);

    myTemplateIndented = false;
    myCurrentVariableNumber = -1;
    setSegments(new TemplateSegments(getEditor()));
    myPrevTemplate = getTemplate();

    setPredefinedVariableValues(predefinedVarValues);

    if (getTemplate().isInline()) {
      int caretOffset = getEditor().getCaretModel().getOffset();
      myTemplateRange = getDocument().createRangeMarker(caretOffset, caretOffset + getTemplate().getTemplateText().length());
    }
    else {
      preprocessTemplate(file, getEditor().getCaretModel().getOffset(), getTemplate().getTemplateText());
      int caretOffset = getEditor().getCaretModel().getOffset();
      myTemplateRange = getDocument().createRangeMarker(caretOffset, caretOffset);
    }
    myTemplateRange.setGreedyToLeft(true);
    myTemplateRange.setGreedyToRight(true);

    LiveTemplateRunLogger.log(myProject, template, file.getLanguage());

    processAllExpressions(getTemplate());
  }

  private void preprocessTemplate(final PsiFile file, int caretOffset, final String textToInsert) {
    for (TemplatePreprocessor preprocessor : TemplatePreprocessor.EP_NAME.getExtensionList()) {
      preprocessor.preprocessTemplate(getEditor(), file, caretOffset, textToInsert, getTemplate().getTemplateText());
    }
  }

  private void processAllExpressions(@NotNull final TemplateImpl template) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!template.isInline()) getDocument().insertString(myTemplateRange.getStartOffset(), template.getTemplateText());
      for (int i = 0; i < template.getSegmentsCount(); i++) {
        int segmentOffset = myTemplateRange.getStartOffset() + template.getSegmentOffset(i);
        getSegments().addSegment(segmentOffset, segmentOffset);
      }

      LOG.assertTrue(myTemplateRange.isValid(), getRangesDebugInfo());
      calcResults(false);
      LOG.assertTrue(myTemplateRange.isValid(), getRangesDebugInfo());
      calcResults(false);  //Fixed SCR #[vk500] : all variables should be recalced twice on start.
      LOG.assertTrue(myTemplateRange.isValid(), getRangesDebugInfo());
      doReformat();

      int nextVariableNumber = getNextVariableNumber(-1);

      if (nextVariableNumber >= 0) {
        fireWaitingForInput();
      }

      if (nextVariableNumber == -1) {
        finishTemplate(false);
      }
      else {
        setCurrentVariableNumber(nextVariableNumber);
        if (isInteractiveModeSupported()) {
          initTabStopHighlighters();
          initListeners();
        }
        focusCurrentExpression();
        fireCurrentVariableChanged(-1);
        if (!isInteractiveModeSupported()) {
          finishTemplate(false);
        }
      }
    });
  }

  private String getRangesDebugInfo() {
    return myTemplateRange + "\ntemplateKey: " + getTemplate().getKey() + "\ntemplateText: " + getTemplate().getTemplateText() +
           "\ntemplateString: " + getTemplate();
  }

  private void doReformat() {
    final Runnable action = () -> {
      IntArrayList indices = initEmptyVariables();
      getSegments().setSegmentsGreedy(false);
      LOG.assertTrue(myTemplateRange.isValid(),
                     "template key: " + getTemplate().getKey() + "; " +
                     "template text" + getTemplate().getTemplateText() + "; " +
                     "variable number: " + getCurrentVariableNumber());
      reformat();
      getSegments().setSegmentsGreedy(true);
      restoreEmptyVariables(indices);
    };
    ApplicationManager.getApplication().runWriteAction(action);
  }

  public void setSegmentsGreedy(boolean greedy) {
    getSegments().setSegmentsGreedy(greedy);
  }

  public void setTabStopHighlightersGreedy(boolean greedy) {
    for (RangeHighlighter highlighter : myTabStopHighlighters) {
      highlighter.setGreedyToLeft(greedy);
      highlighter.setGreedyToRight(greedy);
    }
  }

  private void shortenReferences() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiFile file = getPsiFile();
      if (file != null) {
        IntArrayList indices = initEmptyVariables();
        getSegments().setSegmentsGreedy(false);
        for (TemplateOptionalProcessor processor : TemplateOptionalProcessor.EP_NAME.getExtensionList()) {
          processor.processText(myProject, getTemplate(), getDocument(), myTemplateRange, getEditor());
        }
        getSegments().setSegmentsGreedy(true);
        restoreEmptyVariables(indices);
      }
    });
  }

  private void afterChangedUpdate() {
    if (isFinished()) return;
    LOG.assertTrue(getTemplate() != null, presentTemplate(myPrevTemplate));
    if (myDocumentChanged) {
      if (myDocumentChangesTerminateTemplate || getSegments().isInvalid()) {
        cancelTemplate();
      }
      else {
        calcResults(true);
      }
      myDocumentChanged = false;
    }
  }

  private static String presentTemplate(@Nullable TemplateImpl template) {
    if (template == null) {
      return "no template";
    }

    String message = StringUtil.notNullize(template.getKey());
    message += "\n\nTemplate#name: " + StringUtil.notNullize(template.toString());
    message += "\n\nTemplate#string: " + StringUtil.notNullize(template.getString());
    message += "\n\nTemplate#text: " + StringUtil.notNullize(template.getTemplateText());
    return message;
  }

  private String getExpressionString(int index) {
    CharSequence text = getDocument().getCharsSequence();

    if (!getSegments().isValid(index)) return "";

    int start = getSegments().getSegmentStart(index);
    int end = getSegments().getSegmentEnd(index);

    return text.subSequence(start, end).toString();
  }

  private int getCurrentSegmentNumber() {
    int varNumber = myCurrentVariableNumber;
    if (varNumber == -1) {
      return -1;
    }
    String variableName = getTemplate().getVariableNameAt(varNumber);
    int segmentNumber = getTemplate().getVariableSegmentNumber(variableName);
    if (segmentNumber < 0) {
      Throwable trace = getTemplate().getBuildingTemplateTrace();
      LOG.error("No segment for variable: var=" + varNumber + "; name=" + variableName + "; " + presentTemplate(getTemplate()) +
                "; offset: " + getEditor().getCaretModel().getOffset(), AttachmentFactory.createAttachment(getDocument()),
                new Attachment("trace.txt", trace != null ? ExceptionUtil.getThrowableText(trace) : "<empty>"));
    }
    return segmentNumber;
  }

  private void focusCurrentExpression() {
    if (isFinished() || isDisposed()) {
      return;
    }

    PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());

    final int currentSegmentNumber = getCurrentSegmentNumber();

    lockSegmentAtTheSameOffsetIfAny();

    if (currentSegmentNumber < 0) return;
    final int start = getSegments().getSegmentStart(currentSegmentNumber);
    final int end = getSegments().getSegmentEnd(currentSegmentNumber);
    if (end >= 0) {
      getEditor().getCaretModel().moveToOffset(end);
      getEditor().getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      getEditor().getSelectionModel().removeSelection();
      getEditor().getSelectionModel().setSelection(start, end);
    }

    DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
      Expression expressionNode = getCurrentExpression();
      List<TemplateExpressionLookupElement> lookupItems = getCurrentExpressionLookupItems();
      final PsiFile psiFile = getPsiFile();
      if (!lookupItems.isEmpty()) {
        if (((TemplateManagerImpl)TemplateManager.getInstance(myProject)).shouldSkipInTests()) {
          insertSingleItem(lookupItems);
        }
        else {
          for (LookupElement lookupItem : lookupItems) {
            assert lookupItem != null : expressionNode;
          }

          AsyncEditorLoader.performWhenLoaded(getEditor(), () ->
            runLookup(lookupItems, expressionNode.getAdvertisingText(), expressionNode.getLookupFocusDegree()));
        }
      }
      else {
        try {
          Result result = expressionNode.calculateResult(getCurrentExpressionContext());
          if (result != null) {
            result.handleFocused(psiFile, getDocument(), getSegments().getSegmentStart(currentSegmentNumber),
                                 getSegments().getSegmentEnd(currentSegmentNumber));
          }
        }
        catch (IndexNotReadyException ignore) {
        }
      }
    });
    focusCurrentHighlighter(true);
  }

  @Nullable
  PsiFile getPsiFile() {
    return !isDisposed() ? PsiDocumentManager.getInstance(myProject).getPsiFile(getDocument()) : null;
  }

  private void insertSingleItem(List<TemplateExpressionLookupElement> lookupItems) {
    TemplateExpressionLookupElement first = lookupItems.get(0);
    EditorModificationUtil.insertStringAtCaret(getEditor(), first.getLookupString());
    first.handleTemplateInsert(lookupItems, Lookup.AUTO_INSERT_SELECT_CHAR);
  }

  @NotNull
  private List<TemplateExpressionLookupElement> getCurrentExpressionLookupItems() {
    LookupElement[] elements = null;
    try {
      elements = getCurrentExpression().calculateLookupItems(getCurrentExpressionContext());
    }
    catch (IndexNotReadyException ignored) { }
    if (elements == null) return Collections.emptyList();

    List<TemplateExpressionLookupElement> result = new ArrayList<>();
    for (int i = 0; i < elements.length; i++) {
      result.add(new TemplateExpressionLookupElement(this, elements[i], i));
    }
    return result;
  }

  public ExpressionContext getCurrentExpressionContext() {
    return createExpressionContext(getSegments().getSegmentStart(getCurrentSegmentNumber()));
  }

  public ExpressionContext getExpressionContextForSegment(int segmentNumber) {
    return createExpressionContext(getSegments().getSegmentStart(segmentNumber));
  }

  @NotNull
  private Expression getCurrentExpression() {
    return getTemplate().getExpressionAt(myCurrentVariableNumber);
  }

  private void runLookup(final List<TemplateExpressionLookupElement> lookupItems,
                         @Nullable String advertisingText,
                         @NotNull LookupFocusDegree lookupFocusDegree) {
    if (isDisposed()) return;

    final LookupManager lookupManager = LookupManager.getInstance(myProject);

    final LookupImpl lookup = (LookupImpl)lookupManager.showLookup(getEditor(), lookupItems.toArray(LookupElement.EMPTY_ARRAY));
    if (lookup == null) return;

    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP && getEditor().getUserData(InplaceRefactoring.INPLACE_RENAMER) == null) {
      lookup.setStartCompletionWhenNothingMatches(true);
    }

    if (advertisingText != null) {
      lookup.addAdvertisement(advertisingText, null);
    }
    else {
      ActionManager am = ActionManager.getInstance();
      String enterShortcut = KeymapUtil.getFirstKeyboardShortcutText(am.getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM));
      String tabShortcut = KeymapUtil.getFirstKeyboardShortcutText(am.getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE));
      lookup.addAdvertisement(LangBundle.message("popup.advertisement.press.or.to.replace", enterShortcut, tabShortcut), null);
    }
    lookup.setLookupFocusDegree(lookupFocusDegree);
    lookup.refreshUi(true, true);
    ourLookupShown = true;
    lookup.addLookupListener(new LookupListener() {
      @Override
      public void lookupCanceled(@NotNull LookupEvent event) {
        lookup.removeLookupListener(this);
        ourLookupShown = false;
      }

      @Override
      public void itemSelected(@NotNull LookupEvent event) {
        lookup.removeLookupListener(this);
        if (isFinished()) return;
        ourLookupShown = false;

        LookupElement item = event.getItem();
        if (item instanceof TemplateExpressionLookupElement) {
          ((TemplateExpressionLookupElement)item).handleTemplateInsert(lookupItems, event.getCompletionChar());
        }
      }
    });
  }

  private void unblockDocument() {
    PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(getDocument());
  }

  // Hours spent fixing code : 3.5
  void calcResults(final boolean isQuick) {
    if (getSegments().isInvalid()) {
      gotoEnd(true);
    }

    if (myProcessor != null && myCurrentVariableNumber >= 0) {
      final String variableName = getTemplate().getVariableNameAt(myCurrentVariableNumber);
      final TextResult value = getVariableValue(variableName);
      if (value != null && !value.getText().isEmpty()) {
        if (!myProcessor.process(variableName, value.getText())) {
          finishTemplate(false); // nextTab(); ?
          return;
        }
      }
    }

    fixOverlappedSegments(myCurrentSegmentNumber);

    WriteCommandAction.runWriteCommandAction(myProject, null, null, () -> {
      if (isDisposed()) {
        return;
      }
      BitSet calcedSegments = new BitSet();
      int maxAttempts = (getTemplate().getVariableCount() + 1) * 3;

      do {
        maxAttempts--;
        calcedSegments.clear();
        for (int i = myCurrentVariableNumber + 1; i < getTemplate().getVariableCount(); i++) {
          String variableName = getTemplate().getVariableNameAt(i);
          final int segmentNumber = getTemplate().getVariableSegmentNumber(variableName);
          if (segmentNumber < 0) continue;
          final Expression expression = getTemplate().getExpressionAt(i);
          final Expression defaultValue = getTemplate().getDefaultValueAt(i);
          String oldValue = getVariableValueText(variableName);
          DumbService.getInstance(myProject).withAlternativeResolveEnabled(
            () -> recalcSegment(segmentNumber, isQuick, expression, defaultValue));
          final TextResult value = getVariableValue(variableName);
          assert value != null : "name=" + variableName + "\ntext=" + getTemplate().getTemplateText();
          String newValue = value.getText();
          if (!newValue.equals(oldValue)) {
            calcedSegments.set(segmentNumber);
          }
        }

        List<TemplateDocumentChange> changes = new ArrayList<>();
        boolean selectionCalculated = false;
        for (int i = 0; i < getTemplate().getSegmentsCount(); i++) {
          if (!calcedSegments.get(i)) {
            String variableName = getTemplate().getSegmentName(i);
            if (variableName.equals(Template.SELECTION)) {
              if (mySelectionCalculated) {
                continue;
              }
              selectionCalculated = true;
            }
            if (Template.END.equals(variableName)) continue; // No need to update end since it can be placed over some other variable
            String newValue = getVariableValueText(variableName);
            int start = getSegments().getSegmentStart(i);
            int end = getSegments().getSegmentEnd(i);
            changes.add(new TemplateDocumentChange(newValue, start, end, i));
          }
        }
        executeChanges(changes);
        if (selectionCalculated) {
          mySelectionCalculated = true;
        }
      }
      while (!calcedSegments.isEmpty() && maxAttempts >= 0);
    });
  }

  private static final class TemplateDocumentChange {
    public final String newValue;
    public final int startOffset;
    public final int endOffset;
    public final int segmentNumber;

    private TemplateDocumentChange(String newValue, int startOffset, int endOffset, int segmentNumber) {
      this.newValue = newValue;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.segmentNumber = segmentNumber;
    }
  }

  private void executeChanges(@NotNull List<TemplateDocumentChange> changes) {
    if (isDisposed() || changes.isEmpty()) {
      return;
    }
    if (changes.size() > 1) {
      ContainerUtil.sort(changes, (o1, o2) -> {
        int startDiff = o2.startOffset - o1.startOffset;
        return startDiff != 0 ? startDiff : o2.segmentNumber - o1.segmentNumber;
      });
    }
    DocumentUtil.executeInBulk(getDocument(), true, () -> {
      for (TemplateDocumentChange change : changes) {
        replaceString(change.newValue, change.startOffset, change.endOffset, change.segmentNumber);
      }
    });
  }

  /**
   * Must be invoked on every segment change in order to avoid overlapping editing segment with its neighbours
   */
  private void fixOverlappedSegments(int currentSegment) {
    if (currentSegment >= 0) {
      int currentSegmentStart = getSegments().getSegmentStart(currentSegment);
      int currentSegmentEnd = getSegments().getSegmentEnd(currentSegment);
      for (int i = 0; i < getSegments().getSegmentsCount(); i++) {
        if (i > currentSegment) {
          final int startOffset = getSegments().getSegmentStart(i);
          if (currentSegmentStart <= startOffset && startOffset < currentSegmentEnd) {
            getSegments().replaceSegmentAt(i, currentSegmentEnd, Math.max(getSegments().getSegmentEnd(i), currentSegmentEnd), true);
          }
        }
        else if (i < currentSegment) {
          final int endOffset = getSegments().getSegmentEnd(i);
          if (currentSegmentStart < endOffset && endOffset <= currentSegmentEnd) {
            getSegments().replaceSegmentAt(i, Math.min(getSegments().getSegmentStart(i), currentSegmentStart), currentSegmentStart, true);
          }
        }
      }
    }
  }

  @NotNull
  private String getVariableValueText(String variableName) {
    TextResult value = getVariableValue(variableName);
    return value != null ? value.getText() : "";
  }

  private void recalcSegment(int segmentNumber, boolean isQuick, Expression expressionNode, Expression defaultValue) {
    if (isDisposed()) return;
    String oldValue = getExpressionString(segmentNumber);
    int start = getSegments().getSegmentStart(segmentNumber);
    int end = getSegments().getSegmentEnd(segmentNumber);
    boolean commitDocument = !isQuick || expressionNode.requiresCommittedPSI();

    if(commitDocument) {
      PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());
    }
    PsiFile psiFile = getPsiFile();
    PsiElement element = psiFile != null ? psiFile.findElementAt(start) : null;
    if (element != null && commitDocument) {
      PsiUtilCore.ensureValid(element);
    }

    ExpressionContext context = createExpressionContext(start);
    Result result = isQuick ? expressionNode.calculateQuickResult(context) : expressionNode.calculateResult(context);
    if (isQuick && result == null) {
      if (!oldValue.isEmpty()) {
        return;
      }
    }

    final boolean resultIsNullOrEmpty = result == null || result.equalsToText("", element);

    // do not update default value of neighbour segment
    if (resultIsNullOrEmpty && myCurrentSegmentNumber >= 0 &&
        (getSegments().getSegmentStart(segmentNumber) == getSegments().getSegmentEnd(myCurrentSegmentNumber) ||
         getSegments().getSegmentEnd(segmentNumber) == getSegments().getSegmentStart(myCurrentSegmentNumber))) {
      return;
    }
    if (defaultValue != null && resultIsNullOrEmpty) {
      result = defaultValue.calculateResult(context);
    }
    if (element != null && commitDocument) {
      PsiUtilCore.ensureValid(element);
    }
    if (result == null || result.equalsToText(oldValue, element)) return;

    replaceString(StringUtil.notNullize(result.toString()), start, end, segmentNumber);

    if (result instanceof RecalculatableResult) {
      IntArrayList indices = initEmptyVariables();
      shortenReferences();
      PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());
      ((RecalculatableResult)result)
        .handleRecalc(psiFile, getDocument(), getSegments().getSegmentStart(segmentNumber), getSegments().getSegmentEnd(segmentNumber));
      restoreEmptyVariables(indices);
    }
  }

  private void replaceString(String newValue, int start, int end, int segmentNumber) {
    TextRange range = TextRange.create(start, end);
    if (!TextRange.from(0, getDocument().getCharsSequence().length()).contains(range)) {
      LOG.error("Diagnostic for EA-54980. Can't extract " + range + " range. " + presentTemplate(getTemplate()),
                AttachmentFactory.createAttachment(getDocument()));
    }
    String oldText = range.subSequence(getDocument().getCharsSequence()).toString();

    if (!oldText.equals(newValue)) {
      getSegments().setNeighboursGreedy(segmentNumber, false);
      getDocument().replaceString(start, end, newValue);
      int newEnd = start + newValue.length();
      getSegments().replaceSegmentAt(segmentNumber, start, newEnd);
      getSegments().setNeighboursGreedy(segmentNumber, true);
      fixOverlappedSegments(segmentNumber);
    }
  }

  public int getCurrentVariableNumber() {
    return myCurrentVariableNumber;
  }

  public void previousTab() {
    if (isFinished()) {
      return;
    }

    myDocumentChangesTerminateTemplate = false;

    final int oldVar = myCurrentVariableNumber;
    int previousVariableNumber = getPreviousVariableNumber(oldVar);
    if (previousVariableNumber >= 0) {
      focusCurrentHighlighter(false);
      calcResults(false);
      doReformat();
      setCurrentVariableNumber(previousVariableNumber);
      focusCurrentExpression();
      fireCurrentVariableChanged(oldVar);
    }
  }

  public void nextTab() {
    if (isFinished()) {
      return;
    }

    //some psi operations may block the document, unblock here
    unblockDocument();

    myDocumentChangesTerminateTemplate = false;

    final int oldVar = myCurrentVariableNumber;
    int nextVariableNumber = getNextVariableNumber(oldVar);
    if (nextVariableNumber == -1) {
      calcResults(false);
      ApplicationManager.getApplication().runWriteAction(() -> reformat());
      finishTemplate(false);
      return;
    }
    focusCurrentHighlighter(false);
    calcResults(false);
    doReformat();
    setCurrentVariableNumber(nextVariableNumber);
    focusCurrentExpression();
    fireCurrentVariableChanged(oldVar);
  }

  public void considerNextTabOnLookupItemSelected(LookupElement item) {
    if (isFinished()) {
      return;
    }
    if (item != null) {
      ExpressionContext context = getCurrentExpressionContext();
      for (TemplateCompletionProcessor processor : TemplateCompletionProcessor.EP_NAME.getExtensionList()) {
        if (!processor.nextTabOnItemSelected(context, item)) {
          return;
        }
      }
    }
    TextRange range = getCurrentVariableRange();
    if (range != null && range.getLength() > 0) {
      int caret = getEditor().getCaretModel().getOffset();
      if (caret == range.getEndOffset() || isCaretInsideOrBeforeNextVariable()) {
        nextTab();
      }
      else if (caret > range.getEndOffset()) {
        gotoEnd(true);
      }
    }
  }

  private void lockSegmentAtTheSameOffsetIfAny() {
    getSegments().lockSegmentAtTheSameOffsetIfAny(getCurrentSegmentNumber());
  }

  private ExpressionContext createExpressionContext(final int start) {
    return new ExpressionContext() {
      @Override
      public Project getProject() {
        return myProject;
      }

      @Override
      public Editor getEditor() {
        return TemplateState.this.getEditor();
      }

      @Override
      public int getStartOffset() {
        return start;
      }

      @Override
      public int getTemplateStartOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getStartOffset();
      }

      @Override
      public int getTemplateEndOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getEndOffset();
      }

      @Override
      public <T> T getProperty(Key<T> key) {
        //noinspection unchecked
        return (T)getProperties().get(key);
      }

      @Nullable
      @Override
      public PsiElement getPsiElementAtStartOffset() {
        Project project = getProject();
        int templateStartOffset = getTemplateStartOffset();
        int offset = templateStartOffset > 0 ? getTemplateStartOffset() - 1 : getTemplateStartOffset();

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        Editor editor = getEditor();
        if (editor == null) {
          return null;
        }
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        return file == null ? null : file.findElementAt(offset);
      }
    };
  }

  public void gotoEnd(boolean brokenOff) {
    if (isDisposed()) return;
    if (!getSegments().isInvalid()) {
      calcResults(false);
    }
    if (!brokenOff) {
      doReformat();
    }
    finishTemplate(brokenOff);
  }

  public void gotoEnd() {
    gotoEnd(true);
  }

  private void finishTemplate(boolean broken) {
    if (isDisposed()) return;
    Editor editor = getEditor();
    LookupManager.getInstance(myProject).hideActiveLookup();
    setFinalEditorState(broken);

    try {
      fireBeforeTemplateFinished(broken);
    }
    finally {
      try {
        cleanupTemplateState();
        TemplateManagerImpl.clearTemplateState(editor);
        fireTemplateFinished(broken);
      }
      finally {
        Disposer.dispose(this);
      }
    }
  }

  private void setFinalEditorState(boolean brokenOff) {
    if (isDisposed()) return;
    getEditor().getSelectionModel().removeSelection();
    if (brokenOff && !((TemplateManagerImpl)TemplateManager.getInstance(myProject)).shouldSkipInTests()) return;

    int endSegmentNumber = getFinalSegmentNumber();
    int offset = -1;
    if (endSegmentNumber >= 0) {
      offset = getSegments().getSegmentStart(endSegmentNumber);
    }
    else {
      if (!getTemplate().isSelectionTemplate() && !getTemplate().isInline()) { //do not move caret to the end of range for selection templates
        offset = myTemplateRange.getEndOffset();
      }
    }

    if (!isInteractiveModeSupported() && getCurrentVariableNumber() > -1) {
      offset = -1; //do not move caret in multicaret mode if at least one tab had been made already
    }

    if (offset >= 0) {
      getEditor().getCaretModel().moveToOffset(offset);
      getEditor().getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    int selStart = getTemplate().getSelectionStartSegmentNumber();
    int selEnd = getTemplate().getSelectionEndSegmentNumber();
    if (selStart >= 0 && selEnd >= 0) {
      getEditor().getSelectionModel().setSelection(getSegments().getSegmentStart(selStart), getSegments().getSegmentStart(selEnd));
    }
  }

  private void cancelTemplate() {
    if (isDisposed()) return;
    try {
      fireTemplateCancelled();
      cleanupTemplateState();
    }
    finally {
      Disposer.dispose(this);
    }
  }

  private void cleanupTemplateState() {
    int oldVar = myCurrentVariableNumber;
    setCurrentVariableNumber(-1);
    fireCurrentVariableChanged(oldVar);
  }

  public boolean isLastVariable() {
    return getNextVariableNumber(getCurrentVariableNumber()) < 0;
  }

  private int getNextVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber + 1; i < getTemplate().getVariableCount(); i++) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private int getPreviousVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber - 1; i >= 0; i--) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private boolean checkIfTabStop(int currentVariableNumber) {
    Expression expression = getTemplate().getExpressionAt(currentVariableNumber);
    if (myCurrentVariableNumber == -1) {
      if (getTemplate().skipOnStart(currentVariableNumber)) return false;
    }
    String variableName = getTemplate().getVariableNameAt(currentVariableNumber);
    if (getPredefinedVariableValues() == null || !getPredefinedVariableValues().containsKey(variableName)) {
      if (getTemplate().isAlwaysStopAt(currentVariableNumber)) {
        return true;
      }
    }
    int segmentNumber = getTemplate().getVariableSegmentNumber(variableName);
    if (segmentNumber < 0) return false;
    int start = getSegments().getSegmentStart(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result = expression.calculateResult(context);
    if (result == null) {
      return true;
    }
    LookupElement[] items = expression.calculateLookupItems(context);
    return items != null && items.length > 1;
  }

  private IntArrayList initEmptyVariables() {
    int endSegmentNumber = getTemplate().getEndSegmentNumber();
    int selStart = getTemplate().getSelectionStartSegmentNumber();
    int selEnd = getTemplate().getSelectionEndSegmentNumber();
    IntArrayList indices = new IntArrayList();
    List<TemplateDocumentChange> changes = new ArrayList<>();
    for (int i = 0; i < getTemplate().getSegmentsCount(); i++) {
      int length = getSegments().getSegmentEnd(i) - getSegments().getSegmentStart(i);
      if (length != 0) continue;
      if (i == endSegmentNumber || i == selStart || i == selEnd) continue;

      String name = getTemplate().getSegmentName(i);
      for (int j = 0; j < getTemplate().getVariableCount(); j++) {
        if (getTemplate().getVariableNameAt(j).equals(name)) {
          Expression e = getTemplate().getExpressionAt(j);
          String marker = "a";
          if (e instanceof MacroCallNode) {
            marker = ((MacroCallNode)e).getMacro().getDefaultValue();
          }
          changes.add(new TemplateDocumentChange(marker, getSegments().getSegmentStart(i), getSegments().getSegmentEnd(i), i));
          indices.add(i);
          break;
        }
      }
    }
    executeChanges(changes);
    return indices;
  }

  private void initTabStopHighlighters() {
    final Set<String> vars = new HashSet<>();
    for (Variable variable : getTemplate().getVariables()) {
      String variableName = variable.getName();
      if (!vars.add(variableName)) continue;
      int segmentNumber = getTemplate().getVariableSegmentNumber(variableName);
      if (segmentNumber < 0) continue;
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(segmentNumber, variable, false, false);
      myTabStopHighlighters.add(segmentHighlighter);
    }

    int endSegmentNumber = getTemplate().getEndSegmentNumber();
    if (endSegmentNumber >= 0) {
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(endSegmentNumber, null, false, true);
      myTabStopHighlighters.add(segmentHighlighter);
    }
  }

  private RangeHighlighter getSegmentHighlighter(int segmentNumber, @Nullable Variable var, boolean isSelected, boolean isEnd) {
    boolean newStyle = Registry.is("live.templates.highlight.all.variables");
    TextAttributesKey attributesKey = isEnd ? null :
                                      isSelected ? EditorColors.LIVE_TEMPLATE_ATTRIBUTES :
                                      newStyle && mightStopAtVariable(var, segmentNumber) ? EditorColors.LIVE_TEMPLATE_INACTIVE_SEGMENT :
                                      null;

    int start = getSegments().getSegmentStart(segmentNumber);
    int end = getSegments().getSegmentEnd(segmentNumber);
    RangeHighlighterEx segmentHighlighter = (RangeHighlighterEx)getEditor().getMarkupModel()
      .addRangeHighlighter(attributesKey, start, end, HighlighterLayer.ELEMENT_UNDER_CARET - 1, HighlighterTargetArea.EXACT_RANGE);
    segmentHighlighter.setGreedyToLeft(true);
    segmentHighlighter.setGreedyToRight(true);

    EditorColorsScheme scheme = getEditor().getColorsScheme();
    TextAttributes attributes = segmentHighlighter.getTextAttributes(scheme);
    if (attributes != null && attributes.getEffectType() == EffectType.BOXED && newStyle) {
      TextAttributes clone = attributes.clone();
      clone.setEffectType(EffectType.SLIGHTLY_WIDER_BOX);
      clone.setBackgroundColor(scheme.getDefaultBackground());
      segmentHighlighter.setTextAttributes(clone);
    }

    return segmentHighlighter;
  }

  private boolean mightStopAtVariable(@Nullable Variable var, int segmentNumber) {
    if (var == null) return false;
    if (var.isAlwaysStopAt()) return true;
    return var.getDefaultValueExpression().calculateQuickResult(getExpressionContextForSegment(segmentNumber)) == null;
  }

  private void focusCurrentHighlighter(boolean toSelect) {
    if (isFinished()) {
      return;
    }
    if (myCurrentVariableNumber >= myTabStopHighlighters.size()) {
      return;
    }
    RangeHighlighter segmentHighlighter = myTabStopHighlighters.get(myCurrentVariableNumber);
    if (segmentHighlighter != null) {
      final int segmentNumber = getCurrentSegmentNumber();
      RangeHighlighter newSegmentHighlighter = getSegmentHighlighter(segmentNumber, getTemplate().getVariables().get(myCurrentVariableNumber), toSelect, false);
      segmentHighlighter.dispose();
      myTabStopHighlighters.set(myCurrentVariableNumber, newSegmentHighlighter);
    }
  }

  private void reformat() {
    final PsiFile file = getPsiFile();
    if (file != null) {
      CodeStyleManager style = CodeStyleManager.getInstance(myProject);
      for (TemplateOptionalProcessor processor : DumbService.getDumbAwareExtensions(myProject, TemplateOptionalProcessor.EP_NAME)) {
        try {
          processor.processText(myProject, getTemplate(), getDocument(), myTemplateRange, getEditor());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(getDocument());
      // for Python, we need to indent the template even if reformatting is enabled, because otherwise indents would be broken
      // and reformat wouldn't be able to fix them
      if (getTemplate().isToIndent()) {
        if (!myTemplateIndented) {
          LOG.assertTrue(myTemplateRange.isValid(), presentTemplate(getTemplate()));
          smartIndent(myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          myTemplateIndented = true;
        }
      }
      if (getTemplate().isToReformat()) {
        try {
          int endSegmentNumber = getFinalSegmentNumber();
          PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());
          RangeMarker dummyAdjustLineMarkerRange = null;
          int endVarOffset = -1;
          if (endSegmentNumber >= 0) {
            endVarOffset = getSegments().getSegmentStart(endSegmentNumber);
            TextRange range = CodeStyleManagerImpl.insertNewLineIndentMarker(file, getDocument(), endVarOffset);
            if (range != null) dummyAdjustLineMarkerRange = getDocument().createRangeMarker(range);
          }
          int reformatStartOffset = myTemplateRange.getStartOffset();
          int reformatEndOffset = myTemplateRange.getEndOffset();
          if (dummyAdjustLineMarkerRange == null && endVarOffset >= 0) {
            // There is a possible case that indent marker element was not inserted (e.g. because there is no blank line
            // at the target offset). However, we want to reformat white space adjacent to the current template (if any).
            PsiElement whiteSpaceElement = CodeStyleManagerImpl.findWhiteSpaceNode(file, endVarOffset);
            if (whiteSpaceElement != null) {
              TextRange whiteSpaceRange = whiteSpaceElement.getTextRange();
              if (whiteSpaceElement.getContainingFile() != null) {
                // Support injected white space nodes.
                whiteSpaceRange = InjectedLanguageManager.getInstance(file.getProject()).injectedToHost(whiteSpaceElement, whiteSpaceRange);
              }
              reformatStartOffset = Math.min(reformatStartOffset, whiteSpaceRange.getStartOffset());
              reformatEndOffset = Math.max(reformatEndOffset, whiteSpaceRange.getEndOffset());
            }
          }
          style.reformatText(file, reformatStartOffset, reformatEndOffset);
          unblockDocument();

          if (dummyAdjustLineMarkerRange != null && dummyAdjustLineMarkerRange.isValid()) {
            //[ven] TODO: [max] correct javadoc reformatting to eliminate isValid() check!!!
            getSegments().replaceSegmentAt(endSegmentNumber, dummyAdjustLineMarkerRange.getStartOffset(), dummyAdjustLineMarkerRange.getEndOffset());
            getDocument().deleteString(dummyAdjustLineMarkerRange.getStartOffset(), dummyAdjustLineMarkerRange.getEndOffset());
            PsiDocumentManager.getInstance(myProject).commitDocument(getDocument());
          }
          if (endSegmentNumber >= 0) {
            final int offset = getSegments().getSegmentStart(endSegmentNumber);
            final int lineStart = getDocument().getLineStartOffset(getDocument().getLineNumber(offset));
            // if $END$ is at line start, put it at correct indentation
            if (getDocument().getCharsSequence().subSequence(lineStart, offset).toString().trim().isEmpty()) {
              final int adjustedOffset = style.adjustLineIndent(file, offset);
              getSegments().replaceSegmentAt(endSegmentNumber, adjustedOffset, adjustedOffset);
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  /**
   * @return the segment template end on. Caret going to be positioned at this segment in the end.
   */
  private int getFinalSegmentNumber() {
    int endSegmentNumber = getTemplate().getEndSegmentNumber();
    if (endSegmentNumber < 0 && getSelectionBeforeTemplate() == null) {
      endSegmentNumber = getTemplate().getVariableSegmentNumber(Template.SELECTION);
    }
    return endSegmentNumber;
  }

  private void smartIndent(int startOffset, int endOffset) {
    int startLineNum = getDocument().getLineNumber(startOffset);
    int endLineNum = getDocument().getLineNumber(endOffset);
    if (endLineNum == startLineNum) {
      return;
    }

    int selectionIndent = -1;
    int selectionStartLine = -1;
    int selectionEndLine = -1;
    int selectionSegment = getTemplate().getVariableSegmentNumber(Template.SELECTION);
    if (selectionSegment >= 0) {
      int selectionStart = getTemplate().getSegmentOffset(selectionSegment);
      selectionIndent = 0;
      String templateText = getTemplate().getTemplateText();
      while (selectionStart > 0 && templateText.charAt(selectionStart - 1) == ' ') {
        // TODO handle tabs
        selectionIndent++;
        selectionStart--;
      }
      selectionStartLine = getDocument().getLineNumber(getSegments().getSegmentStart(selectionSegment));
      selectionEndLine = getDocument().getLineNumber(getSegments().getSegmentEnd(selectionSegment));
    }

    int indentLineNum = startLineNum;

    int lineLength = 0;
    for (; indentLineNum >= 0; indentLineNum--) {
      lineLength = getDocument().getLineEndOffset(indentLineNum) - getDocument().getLineStartOffset(indentLineNum);
      if (lineLength > 0) {
        break;
      }
    }
    if (indentLineNum < 0) {
      return;
    }
    StringBuilder buffer = new StringBuilder();
    CharSequence text = getDocument().getCharsSequence();
    for (int i = 0; i < lineLength; i++) {
      char ch = text.charAt(getDocument().getLineStartOffset(indentLineNum) + i);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      buffer.append(ch);
    }
    if (buffer.length() == 0 && selectionIndent <= 0 || startLineNum >= endLineNum) {
      return;
    }
    String stringToInsert = buffer.toString();
    int finalSelectionStartLine = selectionStartLine;
    int finalSelectionEndLine = selectionEndLine;
    int finalSelectionIndent = selectionIndent;
    DocumentUtil.executeInBulk(getDocument(), true, () -> {
      for (int i = startLineNum + 1; i <= endLineNum; i++) {
        if (i > finalSelectionStartLine && i <= finalSelectionEndLine) {
          getDocument().insertString(getDocument().getLineStartOffset(i), StringUtil.repeatSymbol(' ', finalSelectionIndent));
        }
        else {
          getDocument().insertString(getDocument().getLineStartOffset(i), stringToInsert);
        }
      }
    });
  }

  public void addTemplateStateListener(TemplateEditingListener listener) {
    myListeners.add(listener);
  }

  private void fireTemplateFinished(boolean brokenOff) {
    for (TemplateEditingListener listener : myListeners) {
      listener.templateFinished(ObjectUtils.chooseNotNull(getTemplate(), myPrevTemplate), brokenOff);
    }
  }

  private void fireBeforeTemplateFinished(boolean brokenOff) {
    for (TemplateEditingListener listener : myListeners) {
      listener.beforeTemplateFinished(this, getTemplate(), brokenOff);
    }
  }

  private void fireWaitingForInput() {
    for (TemplateEditingListener listener : myListeners) {
      listener.waitingForInput(getTemplate());
    }
  }
  private void fireTemplateCancelled() {
    for (TemplateEditingListener listener : myListeners) {
      listener.templateCancelled(getTemplate());
    }
  }

  private void fireCurrentVariableChanged(int oldIndex) {
    for (TemplateEditingListener listener : myListeners) {
      listener.currentVariableChanged(this, getTemplate(), oldIndex, myCurrentVariableNumber);
    }
    if (myCurrentSegmentNumber < 0) {
      if (myCurrentVariableNumber >= 0) {
        LOG.error("A variable with no segment: " + myCurrentVariableNumber + "; " + presentTemplate(getTemplate()));
      }
    }
  }

  @Override
  public TemplateImpl getTemplate() {
    return (TemplateImpl) super.getTemplate();
  }

  private static final class MyBasicUndoableAction extends BasicUndoableAction implements Disposable {
    @Nullable
    private TemplateState myTemplateState;

    private MyBasicUndoableAction(@NotNull TemplateState templateState, @Nullable Document document) {
      super(document != null ? new DocumentReference[]{DocumentReferenceManager.getInstance().create(document)} : null);
      myTemplateState = templateState;
    }

    @Override
    public void undo() {
      if (myTemplateState != null) {
        LookupManager.getInstance(myTemplateState.myProject).hideActiveLookup();
        myTemplateState.cancelTemplate();
      }
    }

    @Override
    public void redo() {
    }

    @Override
    public void dispose() {
      myTemplateState = null;
    }
  }
}
