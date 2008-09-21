package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
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
  private final ElementFilter myFilter;
  private static final InsertHandler<JavaPsiClassReferenceElement> INSERT_HANDLER = new InsertHandler<JavaPsiClassReferenceElement>() {

    private void _handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      final Editor editor = context.getEditor();
      final PsiClass psiClass = item.getObject();
      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return;

      if (endOffset == 0) return;

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(psiClass.getProject());
      final PsiFile file = context.getFile();
      if (file.findElementAt(endOffset - 1) == null) return;

      if (file.getLanguage() == StdLanguages.JAVA) {
        final OffsetKey key = OffsetKey.create("endOffset");
        context.getOffsetMap().addOffset(key, endOffset - 1);
        JavaPsiClassReferenceElement.JAVA_CLASS_INSERT_HANDLER.handleInsert(context, item);
        endOffset = context.getOffsetMap().getOffset(key) + 1;
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
      }
    }

    public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      context.setAddCompletionChar(false);
      _handleInsert(context, item);
      item.getTailType().processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
    }

  };

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public void getClasses(final PsiElement context, CompletionResultSet set, final int offset, final boolean filterByScope) {
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
          set.addElement(createLookupItem(psiClass));
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

  public static LookupItem<PsiClass> createLookupItem(final PsiClass psiClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupItem<PsiClass>>() {
      public LookupItem compute() {
        return new JavaPsiClassReferenceElement(psiClass).setInsertHandler(INSERT_HANDLER);
      }
    });
  }

}
