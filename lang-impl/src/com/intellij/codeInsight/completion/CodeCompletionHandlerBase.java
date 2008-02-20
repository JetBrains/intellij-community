package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CompletionService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

abstract class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private static final Key<Class<? extends CodeCompletionHandlerBase>> COMPLETION_HANDLER_CLASS_KEY =
    Key.create("COMPLETION_HANDLER_CLASS_KEY");
  protected final CompletionType myCompletionType;

  protected CodeCompletionHandlerBase(final CompletionType completionType) {
    myCompletionType = completionType;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    final Document document = editor.getDocument();
    if (editor.isViewer()) {
      document.fireReadOnlyModificationAttempt();
      return;
    }
    if (!file.isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
        return;
      }
    }

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null) {
      Class<? extends CodeCompletionHandlerBase> handlerClass = activeLookup.getUserData(COMPLETION_HANDLER_CLASS_KEY);
      if (handlerClass == null) {
        handlerClass = CodeCompletionHandler.class;
      }
      if (handlerClass.equals(getClass())) {
        if (!isAutocompleteCommonPrefixOnInvocation() || activeLookup.fillInCommonPrefix(true)) {
          return;
        }
      }
    }

    EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    documentManager.commitAllDocuments();
    final CompletionInitializationContext initializationContext = new CompletionInitializationContext(editor, file, myCompletionType);
    for (final CompletionContributor contributor : Extensions.getExtensions(CompletionContributor.EP_NAME)) {
      contributor.beforeCompletion(initializationContext);
      assert !documentManager.isUncommited(editor.getDocument()) : "Contributor " + contributor + " left the document uncommitted";
    }

    final int offset1 = initializationContext.getStartOffset();
    final int offset2 = initializationContext.getSelectionEndOffset();
    final CompletionContext context = new CompletionContext(project, editor, file, initializationContext.getOffsetMap());

    final LookupData data = getLookupData(context);
    final LookupItem[] items = data.items;
    String prefix = data.prefix;
    context.setPrefix(data.prefix);
    if (items.length == 0) {
      handleEmptyLookup(context, data);
      return;
    }
    context.setStartOffset(offset1 - prefix.length());

    String uniqueText = null;
    LookupItem item = null;
    boolean doNotAutocomplete = false;

    for (final LookupItem curItem : items) {
      if (curItem.getAttribute(LookupItem.DO_NOT_AUTOCOMPLETE_ATTR) != null) {
        item = null;
        doNotAutocomplete = true;
        break;
      }

      if (uniqueText == null) {
        uniqueText = curItem.getLookupString();
        item = curItem;
      }
      else if (!uniqueText.equals(curItem.getLookupString())) {
        item = null;
        break;
      }
    }

    if (item != null) {
      if (!isAutocompleteOnInvocation() && item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) == null) {
        item = null;
      }
    }
    if (item != null && context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != context.getSelectionEndOffset()) { // give a chance to use Tab
      if (item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) == null) {
        item = null;
      }
    }

    if (item != null) {
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
    else {
      if (isAutocompleteCommonPrefixOnInvocation() && !doNotAutocomplete) {
        final String newPrefix = fillInCommonPrefix(items, prefix, context.editor);

        if (!newPrefix.equals(prefix)) {
          editor.getCaretModel().moveToOffset(offset1 - prefix.length() + newPrefix.length());
          context.setPrefix(newPrefix);
          prefix = newPrefix;
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }

      documentManager.commitAllDocuments();
      final Lookup lookup = showLookup(project, editor, items, prefix, data, file, appendSuggestion(null, data));
      
      if (lookup != null) {
        lookup.putUserData(COMPLETION_HANDLER_CLASS_KEY, getClass());

        lookup.addLookupListener(new LookupAdapter() {
          public void itemSelected(LookupEvent event) {
            LookupItem item = event.getItem();
            if (item == null) return;

            selectLookupItem(item, settings.SHOW_SIGNATURES_IN_LOOKUPS || item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null,
                             event.getCompletionChar(), context, data);
            
          }
        });
      }
    }
  }

  @Nullable protected static String appendSuggestion(@Nullable String prefix, LookupData data) {
    if (prefix == null) return data.adText;
    if (data.adText == null) return prefix;
    return prefix + "; " + data.adText;
  }

  private static void insertLookupString(final CompletionContext context, final int currentOffset, final String newText) {
    final Editor editor = context.editor;
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

  private static String fillInCommonPrefix(LookupItem[] items, final String prefix, final Editor editor) {
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

  protected LookupData getLookupData(final CompletionContext _context) {
    final PsiFile file = _context.file;
    final PsiManager manager = file.getManager();

    final Pair<CompletionContext, PsiElement> insertedInfo =
      ApplicationManager.getApplication().runWriteAction(new Computable<Pair<CompletionContext, PsiElement>>() {
        public Pair<CompletionContext, PsiElement> compute() {
          return insertDummyIdentifier(_context);
        }
      });

    PsiElement insertedElement = insertedInfo.getSecond();
    final CompletionContext context = insertedInfo.getFirst();
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, context);
    final CompletionParametersImpl parameters = new CompletionParametersImpl(insertedElement, context.file);
    final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>((Collection)CompletionService.getCompletionService().
      getQueryFactory(myCompletionType, parameters).
      createQuery(parameters).findAll());
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, null);

    final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
    final LookupData data = new LookupData(items, context.getPrefix());
    data.itemPreferencePolicy = new CompletionPreferencePolicy(context.getPrefix(), parameters, myCompletionType);
    return data;
  }

  protected Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context) {
    PsiFile oldFileCopy = createFileCopy(context.file);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(oldFileCopy);
    Project project = hostFile.getProject();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    // is null in tests
    int hostStartOffset = injectedLanguageManager == null ? context.getStartOffset() : injectedLanguageManager.injectedToHost(oldFileCopy, TextRange.from(
      context.getStartOffset(), 0)).getStartOffset();
    Document document = oldFileCopy.getViewProvider().getDocument();

    document.insertString(context.getStartOffset(), CompletionUtil.DUMMY_IDENTIFIER);
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
    return LookupManager.getInstance(project)
      .showLookup(editor, items, prefix, data.itemPreferencePolicy, bottomText);
  }

  protected abstract boolean isAutocompleteOnInvocation();

  protected abstract boolean isAutocompleteCommonPrefixOnInvocation();

  protected void analyseItem(LookupItem item, PsiElement place, CompletionContext context) {}

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;

    LOG.assertTrue(lookupData.items.length == 0);
    if (lookupData.prefix != null) {
      HintManager.getInstance().showErrorHint(editor, appendSuggestion(CompletionBundle.message("completion.no.suggestions"), lookupData));
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
    return true;
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
