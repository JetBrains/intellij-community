package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;

public class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private final CompletionType myCompletionType;

  public CodeCompletionHandlerBase(final CompletionType completionType) {
    myCompletionType = completionType;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile psiFile) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    }

    invokeCompletion(project, editor, psiFile, 1);
  }

  public void invokeCompletion(final Project project, final Editor editor, final PsiFile psiFile, int time) {
    TemplateSettings.getInstance(); //deadlock fix

    final Document document = editor.getDocument();
    if (editor.isViewer()) {
      document.fireReadOnlyModificationAttempt();
      return;
    }
    if (!psiFile.isWritable() && !FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
      return;
    }

    CompletionProgressIndicator indicator = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (indicator != null) {
      if (indicator.getHandler().getClass().equals(getClass())) {
        if (!indicator.isRunning() && (!isAutocompleteCommonPrefixOnInvocation() || indicator.fillInCommonPrefix(true))) {
          return;
        }
        else {
          time = indicator.getParameters().getInvocationCount() + 1;
        }
      }
      indicator.closeAndFinish();
    }

    if (time != 1) {
      if (myCompletionType == CompletionType.CLASS_NAME) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION);
      }
      else if (myCompletionType == CompletionType.BASIC) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
      }
    }

    final CompletionInitializationContext initializationContext = new WriteCommandAction<CompletionInitializationContext>(project) {
      protected void run(Result<CompletionInitializationContext> result) throws Throwable {
        CommandProcessor.getInstance().setCurrentCommandGroupId(null);

        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

        EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);
        documentManager.commitAllDocuments();
        final CompletionInitializationContext initializationContext = new CompletionInitializationContext(editor, psiFile, myCompletionType);
        result.setResult(initializationContext);

        for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
          contributor.beforeCompletion(initializationContext);
          assert !documentManager.isUncommited(editor.getDocument()) : "Contributor " + contributor + " left the document uncommitted";
        }
      }
    }.execute().getResultObject();


    final int offset1 = initializationContext.getStartOffset();
    final int offset2 = initializationContext.getSelectionEndOffset();
    final CompletionContext context = new CompletionContext(project, editor, psiFile, initializationContext.getOffsetMap());

    doComplete(offset1, offset2, context, initializationContext.getFileCopyPatcher(), editor, time);
  }

  protected void doComplete(final int offset1,
                            final int offset2,
                            final CompletionContext context,
                            final FileCopyPatcher patcher, final Editor editor, final int invocationCount) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && context.editor.getComponent().getRootPane() == null) {
      LOG.assertTrue(false, "null root pane");
    }


    final Pair<CompletionContext, PsiElement> insertedInfo = new WriteCommandAction<Pair<CompletionContext, PsiElement>>(context.project) {
      protected void run(Result<Pair<CompletionContext, PsiElement>> result) throws Throwable {
        result.setResult(insertDummyIdentifier(context, patcher));
      }
    }.execute().getResultObject();

    final PsiElement insertedElement = insertedInfo.getSecond();
    final CompletionContext newContext = insertedInfo.getFirst();
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);

    final CompletionParameters parameters = new CompletionParameters(insertedElement, newContext.file, myCompletionType, newContext.getStartOffset(), invocationCount);

    final Semaphore freezeSemaphore = new Semaphore();
    freezeSemaphore.down();
    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, this, context, freezeSemaphore);

    final Ref<LookupElement[]> data = Ref.create(null);

    final Runnable computeRunnable = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            try {
              final Collection<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();

              CompletionService.getCompletionService().getVariantsFromContributors(CompletionContributor.EP_NAME, parameters, null, new Consumer<LookupElement>() {
                public void consume(final LookupElement lookupElement) {
                  ApplicationManager.getApplication().runReadAction(new Runnable() {
                    public void run() {
                      if (lookupSet.add(lookupElement)) {
                        indicator.addItem(lookupElement);
                      }
                    }
                  });
                }
              });
              indicator.getLookup().setCalculating(false);

              final LookupElement[] items = lookupSet.toArray(new LookupElement[lookupSet.size()]);
              data.set(items);
              freezeSemaphore.up();
              if (items.length == 0) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (indicator != CompletionServiceImpl.getCompletionService().getCurrentCompletion()) return;
                    final Lookup lookup = LookupManager.getActiveLookup(editor);
                    assert lookup == indicator.getLookup() : lookup;

                    indicator.closeAndFinish();
                    CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
                    assert completion == null : "1 this=" + indicator + "\ncurrent=" + completion;
                    HintManager.getInstance().hideAllHints();
                    completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
                    assert completion == null : "2 this=" + indicator + "\ncurrent=" + completion;
                    handleEmptyLookup(context, parameters, indicator);
                  }
                });
              }
            }
            catch (ProcessCanceledException e) {
            }
          }
        }, indicator);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      computeRunnable.run();

      if (data.get().length == 0) {
        indicator.closeAndFinish();
      }
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);

      if (!freezeSemaphore.waitFor(2000) || data.isNull()) {
        indicator.showLookup();
        return;
      }
    }

    completionFinished(offset1, offset2, context, indicator, data.get());
  }

  protected void completionFinished(final int offset1, final int offset2, final CompletionContext context, final CompletionProgressIndicator indicator,
                                    final LookupElement[] items) {
    if (items.length == 0) return;

    LookupElement item = items[0];
    if (items.length == 1 && indicator.willAutoInsert(item.getAutoCompletionPolicy(), item.getPrefixMatcher())) {
      indicator.closeAndFinish();
      context.setStartOffset(offset1 - item.getPrefixMatcher().getPrefix().length());
      handleSingleItem(offset2, context, items, item.getLookupString(), item);
    } else {
      indicator.showLookup();
      if (isAutocompleteCommonPrefixOnInvocation() && items.length > 1) {
        indicator.fillInCommonPrefix(false);
      }
    }
  }

  protected static void handleSingleItem(final int offset2, final CompletionContext context, final LookupElement[] items, final String _uniqueText, final LookupElement item) {

    new WriteCommandAction(context.project) {
      protected void run(Result result) throws Throwable {
        String uniqueText = _uniqueText;

        if (item.getObject() instanceof DeferredUserLookupValue && item instanceof LookupItem) {
          if (!((DeferredUserLookupValue)item.getObject()).handleUserSelection((LookupItem)item, context.project)) {
            return;
          }

          uniqueText = item.getLookupString(); // text may be not ready yet
        }

        if (!StringUtil.startsWithIgnoreCase(uniqueText, item.getPrefixMatcher().getPrefix())) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
        }

        insertLookupString(context, offset2, uniqueText);
        context.editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

        lookupItemSelected(context, item, Lookup.AUTO_INSERT_SELECT_CHAR, false, items);
      }
    }.execute();
  }

  private static void insertLookupString(final CompletionContext context, final int currentOffset, final String newText) {
    Editor editor = context.editor;
    editor.getDocument().replaceString(context.getStartOffset(), currentOffset, newText);
    editor.getCaretModel().moveToOffset(context.getStartOffset() + newText.length());
    editor.getSelectionModel().removeSelection();
  }

  protected static void selectLookupItem(final LookupElement item, final boolean signatureSelected, final char completionChar, final CompletionContext context,
                                        final LookupElement[] items) {
    final int caretOffset = context.editor.getCaretModel().getOffset();

    context.setSelectionEndOffset(caretOffset);
    final int idEnd = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
    final int identifierEndOffset =
        CompletionUtil.isOverwrite(item, completionChar) && context.getSelectionEndOffset() == idEnd ?
        caretOffset :
        Math.max(caretOffset, idEnd);
    context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, identifierEndOffset);
    lookupItemSelected(context, item, completionChar, signatureSelected, items);
  }

  private Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context, final FileCopyPatcher patcher) {
    PsiFile oldFileCopy = createFileCopy(context.file);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(oldFileCopy);
    Project project = hostFile.getProject();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    // is null in tests
    int hostStartOffset = injectedLanguageManager == null ? context.getStartOffset() : injectedLanguageManager.injectedToHost(oldFileCopy, TextRange.from(
        context.getStartOffset(), 0)).getStartOffset();

    Document document = oldFileCopy.getViewProvider().getDocument();
    assert document != null;
    patcher.patchFileCopy(oldFileCopy, document, context.getOffsetMap());
    String s = document.getText();
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
      if (source != null) {
        return source.findElementAt(startOffset - source.getTextRange().getStartOffset());
      }
    }
    return element;
  }


  protected boolean mayAutocompleteOnInvocation() {
    return true;
  }

  protected boolean isAutocompleteCommonPrefixOnInvocation() {
    return CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
  }

  protected void handleEmptyLookup(CompletionContext context, final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;
    if (!ApplicationManager.getApplication().isUnitTestMode() && context.editor.getComponent().getRootPane() == null) {
      LOG.assertTrue(false, "null root pane");
    }

    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      final String text = contributor.handleEmptyLookup(parameters, editor);
      if (StringUtil.isNotEmpty(text)) {
        final EditorHintListener listener = new EditorHintListener() {
          public void hintShown(final Project project, final LightweightHint hint, final int flags) {
            indicator.liveAfterDeath(hint);
          }
        };
        final MessageBusConnection connection = context.project.getMessageBus().connect();
        connection.subscribe(EditorHintListener.TOPIC, listener);
        HintManager.getInstance().showErrorHint(editor, text);
        connection.disconnect();
        break;
      }
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private static void lookupItemSelected(final CompletionContext context, @NotNull final LookupElement item, final char completionChar, final boolean signatuireSelected, final LookupElement[] items) {
    final Editor editor = context.editor;
    final PsiFile file = context.file;
    final InsertionContext context1 = new InsertionContext(context.getOffsetMap(), completionChar, signatuireSelected, items, file, editor);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
        if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite(item, completionChar)) {
          editor.getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
        }

        PsiDocumentManager.getInstance(context.project).commitAllDocuments();
        item.handleInsert(context1);

        if (context1.shouldAddCompletionChar() &&
            completionChar != Lookup.AUTO_INSERT_SELECT_CHAR && completionChar != Lookup.REPLACE_SELECT_CHAR &&
            completionChar != Lookup.NORMAL_SELECT_CHAR && completionChar != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
          TailType.insertChar(editor, context1.getTailOffset(), completionChar);
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
    final Runnable runnable = context1.getLaterRunnable();
    if (runnable != null) {
      final Runnable runnable1 = new Runnable() {
        public void run() {
          final Project project = context1.getProject();
          if (project.isDisposed()) return;
          runnable.run();
        }
      };
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(runnable1);
      } else {
        runnable1.run();
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected PsiFile createFileCopy(PsiFile file) {
    return (PsiFile)file.copy();
  }
}
