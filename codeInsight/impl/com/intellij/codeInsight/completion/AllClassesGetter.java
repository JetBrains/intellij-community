package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElementFactoryImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
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
  private static final InsertHandler INSERT_HANDLER = new InsertHandler() {

    public int _handleInsert(final InsertionContext context, final LookupItem item) {
      final Editor editor = context.getEditor();
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

      PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(element);
      if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
        ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(context.getProject()).getExpectedTypes((PsiExpression)prevElement.getParent(), true);
        boolean flag = true;
        for (ExpectedTypeInfo info : infos) {
          if (info.isArrayTypeInfo()) {
            flag = false;
            break;
          }
        }
        if (flag) {
          item.setAttribute(LookupItem.NEW_OBJECT_ATTR, "");
          item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, ""); //strange hack
        }
      }

      if (context.getFile().getLanguage() == StdLanguages.JAVA) {
        new DefaultInsertHandler().handleInsert(context, item);
        return editor.getCaretModel().getOffset();
      }

      final RangeMarker toDelete = DefaultInsertHandler.insertSpace(endOffset, document);
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);
      boolean insertFqn = true;
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, DefaultInsertHandler.resolveReference(psiReference))) {
          insertFqn = false;
        }
        else {
          try {
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                for (final PsiReference reference : psiElement.getReferences()) {
                  if (psiManager.areElementsEquivalent(psiClass, DefaultInsertHandler.resolveReference(reference))) {
                    insertFqn = false;
                    endOffset = reference.getRangeInElement().getEndOffset() + reference.getElement().getTextRange().getStartOffset();
                    break;
                  }
                }
              }
            }
          }
          catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (toDelete.isValid()) {
        document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        if (insertFqn) {
          endOffset = toDelete.getStartOffset();
        }
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
      return endOffset;
    }

    public void handleInsert(final InsertionContext context, final LookupElement item) {
      context.setAddCompletionChar(false);
      int endOffset = _handleInsert(context, (LookupItem)item);
      context.getEditor().getCaretModel().moveToOffset(endOffset);
      ((LookupItem)item).getTailType().processTail(context.getEditor(), endOffset);
    }

  };

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public void getClasses(final PsiElement context, CompletionResultSet set, boolean afterNew, final int offset,
                         final boolean filterByScope) {
    if(context == null || !context.isValid()) return;

    String packagePrefix = getPackagePrefix(context, offset);

    final PsiManager manager = context.getManager();
    final Set<String> qnames = new THashSet<String>();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiShortNamesCache cache = facade.getShortNamesCache();

    final GlobalSearchScope scope = filterByScope ? context.getContainingFile().getResolveScope() : GlobalSearchScope.allScope(context.getProject());
    final String[] names = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return cache.getAllClassNames();
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

    final PrefixMatcher matcher = set.getPrefixMatcher();
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
        if (isSuitable(context, packagePrefix, qnames, lookingForAnnotations, psiClass, filterByScope)) {
          set.addElement(createLookupItem(psiClass, afterNew));
        }
      }
    }
  }

  private static String getPackagePrefix(final PsiElement context, final int offset) {
    final String fileText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return context.getContainingFile().getText();
      }
    });
    int i = offset - 1;
    while (i >= 0) {
      final char c = fileText.charAt(i);
      if (!Character.isJavaIdentifierPart(c) && c != '.') break;
      i--;
    }
    String prefix = fileText.substring(i + 1, offset);
    final int j = prefix.lastIndexOf('.');
    return j > 0 ? prefix.substring(0, j) : "";
  }

  private boolean isSuitable(final PsiElement context, final String packagePrefix, final Set<String> qnames,
                             final boolean lookingForAnnotations,
                             final PsiClass psiClass,
                             final boolean filterByScope) {
    //noinspection AutoUnboxing
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (lookingForAnnotations && !psiClass.isAnnotationType()) return false;

        if (JavaCompletionUtil.isInExcludedPackage(psiClass)) return false;

        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(packagePrefix)) return false;

        if (!myFilter.isAcceptable(psiClass, context)) return false;

        if (!(psiClass instanceof PsiCompiledElement) || !filterByScope ||
            JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().isAccessible(psiClass, context, psiClass)) {
          return qnames.add(qualifiedName);
        }
        return false;

      }
    });
  }

  public static LookupItem<PsiClass> createLookupItem(final PsiClass psiClass, final boolean afterNew) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupItem<PsiClass>>() {
      public LookupItem<PsiClass> compute() {
        final LookupItem<PsiClass> item = LookupElementFactoryImpl.getInstance().createLookupElement(psiClass).setInsertHandler(INSERT_HANDLER);
        JavaAwareCompletionData.setShowFQN(item);
        return item;
      }
    });
  }

}
