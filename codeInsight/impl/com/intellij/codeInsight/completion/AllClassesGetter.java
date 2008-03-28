package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.12.2003
 * Time: 16:49:25
 * To change this template use Options | File Templates.
 */
public class AllClassesGetter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.AllClassesGetter");

  private final ElementFilter myFilter;
  private static final SimpleInsertHandler INSERT_HANDLER = new SimpleInsertHandler() {
    public int handleInsert(final Editor editor, final int startOffset, final LookupElement item, final LookupElement[] allItems, final TailType tailType) {
      final PsiClass psiClass = (PsiClass)item.getObject();
      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return endOffset;

      if (endOffset == 0) return endOffset;

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(editor.getProject());
      final PsiFile file = psiDocumentManager.getPsiFile(document);
      final PsiElement element = file.findElementAt(endOffset - 1);
      if (element == null) return endOffset;


      final RangeMarker toDelete = DefaultInsertHandler.insertSpace(endOffset, document);
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);
      boolean insertFqn = true;
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, resolveReference(psiReference))) {
          insertFqn = false;
        }
        else {
          try {
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                endOffset = psiElement.getTextRange().getEndOffset();
              }
              insertFqn = false;
            }
          }
          catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (toDelete.isValid()) {
        document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
      }

      if (insertFqn) {
        int i = endOffset - 1;
        while (i >= 0) {
          final char ch = document.getCharsSequence().charAt(i);
          if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
          i--;
        }
        document.replaceString(i + 1, endOffset, qname);
        endOffset = i + 1 + qname.length();
      }

      if (tailType == TailTypes.SMART_COMPLETION) {
        document.insertString(endOffset, "(");
        endOffset++;
      }
      editor.getCaretModel().moveToOffset(endOffset);

      //todo[peter] hack, to deal with later
      if (psiClass.isAnnotationType()) {
        // Check if someone inserts annotation class that require @
        psiDocumentManager.commitAllDocuments();
        PsiElement elementAt = file.findElementAt(startOffset);
        final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

        if (elementAt instanceof PsiIdentifier &&
            ( PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null || //we are inserting '@' only in annotation parameters
              (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile) // top level annotation without @
            )
            && isAtTokenNeeded(editor, startOffset)) {
          PsiElement parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, PsiCodeBlock.class);
          if (parent == null && parentElement instanceof PsiErrorElement) {
            PsiElement nextElement = parentElement.getNextSibling();
            if (nextElement instanceof PsiWhiteSpace) nextElement = nextElement.getNextSibling();
            if (nextElement instanceof PsiClass) parent = nextElement;
          }

          if (parent instanceof PsiModifierListOwner) {
            document.insertString(elementAt.getTextRange().getStartOffset(), "@");
            endOffset++;
            editor.getCaretModel().moveToOffset(endOffset);
          }
        }
      }

      return endOffset;
    }

    private boolean isAtTokenNeeded(Editor editor, int startOffset) {
      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(startOffset);
      LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
      iterator.retreat();
      if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
      return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
    }

  };
  private static final TailType PARENS_WITH_PARAMS = new TailType() {
    public int processTail(final Editor editor, final int tailOffset) {
      final int offset = editor.getCaretModel().getOffset();
      if (!editor.getDocument().getText().substring(offset).startsWith("()")) {
        editor.getDocument().insertString(offset, "()");
      }
      editor.getCaretModel().moveToOffset(offset + 1);
      return offset + 1;
    }
  };
  private static final TailType PARENS_NO_PARAMS = new TailType() {
    public int processTail(final Editor editor, final int tailOffset) {
      final int offset = editor.getCaretModel().getOffset();
      if (!editor.getDocument().getText().substring(offset).startsWith("()")) {
        editor.getDocument().insertString(offset, "()");
      }
      editor.getCaretModel().moveToOffset(offset + 2);
      return offset + 2;
    }
  };

  private static PsiElement resolveReference(final PsiReference psiReference) {
    if (psiReference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)psiReference).multiResolve(true);
      if (results.length == 1) return results[0].getElement();
    }
    return psiReference.resolve();
  }

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public void getClasses(final PsiElement context, final CompletionContext completionContext, CompletionResultSet<LookupElement> set, boolean afterNew) {
    if(context == null || !context.isValid()) return;

    String prefix = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return context.getText().substring(0, completionContext.getStartOffset() - context.getTextRange().getStartOffset());
      }
    });
    final int i = prefix.lastIndexOf('.');
    String packagePrefix = "";
    if (i > 0) {
      packagePrefix = prefix.substring(0, i);
    }

    final PsiManager manager = context.getManager();
    final Set<String> qnames = new THashSet<String>();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiShortNamesCache cache = facade.getShortNamesCache();

    final GlobalSearchScope scope = context.getContainingFile().getResolveScope();
    final String[] names = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return cache.getAllClassNames(true);
      }
    });
    Arrays.sort(names, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareToIgnoreCase(o2);
      }
    });

    boolean lookingForAnnotations = false;
    final PsiElement prevSibling = context.getParent().getPrevSibling();
    if (prevSibling instanceof PsiJavaToken && ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.AT) {
      lookingForAnnotations = true;
    }

    final CamelHumpMatcher matcher = new CamelHumpMatcher(completionContext.getPrefix());
    for (final String name : names) {
      if (!matcher.prefixMatches(name)) continue;

      ProgressManager.getInstance().checkCanceled();
      final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
        public PsiClass[] compute() {
          return cache.getClassesByName(name, scope);
        }
      });
      for (PsiClass psiClass : classes) {
        ProgressManager.getInstance().checkCanceled();
        if (isSuitable(context, packagePrefix, qnames, lookingForAnnotations, psiClass)) {
          set.addElement(createLookupItem(psiClass, afterNew));
        }
      }
    }
  }

  private boolean isSuitable(final PsiElement context, final String packagePrefix, final Set<String> qnames,
                             final boolean lookingForAnnotations,
                             final PsiClass psiClass) {
    //noinspection AutoUnboxing
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (lookingForAnnotations && !psiClass.isAnnotationType()) return false;

        if (JavaCompletionUtil.isInExcludedPackage(psiClass)) return false;

        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(packagePrefix)) return false;

        if (!myFilter.isAcceptable(psiClass, context)) return false;

        if (!(psiClass instanceof PsiCompiledElement) &&
            !JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().isAccessible(psiClass, context, null)) return false;

        return qnames.add(qualifiedName);
      }
    });
  }

  public static LookupItem<PsiClass> createLookupItem(final PsiClass psiClass, final boolean afterNew) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupItem<PsiClass>>() {
      public LookupItem<PsiClass> compute() {
        final LookupItem<PsiClass> item = LookupElementFactoryImpl.getInstance().createLookupElement(psiClass).setInsertHandler(INSERT_HANDLER);
        JavaAwareCompletionData.setShowFQN(item);
        if (afterNew) {
          item.setTailType(hasParams(psiClass) ? PARENS_WITH_PARAMS : PARENS_NO_PARAMS);
        }
        return item;
      }
    });
  }

  private static boolean hasParams(PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getConstructors()) {
      if (method.getParameterList().getParameters().length > 0) return true;
    }
    return false;
  }

}
