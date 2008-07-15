package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.DeferredUserLookupValue;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.LinkedHashSet;

abstract class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private final CompletionType myCompletionType;
  private static final Key COMPLETION_EMPTY_MESSAGE = Key.create("COMPLETION_EMPTY_MESSAGE");

  protected CodeCompletionHandlerBase(final CompletionType completionType) {
    myCompletionType = completionType;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile psiFile) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    }

    invokeCompletion(project, editor, psiFile, 1);
  }

  public void invokeCompletion(final Project project, final Editor editor, final PsiFile psiFile, int time) {
    final Document document = editor.getDocument();
    if (editor.isViewer()) {
      document.fireReadOnlyModificationAttempt();
      return;
    }
    if (!psiFile.isWritable() && !FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
      return;
    }

    CompletionProgressIndicator indicator = CompletionProgressIndicator.getCurrentCompletion();
    if (indicator != null) {
      if (!indicator.isCanceled() && indicator.getHandler().getClass().equals(getClass())) {
        if (!indicator.isRunning() && (!isAutocompleteCommonPrefixOnInvocation() || indicator.fillInCommonPrefix())) {
          return;
        }
        else {
          time++;
        }
      }
      indicator.closeAndFinish();
    } else {
      for (final LightweightHint hint : HintManager.getInstance().getAllHints()) {
        indicator = (CompletionProgressIndicator)hint.getComponent().getClientProperty(COMPLETION_EMPTY_MESSAGE);
        if (indicator != null && indicator.getHandler().getClass().equals(getClass())) {
          time++;
        }
      }
    }
    HintManager.getInstance().hideAllHints();

    if (time != 0 && myCompletionType == CompletionType.CLASS_NAME) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION);
    }

    final PsiFile file = psiFile;

    final CompletionInitializationContext initializationContext = new WriteCommandAction<CompletionInitializationContext>(project) {
      protected void run(Result<CompletionInitializationContext> result) throws Throwable {
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

        EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);
        documentManager.commitAllDocuments();
        final CompletionInitializationContext initializationContext = new CompletionInitializationContext(editor, file, myCompletionType);
        result.setResult(initializationContext);

        DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file);
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting(file.getViewProvider());

        documentManager.commitAllDocuments();
        for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
          contributor.beforeCompletion(initializationContext);
          assert !documentManager.isUncommited(editor.getDocument()) : "Contributor " + contributor + " left the document uncommitted";
        }
      }
    }.execute().getResultObject();


    final int offset1 = initializationContext.getStartOffset();
    final int offset2 = initializationContext.getSelectionEndOffset();
    final CompletionContext context = new CompletionContext(project, editor, file, initializationContext.getOffsetMap());

    doComplete(offset1, offset2, context, initializationContext.getDummyIdentifier(), editor, time);
  }

  protected void doComplete(final int offset1,
                            final int offset2,
                            final CompletionContext context,
                            final String dummyIdentifier, final Editor editor, final int invocationCount) {
    final Pair<CompletionContext, PsiElement> insertedInfo = new WriteCommandAction<Pair<CompletionContext, PsiElement>>(context.project) {
      protected void run(Result<Pair<CompletionContext, PsiElement>> result) throws Throwable {
        result.setResult(insertDummyIdentifier(context, dummyIdentifier));
      }
    }.execute().getResultObject();

    final PsiElement insertedElement = insertedInfo.getSecond();
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, insertedInfo.getFirst());

    final CompletionParameters parameters = new CompletionParameters(insertedInfo.getSecond(), insertedInfo.getFirst().file, myCompletionType, insertedInfo.getFirst().getStartOffset(),
                                                                             invocationCount);
    final String adText = getAdvertisementText(parameters);

    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, adText, this, insertedInfo.getFirst(), context);

    final Semaphore startSemaphore = new Semaphore();
    startSemaphore.down();

    final Ref<LookupData> data = Ref.create(null);

    final Runnable computeRunnable = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            try {
              startSemaphore.up();
              final Collection<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();

              CompletionService.getCompletionService().getVariantsFromContributors(CompletionContributor.EP_NAME, parameters, null, new Consumer<LookupElement>() {
                public void consume(final LookupElement lookupElement) {
                  ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                      if (lookupSet.add(lookupElement)) {
                        indicator.addItem((LookupItem)lookupElement);
                      }
                    }
                  });
                }
              });
              final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
              final LookupData data1 = new LookupData(items);
              data.set(data1);
            }
            catch (ProcessCanceledException e) {
            }
          }
        }, indicator);
      }
    };

    final Queue<AWTEvent> queue = new Queue<AWTEvent>(10);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      computeRunnable.run();
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);

      startSemaphore.waitFor();

      IdeEventQueue.getInstance().pumpEventsForHierarchy(indicator.getLookup().getComponent(), new Condition<AWTEvent>() {
        public boolean value(final AWTEvent object) {
          if (object instanceof KeyEvent && !indicator.isInitialized()) {
            final KeyEvent event = (KeyEvent)object;
            queue.addLast(new KeyEvent(event.getComponent(), event.getID(), event.getWhen(), event.getModifiers(), event.getKeyCode(),
                                       event.getKeyChar(), event.getKeyLocation()));
          } else if (indicator.getLookup().isVisible()) {
            flushQueue(queue);
          }

          return !indicator.isRunning() || indicator.isCanceled();
        }
      });
    }

    if (!indicator.isCanceled()) {
      computingFinished(data.get(), indicator, context, parameters, offset1, offset2);
    }

    flushQueue(queue);
  }

  @Nullable
  private static String getAdvertisementText(final CompletionParameters parameters) {
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      final String s = contributor.advertise(parameters);
      if (s != null) return s;
    }
    return null;
  }

  private static void flushQueue(final Queue<AWTEvent> queue) {
    while (!queue.isEmpty()) {
      IdeEventQueue.getInstance().dispatchEvent(queue.pullFirst());
    }
  }

  protected void computingFinished(final LookupData data, final CompletionProgressIndicator indicator, final CompletionContext context,
                                   final CompletionParameters parameters, final int offset1, final int offset2) {
    if (data == null) {
      indicator.closeAndFinish();
      return;
    }

    final LookupItem[] items = data.items;
    if (items.length == 0) {
      indicator.closeAndFinish();
      HintManager.getInstance().hideAllHints();
      handleEmptyLookup(context, data, parameters, indicator);
      return;
    }

    indicator.getLookup().setCalculating(false);
    if (shouldAutoComplete(items, context)) {
      LookupItem item = items[0];
      indicator.closeAndFinish();
      context.setStartOffset(offset1 - item.getPrefixMatcher().getPrefix().length());
      handleSingleItem(offset2, context, data, item.getLookupString(), item);
    } else {
      handleMultipleItems(items, indicator.getShownLookup(), context.project);
    }
  }

  private boolean shouldAutoComplete(final LookupItem[] items, final CompletionContext context) {
    if (items.length != 1) return false;

    final LookupItem item = items[0];
    final AutoCompletionPolicy policy = item.getAutoCompletionPolicy();
    if (policy == AutoCompletionPolicy.NEVER_AUTOCOMPLETE) return false;
    if (policy == AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) return true;

    if (!isAutocompleteOnInvocation()) return false;

    if (context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != context.getSelectionEndOffset()) return false;
    if (policy == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) return true;

    if (StringUtil.isEmpty(item.getPrefixMatcher().getPrefix()) && myCompletionType != CompletionType.SMART) return false;
    return true;
  }

  protected void handleSingleItem(final int offset2, final CompletionContext context, final LookupData data, final String _uniqueText, final LookupItem item) {

    new WriteCommandAction(context.project) {
      protected void run(Result result) throws Throwable {
        CommandProcessor.getInstance().setCurrentCommandGroupId(null);
        String uniqueText = _uniqueText;

        if (item.getObject() instanceof DeferredUserLookupValue) {
          if (!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item, context.project)) {
            return;
          }

          uniqueText = item.getLookupString(); // text may be not ready yet
        }

        if (!StringUtil.startsWithIgnoreCase(uniqueText, item.getPrefixMatcher().getPrefix())) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
        }

        insertLookupString(context, offset2, uniqueText);
        context.editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

        lookupItemSelected(context, item, (char)0, false, data.items);
      }
    }.execute();
  }

  private void handleMultipleItems(final LookupItem[] items, final LookupImpl lookup, final Project project) {
    new WriteCommandAction(project) {
      protected void run(Result result) throws Throwable {
        if (isAutocompleteCommonPrefixOnInvocation() && items.length > 1) {
          lookup.fillInCommonPrefix(false);
          PsiDocumentManager.getInstance(project).commitAllDocuments();
        }
      }
    }.execute().getResultObject();
  }

  private static void insertLookupString(final CompletionContext context, final int currentOffset, final String newText) {
    Editor editor = context.editor;
    editor.getDocument().replaceString(context.getStartOffset(), currentOffset, newText);
    editor.getCaretModel().moveToOffset(context.getStartOffset() + newText.length());
    editor.getSelectionModel().removeSelection();
  }

  protected final void selectLookupItem(final LookupItem item, final boolean signatureSelected, final char completionChar, final CompletionContext context,
                                        final LookupData data) {
    final int caretOffset = context.editor.getCaretModel().getOffset();

    final int previousSelectionEndOffset = context.getSelectionEndOffset();

    context.setSelectionEndOffset(caretOffset);
    final int identifierEndOffset =
        CompletionUtil.isOverwrite(item, completionChar) && previousSelectionEndOffset ==
                                                            context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) ?
                                                                                                                                                    caretOffset:Math.max(caretOffset, context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, identifierEndOffset);
    lookupItemSelected(context, item, completionChar, signatureSelected, data.items);
  }

  private Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context, final String dummyIdentifier) {
    PsiFile oldFileCopy = createFileCopy(context.file);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(oldFileCopy);
    Project project = hostFile.getProject();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    // is null in tests
    int hostStartOffset = injectedLanguageManager == null ? context.getStartOffset() : injectedLanguageManager.injectedToHost(oldFileCopy, TextRange.from(
        context.getStartOffset(), 0)).getStartOffset();

    Document document = oldFileCopy.getViewProvider().getDocument();

    document.replaceString(context.getStartOffset(), context.getSelectionEndOffset(), dummyIdentifier);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile fileCopy = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostStartOffset);
    if (fileCopy == null) {
      PsiElement elementAfterCommit = findElementAt(hostFile, hostStartOffset);
      fileCopy = elementAfterCommit == null ? oldFileCopy : elementAfterCommit.getContainingFile();
    }

    if (oldFileCopy != fileCopy) {
      // newly inserted identifier can well end up in the injected language region
      Editor oldEditor = context.editor;
      Editor editor = EditorFactory.getInstance().createEditor(document, project);
      Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, hostFile, context.getStartOffset());
      if (newEditor instanceof EditorWindow) {
        EditorWindow injectedEditor = (EditorWindow)newEditor;
        PsiFile injectedFile = injectedEditor.getInjectedFile();
        final OffsetMap map = new OffsetMap(newEditor.getDocument());
        final OffsetMap oldMap = context.getOffsetMap();
        for (final OffsetKey key : oldMap.keySet()) {
          map.addOffset(key, injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(oldEditor.offsetToLogicalPosition(oldMap.getOffset(key)))));
        }
        CompletionContext newContext = new CompletionContext(context.project, injectedEditor, injectedFile, map);
        PsiElement element = findElementAt(injectedFile, newContext.getStartOffset());
        if (element == null) {
          LOG.assertTrue(false, "offset " + newContext.getStartOffset() + " at:\n" + injectedFile.getText());
        }
        EditorFactory.getInstance().releaseEditor(editor);
        return Pair.create(newContext, element);
      }
      EditorFactory.getInstance().releaseEditor(editor);
    }
    PsiElement element = findElementAt(fileCopy, context.getStartOffset());
    if (element == null) {
      LOG.assertTrue(false, "offset " + context.getStartOffset() + " at:\n" + fileCopy.getText());
    }
    return Pair.create(context, element);
  }

  private static PsiElement findElementAt(final PsiFile fileCopy, int startOffset) {
    PsiElement element = fileCopy.findElementAt(startOffset);
    if (element instanceof MetadataPsiElementBase) {
      final PsiElement source = ((MetadataPsiElementBase)element).getSourceElement();
      return source.findElementAt(startOffset - source.getTextRange().getStartOffset());
    }
    return element;
  }


  protected abstract boolean isAutocompleteOnInvocation();

  protected abstract boolean isAutocompleteCommonPrefixOnInvocation();

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData, final CompletionParameters parameters,
                                   final CompletionProgressIndicator indicator) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;

    LOG.assertTrue(lookupData.items.length == 0);
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      final String text = contributor.handleEmptyLookup(parameters, editor);
      if (StringUtil.isNotEmpty(text)) {
        HintManager.getInstance().showErrorHint(editor, text);
        for (final LightweightHint hint : HintManager.getInstance().getAllHints()) {
          hint.getComponent().putClientProperty(COMPLETION_EMPTY_MESSAGE, indicator);
        }
        break;
      }
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private void lookupItemSelected(final CompletionContext context, @NotNull final LookupItem item, final char completionChar, final boolean signatuireSelected, final LookupItem[] items) {
    final InsertHandler handler;
    if (item.getInsertHandler() == null) {
      handler = new InsertHandler() {
        public void handleInsert(final InsertionContext context, final LookupElement item) {
          final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
          if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite((LookupItem)item, completionChar)) {
            context.getEditor().getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
          }
          final TailType type =
              SimpleInsertHandler.DEFAULT_COMPLETION_CHAR_HANDLER.handleCompletionChar(context.getEditor(), item, completionChar);
          type.processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
        }
      };
    }
    else {
      handler = item.getInsertHandler();
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(context.project).commitAllDocuments();
        handler.handleInsert(new InsertionContext(context.getOffsetMap(), completionChar, signatuireSelected, items, context.file, context.editor), item);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected PsiFile createFileCopy(PsiFile file) {
    return PsiUtilBase.copyElementPreservingOriginalLinks(file, CompletionUtil.ORIGINAL_KEY);
  }


}
