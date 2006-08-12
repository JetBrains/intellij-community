package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.injected.EditorDelegate;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

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

  private LookupItemPreferencePolicy myPreferencePolicy = null;

  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!file.isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)) {
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

    EditorUtil
      .fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset1 =
      editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionStart() : editor.getCaretModel().getOffset();
    int offset2 = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionEnd() : offset1;
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

      EditorModificationUtil.deleteSelectedText(editor);

      if (!StringUtil.startsWithIgnoreCase(uniqueText, prefix)) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.camelHumps");
      }

      editor.getDocument().replaceString(offset1 - prefix.length(), offset1, uniqueText);
      final int offset = offset1 - prefix.length() + uniqueText.length();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();

      lookupItemSelected(context, startOffset, data, item, false, (char)0);
    }
    else {
      if (isAutocompleteCommonPrefixOnInvocation() && !doNotAutocomplete) {
        final String newPrefix = fillInCommonPrefix(items, prefix, editor);

        if (!newPrefix.equals(prefix)) {
          final int shift = newPrefix.length() - prefix.length();
          context.setPrefix(newPrefix);
          prefix = newPrefix;
          context.shiftOffsets(shift);
          //context.offset1 += shift;
          editor.getCaretModel().moveToOffset(context.startOffset + shift);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }

      PsiDocumentManager.getInstance(project).commitAllDocuments();
      final RangeMarker startOffsetMarker = editor.getDocument().createRangeMarker(startOffset, startOffset);
      final Lookup lookup = showLookup(project, editor, items, prefix, data, file);

      if (lookup != null) {
        lookup.putUserData(COMPLETION_HANDLER_CLASS_KEY, getClass());

        lookup.addLookupListener(new LookupAdapter() {
          public void itemSelected(LookupEvent event) {
            int shift = startOffsetMarker.getStartOffset() - startOffset;
            context.shiftOffsets(shift);
            context.startOffset += shift;
            LookupItem item = event.getItem();
            if (item != null) {
              lookupItemSelected(context, startOffsetMarker.getStartOffset(), data, item,
                                 settings.SHOW_SIGNATURES_IN_LOOKUPS || item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null,
                                 event.getCompletionChar());
            }
          }
        });
      }
    }
  }

  private static String fillInCommonPrefix(LookupItem[] items, final String prefix, final Editor editor) {
    String commonPrefix = null;
    boolean isStrict = false;

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
        if (commonPrefix.length() <= prefix.length()) {
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
    int lookupStart = offset - prefix.length();

    editor.getDocument().replaceString(lookupStart, lookupStart + prefix.length(), commonPrefix);

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
      return;
    }

    if (lastElement instanceof PsiIdentifier) {
      final PsiElement parent = lastElement.getParent();
      if (parent instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)parent;
        if (lastElement.equals(psiClass.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeClassName(lookupSet, context, psiClass);
          return;
        }
      }

      if (parent instanceof PsiLocalVariable || parent instanceof PsiParameter) {
        final PsiVariable variable = (PsiVariable)parent;
        if (lastElement.equals(variable.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeLocalVariableName(lookupSet, context, variable);
          return;
        }
      }

      if (parent instanceof PsiField) {
        final PsiVariable variable = (PsiVariable)parent;
        if (lastElement.equals(variable.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeFieldName(lookupSet, context, variable);
          if (parent.getLastChild() instanceof PsiErrorElement) return;
          myPreferencePolicy = completionData.completeMethodName(lookupSet, context, variable);
        }
      }

      if (parent instanceof PsiMethod) {
        final PsiMethod psiMethod = (PsiMethod)parent;
        if (lastElement.equals(psiMethod.getNameIdentifier())) {
          myPreferencePolicy = completionData.completeMethodName(lookupSet, context, psiMethod);
        }
      }
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

    context.setPrefix(findPrefix(insertedElement, context.startOffset, CompletionUtil.DUMMY_IDENTIFIER, completionData));
    if (completionData == null) {
      // some completion data may depend on prefix
      completionData = getCompletionData(context, lastElement);
    }

    if (completionData == null) return new LookupData(new LookupItem[0], context.getPrefix());

    final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
    complete(context, insertedElement, completionData, lookupSet);
    if (lookupSet.size() == 0 || !CodeInsightUtil.isAntFile(file)) {
      final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
      completionData.addKeywordVariants(keywordVariants, context, insertedElement);
      CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
      CompletionUtil.highlightMembersOfContainer(lookupSet);
    }

    final LookupItem[] items = lookupSet.toArray(new LookupItem[lookupSet.size()]);
    final LookupData data = new LookupData(items, context.getPrefix());
    if (myPreferencePolicy == null) {
      myPreferencePolicy = new CompletionPreferencePolicy(manager, items, null, context.getPrefix());
    }
    data.itemPreferencePolicy = myPreferencePolicy;
    myPreferencePolicy = null;
    return data;
  }

  private static Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context) {
    final PsiFile fileCopy = createFileCopy(context.file);
    Document oldDoc = fileCopy.getViewProvider().getDocument();
    oldDoc.insertString(context.startOffset, CompletionUtil.DUMMY_IDENTIFIER);
    PsiDocumentManager.getInstance(fileCopy.getProject()).commitDocument(oldDoc);
    context.offset = context.startOffset;

    Editor oldEditor = context.editor;
    Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguage(oldEditor, fileCopy, context.startOffset);
    if (injectedEditor != oldEditor) {
      // newly inserted identifier can well end up in the injected language region
      final EditorDelegate editorDelegate = (EditorDelegate)injectedEditor;
      int newOffset1 =
        editorDelegate.logicalPositionToOffset(editorDelegate.parentToInjected(oldEditor.offsetToLogicalPosition(context.startOffset)));
      int newOffset2 = editorDelegate
        .logicalPositionToOffset(editorDelegate.parentToInjected(oldEditor.offsetToLogicalPosition(context.selectionEndOffset)));
      PsiFile injectedFile = editorDelegate.getInjectedFile();
      CompletionContext newContext = new CompletionContext(context.project, injectedEditor, injectedFile, newOffset1, newOffset2);
      newContext.offset = newContext.startOffset;
      PsiElement element = injectedFile.findElementAt(newContext.startOffset);
      return Pair.create(newContext, element);
    }
    PsiElement element;
    if (CodeInsightUtil.isAntFile(fileCopy)) {
      //need xml element but ant reference
      //TODO: need a better way of handling this
      final ASTNode fileNode = fileCopy.getViewProvider().getPsi(StdLanguages.XML).getNode();
      assert fileNode != null;
      element = fileNode.findLeafElementAt(context.startOffset).getPsi();
    }
    else {
      element = fileCopy.findElementAt(context.startOffset);
    }
    return Pair.create(context, element);
  }


  protected Lookup showLookup(Project project,
                              final Editor editor,
                              final LookupItem[] items,
                              String prefix,
                              final LookupData data,
                              PsiFile file) {
    return LookupManager.getInstance(project)
      .showLookup(editor, items, prefix, data.itemPreferencePolicy, new DefaultCharFilter(file, editor.getCaretModel().getOffset()) {
        public int accept(char c) {
          switch (c) {
            case'<':
            case'>':
            case'[':
              return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
            default:
              return super.accept(c);
          }
        }
      });
  }

  protected abstract boolean isAutocompleteOnInvocation();

  protected abstract boolean isAutocompleteCommonPrefixOnInvocation();

  protected abstract void analyseItem(LookupItem item, PsiElement place, CompletionContext context);

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Project project = context.project;
    Editor editor = context.editor;

    LOG.assertTrue(lookupData.items.length == 0);
    if (lookupData.prefix == null) {
//      Toolkit.getDefaultToolkit().beep();
    }
    else {
      HintManager.getInstance().showErrorHint(editor, CompletionBundle.message("completion.no.suggestions"));
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

  static String findPrefix(PsiElement insertedElement, int offset, String dummyIdentifier, CompletionData completionData) {
    final String result = completionData == null
                          ? CompletionData.findPrefixStatic(insertedElement, offset)
                          : completionData.findPrefix(insertedElement, offset);

    return result.endsWith(dummyIdentifier) ? result.substring(0, result.length() - dummyIdentifier.length()) : result;
  }

  public boolean startInWriteAction() {
    return true;
  }

  protected static PsiFile createFileCopy(PsiFile file) {
    final PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public void visitClass(PsiClass aClass) {
        aClass.putCopyableUserData(PsiUtil.ORIGINAL_KEY, aClass);
        super.visitClass(aClass);
      }

      public void visitVariable(PsiVariable variable) {
        variable.putCopyableUserData(PsiUtil.ORIGINAL_KEY, variable);
        super.visitVariable(variable);
      }

      public void visitMethod(PsiMethod method) {
        method.putCopyableUserData(PsiUtil.ORIGINAL_KEY, method);
        super.visitMethod(method);
      }

      public void visitXmlTag(XmlTag tag) {
        tag.putCopyableUserData(PsiUtil.ORIGINAL_KEY, tag);
        super.visitXmlTag(tag);
      }
    };

    visitor.visitFile(file);
    final PsiFile fileCopy = (PsiFile)file.copy();

    final PsiElementVisitor copyVisitor = new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      public void visitClass(PsiClass aClass) {
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
}