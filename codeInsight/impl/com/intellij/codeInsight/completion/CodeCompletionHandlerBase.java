package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @see #notifyAll()
 */
abstract class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private static final Key<Class<? extends CodeCompletionHandlerBase>> COMPLETION_HANDLER_CLASS_KEY =
    Key.create("COMPLETION_HANDLER_CLASS_KEY");

  protected LookupItemPreferencePolicy myPreferencePolicy = null;
  protected boolean mySuggestSmartCompletion = false;
  protected boolean mySuggestClassNameCompletion = false;

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
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    ShowAutoImportPass.autoImportReferenceAtCursor(editor, file); //let autoimport complete

    final int offset1 = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionStart() : editor.getCaretModel().getOffset();
    final int offset2 = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionEnd() : offset1;
    final CompletionContext context = new CompletionContext(project, editor, file, offset1, offset2);

    final LookupData data = getLookupData(context);
    final LookupItem[] items = data.items;
    String prefix = data.prefix;
    context.setPrefix(data.prefix);
    if (items.length == 0) {
      handleEmptyLookup(context, data);
      return;
    }
    final int startOffset = offset1 - prefix.length();

    String uniqueText = null;
    LookupItem item = null;
    boolean doNotAutocomplete = false;
    boolean signatureSensitive = false;

    for (final LookupItem item1 : items) {
      if (item1.getAttribute(LookupItem.DO_NOT_AUTOCOMPLETE_ATTR) != null) {
        item = null;
        doNotAutocomplete = true;
        break;
      }

      if (item1.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null) {
        signatureSensitive = true;
      }
      if (uniqueText == null) {
        uniqueText = item1.getLookupString();
        item = item1;
      }
      else {
        if (!uniqueText.equals(item1.getLookupString())) {
          item = null;
          break;
        }
        if (item.getObject() instanceof PsiMethod && item1.getObject() instanceof PsiMethod) {
          if (!signatureSensitive) {
            final PsiParameter[] parms = ((PsiMethod)item1.getObject()).getParameterList().getParameters();
            if (parms.length > 0) {
              item = item1;
            }
          }
          else {
            item = null;
            break;
          }
        }
        else {
          item = null;
          break;
        }
      }
    }

    if (item != null) {
      if (!isAutocompleteOnInvocation() && item.getAttribute(LookupItem.DO_AUTOCOMPLETE_ATTR) == null) {
        item = null;
      }
    }
    if (item != null && context.identifierEndOffset != context.selectionEndOffset) { // give a chance to use Tab
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
        context.startOffset -= prefix.length();
        data.prefix = "";
        context.setPrefix(""); // prefix may be of no interest
      }

      if (!StringUtil.startsWithIgnoreCase(uniqueText, prefix)) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
      }

      insertLookupString(context, offset2, uniqueText, startOffset);
      context.editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      lookupItemSelected(context, startOffset, data, item, false, (char)0);
    }
    else {
      if (isAutocompleteCommonPrefixOnInvocation() && !doNotAutocomplete) {
        final int offset = context.startOffset;
        final String newPrefix = fillInCommonPrefix(items, prefix, context);

        if (!newPrefix.equals(prefix)) {
          final int shift = newPrefix.length() - prefix.length();
          context.setPrefix(newPrefix);
          prefix = newPrefix;
          context.shiftOffsets(shift);
          //context.offset1 += shift;
          editor.getCaretModel().moveToOffset(offset + shift);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments();
      final RangeMarker startOffsetMarker = document.createRangeMarker(startOffset, startOffset);
      final Lookup lookup = showLookup(project, editor, items, prefix, data, file, appendSuggestion(null));
      
      if (lookup != null) {
        lookup.putUserData(COMPLETION_HANDLER_CLASS_KEY, getClass());

        lookup.addLookupListener(new LookupAdapter() {
          public void itemSelected(LookupEvent event) {
            LookupItem item = event.getItem();
            if (item == null) return;

            selectLookupItem(item, settings.SHOW_SIGNATURES_IN_LOOKUPS || item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null,
                             event.getCompletionChar(), startOffsetMarker.getStartOffset(), context, data);
            
          }
        });
      }
    }
  }

  @Nullable protected String appendSuggestion(@Nullable String s) {
    String suggestion = null;
    if (mySuggestSmartCompletion) {
      suggestion = CompletionBundle.message("completion.smart.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SMART_TYPE_COMPLETION)));
    } else if (mySuggestClassNameCompletion) {
      suggestion = CompletionBundle.message("completion.class.name.hint", KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CLASS_NAME_COMPLETION)));
    }
    if (s == null) return suggestion;
    if (suggestion == null) return s;
    return s + "; " + suggestion;
  }

  private static void insertLookupString(final CompletionContext context, final int currentOffset, final String newText,
                                         final int startOffset) {
    final Editor editor = context.editor;
    editor.getDocument().replaceString(startOffset, currentOffset, newText);
    context.shiftOffsets(startOffset + newText.length() - currentOffset);
    editor.getCaretModel().moveToOffset(startOffset + newText.length());
    editor.getSelectionModel().removeSelection();
  }

  protected final void selectLookupItem(final LookupItem item,
                                  final boolean signatureSelected,
                                  final char completionChar,
                                  final int startOffset,
                                  final CompletionContext context, final LookupData data) {
    context.shiftOffsets(context.editor.getCaretModel().getOffset() - context.offset);
    context.shiftOffsets(startOffset - (context.startOffset - context.getPrefix().length()));
    insertLookupString(context, context.editor.getCaretModel().getOffset(), item.getLookupString(), startOffset);
    final int caretOffset = context.editor.getCaretModel().getOffset();
    final int previousSelectionEndOffset = context.selectionEndOffset;

    context.selectionEndOffset = caretOffset;
    context.identifierEndOffset =
      CompletionUtil.isOverwrite(item, completionChar) && previousSelectionEndOffset == context.identifierEndOffset ?
      caretOffset:Math.max(caretOffset, context.identifierEndOffset);
    lookupItemSelected(context, startOffset, data, item, signatureSelected, completionChar);
  }

  private static String fillInCommonPrefix(LookupItem[] items, final String prefix, CompletionContext context) {
    final Editor editor = context.editor;
    String commonPrefix = null;
    boolean isStrict = false;

    final int prefixLength = prefix.length();
    for (final LookupItem item : items) {
      final String lookupString = item.getLookupString();
      if (!StringUtil.startsWithIgnoreCase(lookupString, prefix)) {
        // since camel humps
        return prefix;
        //throw new RuntimeException("Hmm... Some lookup items have other than $prefix prefix.");
      }

      //final String afterPrefix = lookupString.substring(prefix.length());

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

      for(;
          replacedLength < commonPrefixLength &&
          lookupStart + replacedLength < sequenceLength &&
          sequence.charAt(lookupStart + replacedLength) == commonPrefix.charAt(replacedLength)
          ;
          ++replacedLength
      ) {
        context.selectionEndOffset++;
        context.startOffset++;
        context.offset++;
        context.shiftOffsets(-1); // compensation of next adjustment
      }
    }
    
    editor.getDocument().replaceString(lookupStart, lookupStart + replacedLength, commonPrefix);

    return commonPrefix;
  }

  protected abstract CompletionData getCompletionData(CompletionContext context, PsiElement element);

  private void complete(final CompletionContext context,
                        final PsiElement lastElement,
                        final CompletionData completionData,
                        final Set<LookupItem> lookupSet) {
    if (lastElement == null) return;
    final PsiReference ref = lastElement.getContainingFile().findReferenceAt(context.offset);
    if (ref != null) {
      completionData.completeReference(ref, lookupSet, context, lastElement);
    }
  }

  protected LookupData getLookupData(final CompletionContext _context) {
    final PsiFile file = _context.file;
    final PsiManager manager = file.getManager();
    final PsiElement lastElement = file.findElementAt(_context.startOffset - 1);

    final Pair<CompletionContext, PsiElement> insertedInfo =
      ApplicationManager.getApplication().runWriteAction(new Computable<Pair<CompletionContext, PsiElement>>() {
        public Pair<CompletionContext, PsiElement> compute() {
          return insertDummyIdentifier(_context);
        }
      });

    PsiElement insertedElement = insertedInfo.getSecond();
    final CompletionContext context = insertedInfo.getFirst();

    CompletionData completionData = getCompletionData(context, lastElement);

    context.setPrefix(insertedElement, context.startOffset, completionData);
    if (completionData == null) {
      // some completion data may depend on prefix
      completionData = getCompletionData(context, lastElement);
    }

    mySuggestSmartCompletion = shouldSuggestSmartCompletion(insertedElement, context);

    if (completionData == null) return new LookupData(LookupItem.EMPTY_ARRAY, context.getPrefix());

    final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
    complete(context, insertedElement, completionData, lookupSet);
    if (lookupSet.isEmpty() || !CodeInsightUtil.isAntFile(file)) {
      final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
      completionData.addKeywordVariants(keywordVariants, context, insertedElement);
      CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
      CompletionUtil.highlightMembersOfContainer(lookupSet);
    }

    final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
    final LookupData data = new LookupData(items, context.getPrefix());
    if (myPreferencePolicy == null) {
      myPreferencePolicy = new CompletionPreferencePolicy(manager, items, null, context.getPrefix(), insertedElement);
    }
    data.itemPreferencePolicy = myPreferencePolicy;
    myPreferencePolicy = null;
    return data;
  }

  protected Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context) {
    PsiFile oldFileCopy = createFileCopy(context.file);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(oldFileCopy);
    Project project = hostFile.getProject();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    // is null in tests
    int hostStartOffset = injectedLanguageManager == null ? context.startOffset : injectedLanguageManager.injectedToHost(oldFileCopy, TextRange.from(context.startOffset, 0)).getStartOffset();
    Document document = oldFileCopy.getViewProvider().getDocument();

    document.insertString(context.startOffset, CompletionUtil.DUMMY_IDENTIFIER);
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile fileCopy = InjectedLanguageUtil.findInjectedPsiAt(hostFile, hostStartOffset);
    if (fileCopy == null) {
      PsiElement elementAfterCommit = findElementAt(hostFile, hostStartOffset);
      fileCopy = elementAfterCommit == null ? oldFileCopy : elementAfterCommit.getContainingFile();
    }

    context.offset = context.startOffset;

    if (oldFileCopy != fileCopy) {
      // newly inserted identifier can well end up in the injected language region
      Editor oldEditor = context.editor;
      Editor editor = EditorFactory.getInstance().createEditor(document, project);
      Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguage(editor, hostFile, context.startOffset);
      if (newEditor instanceof EditorWindow) {
        EditorWindow injectedEditor = (EditorWindow)newEditor;
        int newOffset1 = injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(oldEditor.offsetToLogicalPosition(context.startOffset)));
        int newOffset2 = injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(oldEditor.offsetToLogicalPosition(context.selectionEndOffset)));
        PsiFile injectedFile = injectedEditor.getInjectedFile();
        CompletionContext newContext = new CompletionContext(context.project, injectedEditor, injectedFile, newOffset1, newOffset2);
        newContext.offset = newContext.startOffset;
        PsiElement element = findElementAt(injectedFile, newContext.startOffset);
        if (element == null) {
          LOG.assertTrue(false, "offset " + newContext.startOffset + " at:\n" + injectedFile.getText());
        }
        EditorFactory.getInstance().releaseEditor(editor);
        return Pair.create(newContext, element);
      }
      EditorFactory.getInstance().releaseEditor(editor);
    }
    PsiElement element = findElementAt(fileCopy, context.startOffset);
    if (element == null) {
      LOG.assertTrue(false, "offset " + context.startOffset + " at:\n" + fileCopy.getText());
    }
    return Pair.create(context, element);
  }

  private static PsiElement findElementAt(final PsiFile fileCopy, int startOffset) {
    final PsiElement element;
    if (CodeInsightUtil.isAntFile(fileCopy)) {
      //need xml element but ant reference
      //TODO: need a better way of handling this
      final ASTNode fileNode = fileCopy.getViewProvider().getPsi(StdLanguages.XML).getNode();
      assert fileNode != null;
      element = fileNode.findLeafElementAt(startOffset).getPsi();
    }
    else {
      element = fileCopy.findElementAt(startOffset);
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
      .showLookup(editor, items, prefix, data.itemPreferencePolicy, new DefaultCharFilter(editor, file, editor.getCaretModel().getOffset()), bottomText);
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
      HintManager.getInstance().showErrorHint(editor, appendSuggestion(CompletionBundle.message("completion.no.suggestions")));
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private void lookupItemSelected(final CompletionContext context,
                                  final int startOffset,
                                  final LookupData data,
                                  @NotNull final LookupItem item,
                                  final boolean signatureSelected,
                                  final char completionChar) {
    final InsertHandler handler = item.getInsertHandler() != null ? item.getInsertHandler() : new DefaultInsertHandler();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(context.project).commitAllDocuments();
        context.setPrefix(data.prefix);
        final PsiElement position =
          context.file.findElementAt(context.startOffset - context.getPrefix().length() + item.getLookupString().length() - 1);
        analyseItem(item, position, context);
        handler.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
      }
    });
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected PsiFile createFileCopy(PsiFile file) {
    if (file instanceof PsiJavaFile) {
      final JavaElementVisitor visitor = new JavaRecursiveElementVisitor() {
        @Override public void visitClass(PsiClass aClass) {
          aClass.putCopyableUserData(PsiUtil.ORIGINAL_KEY, aClass);
          super.visitClass(aClass);
        }

        @Override public void visitVariable(PsiVariable variable) {
          variable.putCopyableUserData(PsiUtil.ORIGINAL_KEY, variable);
          super.visitVariable(variable);
        }

        @Override public void visitMethod(PsiMethod method) {
          method.putCopyableUserData(PsiUtil.ORIGINAL_KEY, method);
          super.visitMethod(method);
        }
      };
      visitor.visitFile(file);
    }

    if (file instanceof XmlFile) {
      final XmlElementVisitor xmlVisitor = new XmlRecursiveElementVisitor() {
        @Override public void visitXmlTag(XmlTag tag) {
          tag.putCopyableUserData(PsiUtil.ORIGINAL_KEY, tag);
          super.visitXmlTag(tag);
        }
      };
      xmlVisitor.visitFile(file);
    }

    final PsiFile fileCopy = (PsiFile)file.copy();

    final PsiElementVisitor copyVisitor = new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override public void visitClass(PsiClass aClass) {
        final PsiElement originalElement = aClass.getCopyableUserData(PsiUtil.ORIGINAL_KEY);
        if (originalElement != null) {
          originalElement.putCopyableUserData(PsiUtil.ORIGINAL_KEY, null);
          originalElement.putUserData(CompletionUtil.COPY_KEY, aClass);
          aClass.putCopyableUserData(PsiUtil.ORIGINAL_KEY, null);
          aClass.putUserData(PsiUtil.ORIGINAL_KEY, originalElement);
        }
        super.visitClass(aClass);
      }
    };
    copyVisitor.visitFile(fileCopy);
    return fileCopy;
  }

  protected static boolean shouldSuggestSmartCompletion(final PsiElement element, CompletionContext context) {
    if (shouldSuggestClassNameCompletion(element, context)) return false;

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifier() != null) return false;

    return new ExpectedTypesGetter().get(element, null).length > 0;
  }

  protected static boolean shouldSuggestClassNameCompletion(final PsiElement element, CompletionContext context) {
    if (StringUtil.isEmpty(context.getPrefix())) return false;
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    if (parent == null) return false;
    return parent.getParent() instanceof PsiTypeElement || parent.getParent() instanceof PsiExpressionStatement || parent.getParent() instanceof PsiReferenceList;
  }
}
