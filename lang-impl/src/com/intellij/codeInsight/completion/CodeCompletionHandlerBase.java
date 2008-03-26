package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.impl.CompletionService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.AsyncConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

abstract class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  static final Key<Class<? extends CodeCompletionHandlerBase>> COMPLETION_HANDLER_CLASS_KEY =
      Key.create("COMPLETION_HANDLER_CLASS_KEY");
  protected final CompletionType myCompletionType;

  protected CodeCompletionHandlerBase(final CompletionType completionType) {
    myCompletionType = completionType;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile psiFile) {
    final Document document = editor.getDocument();
    if (editor.isViewer()) {
      document.fireReadOnlyModificationAttempt();
      return;
    }
    if (!psiFile.isWritable() && !FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
      return;
    }

    final CompletionProgressIndicator indicator = CompletionProgressIndicator.getCurrentCompletion();
    if (indicator != null) {
      if (!indicator.isRunning() &&
          !indicator.isCanceled() &&
          indicator.getHandler().getClass().equals(getClass()) &&
          (!isAutocompleteCommonPrefixOnInvocation() || indicator.fillInCommonPrefix())) {
        return;
      }
      indicator.closeAndFinish();
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

    doComplete(offset1, offset2, context, initializationContext.getDummyIdentifier(), editor);
  }

  protected void doComplete(final int offset1,
                            final int offset2,
                            final CompletionContext context,
                            final String dummyIdentifier, final Editor editor) {
    final Pair<CompletionContext, PsiElement> insertedInfo = new WriteCommandAction<Pair<CompletionContext, PsiElement>>(context.project) {
      protected void run(Result<Pair<CompletionContext, PsiElement>> result) throws Throwable {
        result.setResult(insertDummyIdentifier(context, dummyIdentifier));
      }
    }.execute().getResultObject();

    final PsiElement insertedElement = insertedInfo.getSecond();
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, insertedInfo.getFirst());

    final CompletionParametersImpl parameters = new CompletionParametersImpl(insertedInfo.getSecond(), insertedInfo.getFirst().file, myCompletionType);
    final String adText = CompletionService.getCompletionService().getAdvertisementText(parameters);

    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, adText, this, insertedInfo.getFirst());

    final Runnable computeRunnable = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            try {
              final LookupData data = computeLookupData(insertedElement, insertedInfo.getFirst(), parameters, indicator);
              insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, null);
              if (data == null) {
                indicator.cancel();
                return; //cancelled
              }

              final LookupItem[] items = data.items;
              if (items.length == 0) {
                indicator.cancel();
                invokeAndWait(new Runnable() {
                  public void run() {
                    handleEmptyLookup(context, data, adText);
                  }
                }, context.editor);
                return;
              }

              final String prefix = data.prefix;
              context.setPrefix(data.prefix);
              context.setStartOffset(offset1 - prefix.length());

              if (shouldAutoComplete(items, context)) {
                LookupItem item = items[0];
                computingFinished(data, null, prefix, item.getLookupString(), item, false, indicator, offset2, context, offset1);
              } else {
                computingFinished(data, items, prefix, null, null, true, indicator, offset2, context, offset1);
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
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);
    }
  }

  private boolean shouldAutoComplete(final LookupItem[] items, final CompletionContext context) {
    if (items.length != 1) return false;

    final LookupItem item = items[0];
    if (item.getAttribute(LookupItem.DO_NOT_AUTOCOMPLETE_ATTR) != null) return false;
    if (item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) != null) return true;

    if (!isAutocompleteOnInvocation()) return false;
    return context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) == context.getSelectionEndOffset(); //give a chance to use tab
  }

  public static void invokeAndWait(final Runnable runnable, Editor editor) {
    ProgressManager.getInstance().checkCanceled();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.stateForComponent(editor.getComponent()));
    }
  }

  protected void computingFinished(final LookupData data, final LookupItem[] items, final String prefix, final String uniqueText, final LookupItem item,
                                   final boolean doNotAutocomplete,
                                   final CompletionProgressIndicator indicator,
                                   final int offset2,
                                   final CompletionContext context,
                                   final int offset1) {
    ProgressManager.getInstance().checkCanceled();
    invokeAndWait(new Runnable() {
      public void run() {
        if (indicator.isCanceled()) return;
        if (item != null) {
          indicator.closeAndFinish();
          handleSingleItem(offset2, context, data, prefix, uniqueText, item);
        }
        else {
          handleMultipleItems(offset1, context, items, prefix, indicator.getLookup());
        }
      }
    }, context.editor);
  }

  private void handleSingleItem(final int offset2, final CompletionContext context, final LookupData data,
                                final String prefix,
                                final String _uniqueText,
                                final LookupItem item) {

    new WriteCommandAction(context.project) {
      protected void run(Result result) throws Throwable {
        String uniqueText = _uniqueText;

        if (item.getObject() instanceof DeferredUserLookupValue) {
          if (!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item, context.project)) {
            return;
          }

          uniqueText = item.getLookupString(); // text may be not ready yet
          data.prefix = "";
          context.setPrefix(""); // prefix may be of no interest
        }

        if (!StringUtil.startsWithIgnoreCase(uniqueText, prefix)) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
        }

        insertLookupString(context, offset2, uniqueText);
        context.editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

        lookupItemSelected(context, data, item, false, (char)0);
      }
    }.execute();
  }



  private void handleMultipleItems(final int offset1, final CompletionContext context, final LookupItem[] items, final String prefix, final LookupImpl lookup) {
    final Project project = context.project;
    final String newPrefix = new WriteCommandAction<String>(project) {
      protected void run(Result<String> result) throws Throwable {
        if (isAutocompleteCommonPrefixOnInvocation() && items.length > 1) {
          result.setResult(fillInCommonPrefix(items, prefix, context.editor));
        } else {
          result.setResult(prefix);
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
    }.execute().getResultObject();

    if (!newPrefix.equals(prefix)) {
      lookup.setPrefix(newPrefix);
      context.editor.getCaretModel().moveToOffset(offset1 - prefix.length() + newPrefix.length());
      context.setPrefix(newPrefix);
      context.editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  @Nullable protected static String appendSuggestion(@Nullable String prefix, String adText) {
    if (prefix == null) return adText;
    if (adText == null) return prefix;
    return prefix + "; " + adText;
  }

  private static void insertLookupString(final CompletionContext context, final int currentOffset, final String newText) {
    Editor editor = context.editor;
    editor.getDocument().replaceString(context.getStartOffset(), currentOffset, newText);
    editor.getCaretModel().moveToOffset(context.getStartOffset() + newText.length());
    editor.getSelectionModel().removeSelection();
  }

  protected final void selectLookupItem(final LookupItem item, final boolean signatureSelected, final char completionChar, final CompletionContext context,
                                        final LookupData data) {
    insertLookupString(context, context.editor.getCaretModel().getOffset(), item.getLookupString());
    final int caretOffset = context.editor.getCaretModel().getOffset();

    final int previousSelectionEndOffset = context.getSelectionEndOffset();

    context.setSelectionEndOffset(caretOffset);
    final int identifierEndOffset =
        CompletionUtil.isOverwrite(item, completionChar) && previousSelectionEndOffset ==
                                                            context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) ?
                                                                                                                                                    caretOffset:Math.max(caretOffset, context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, identifierEndOffset);
    lookupItemSelected(context, data, item, signatureSelected, completionChar);
  }

  protected String fillInCommonPrefix(LookupItem[] items, final String prefix, final Editor editor) {
    String commonPrefix = null;
    boolean isStrict = false;

    final int prefixLength = prefix.length();
    for (final LookupItem item : items) {
      final String lookupString = item.getLookupString();
      if (!StringUtil.startsWithIgnoreCase(lookupString, prefix)) {
        // since camel humps
        return prefix;
      }

      if (commonPrefix != null) {
        int matchingRegLength = lookupString.length();
        while (!lookupString.regionMatches(0, commonPrefix, 0, matchingRegLength--)) ;
        commonPrefix = lookupString.substring(0, matchingRegLength + 1);
        if (commonPrefix.length() < lookupString.length()) {
          isStrict = true;
        }
        if (commonPrefix.length() <= prefixLength) {
          return prefix;
        }
      }
      else {
        commonPrefix = lookupString;
      }
    }

    if (!isStrict) return prefix;

    int offset =
        editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionStart() : editor.getCaretModel().getOffset();
    int lookupStart = offset - prefixLength;
    int replacedLength = prefixLength;
    final int commonPrefixLength = commonPrefix.length();

    if (prefixLength < commonPrefixLength) {
      final CharSequence sequence = editor.getDocument().getCharsSequence();
      final int sequenceLength = sequence.length();

      while (replacedLength < commonPrefixLength &&
             lookupStart + replacedLength < sequenceLength &&
             sequence.charAt(lookupStart + replacedLength) == commonPrefix.charAt(replacedLength)) {
        replacedLength++;
      }
    }

    editor.getDocument().replaceString(lookupStart, lookupStart + replacedLength, commonPrefix);

    return commonPrefix;
  }

  @Nullable
  protected LookupData computeLookupData(final PsiElement insertedElement, final CompletionContext context,
                                         final CompletionParametersImpl parameters, final CompletionProgressIndicator indicator) {
    final Collection<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();

    CompletionService.getCompletionService().performAsyncCompletion(myCompletionType, parameters, new AsyncConsumer<LookupElement>() {
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
    final LookupData data = ApplicationManager.getApplication().runReadAction(new Computable<LookupData>() {
      public LookupData compute() {
        return createLookupData(context, items, insertedElement);
      }
    });
    data.itemPreferencePolicy = new CompletionPreferencePolicy(context.getPrefix(), parameters);
    return data;
  }

  protected LookupData createLookupData(final CompletionContext context, final LookupItem[] items, final PsiElement insertedElement) {
    return new LookupData(items, context.getPrefix());
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
    PsiFile fileCopy = InjectedLanguageUtil.findInjectedPsiAt(hostFile, hostStartOffset);
    if (fileCopy == null) {
      PsiElement elementAfterCommit = findElementAt(hostFile, hostStartOffset);
      fileCopy = elementAfterCommit == null ? oldFileCopy : elementAfterCommit.getContainingFile();
    }

    if (oldFileCopy != fileCopy) {
      // newly inserted identifier can well end up in the injected language region
      Editor oldEditor = context.editor;
      Editor editor = EditorFactory.getInstance().createEditor(document, project);
      Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguage(editor, hostFile, context.getStartOffset());
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


  protected Lookup showLookup(Project project,
                              final Editor editor,
                              final LookupItem[] items,
                              String prefix,
                              final LookupData data,
                              PsiFile file, final String bottomText) {
    return LookupManager.getInstance(project).showLookup(editor, items, prefix, data.itemPreferencePolicy, bottomText);
  }

  protected abstract boolean isAutocompleteOnInvocation();

  protected abstract boolean isAutocompleteCommonPrefixOnInvocation();

  protected void analyseItem(LookupItem item, PsiElement place, CompletionContext context) {}

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData, final String adText) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;

    LOG.assertTrue(lookupData.items.length == 0);
    if (lookupData.prefix != null) {
      HintManager.getInstance().showErrorHint(editor, appendSuggestion(LangBundle.message("completion.no.suggestions"), adText));
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private void lookupItemSelected(final CompletionContext context, final LookupData data,
                                  @NotNull final LookupItem item,
                                  final boolean signatureSelected,
                                  final char completionChar) {
    final InsertHandler handler;
    if (item.getInsertHandler() == null) {
      handler = new InsertHandler() {
        public void handleInsert(final CompletionContext context,
                                 final int startOffset,
                                 final LookupData data,
                                 final LookupItem item,
                                 final boolean signatureSelected,
                                 final char completionChar) {
          final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
          if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite(item, completionChar)) {
            context.editor.getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
          }
          item.getTailType().processTail(context.editor, context.editor.getCaretModel().getOffset());
        }
      };
    }
    else {
      handler = item.getInsertHandler();
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(context.project).commitAllDocuments();
        context.setPrefix(data.prefix);
        final PsiElement position = context.file.findElementAt(context.getStartOffset() + item.getLookupString().length() - 1);
        analyseItem(item, position, context);
        handler.handleInsert(context, context.getStartOffset(), data, item, signatureSelected, completionChar);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected PsiFile createFileCopy(PsiFile file) {
    final PsiElementVisitor originalVisitor = new PsiRecursiveElementVisitor() {
      public void visitElement(final PsiElement element) {
        if (element instanceof LeafElement) return;

        element.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, element);
        super.visitElement(element);
      }
    };
    originalVisitor.visitFile(file);


    final PsiFile fileCopy = (PsiFile)file.copy();

    final PsiElementVisitor copyVisitor = new PsiRecursiveElementVisitor() {

      public void visitElement(final PsiElement element) {
        if (element instanceof LeafElement) return;

        final PsiElement originalElement = element.getCopyableUserData(CompletionUtil.ORIGINAL_KEY);
        if (originalElement != null) {
          originalElement.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, null);
          element.putCopyableUserData(CompletionUtil.ORIGINAL_KEY, null);
          element.putUserData(CompletionUtil.ORIGINAL_KEY, originalElement);
        }
        super.visitElement(element);
      }

    };
    copyVisitor.visitFile(fileCopy);
    return fileCopy;
  }


}
