package com.intellij.codeInsight.completion.methodChains.completion.lookup.sub;

import com.intellij.codeInsight.completion.methodChains.completion.lookup.ChainCompletionLookupElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class StaticMethodSubLookupElement implements SubLookupElement {

  private final PsiMethod myMethod;
  private final TIntObjectHashMap<SubLookupElement> myReplaceElements;

  public StaticMethodSubLookupElement(final PsiMethod method, @Nullable final TIntObjectHashMap<SubLookupElement> replaceElements) {
    assert method.hasModifierProperty(PsiModifier.STATIC);
    myReplaceElements = replaceElements;
    myMethod = method;
  }

  @Override
  public void doImport(final PsiJavaFile javaFile) {
    final PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      if (javaFile.findImportReferenceTo(containingClass) == null) {
        javaFile.importClass(containingClass);
      }
    }
    if (myReplaceElements != null) {
      myReplaceElements.forEachValue(new TObjectProcedure<SubLookupElement>() {
        @Override
        public boolean execute(final SubLookupElement subLookupElement) {
          subLookupElement.doImport(javaFile);
          return false;
        }
      });
    }
  }

  @Override
  public String getInsertString() {
    //noinspection ConstantConditions
    return String.format("%s.%s(%s)", myMethod.getContainingClass().getName(), myMethod.getName(),
                         ChainCompletionLookupElementUtil.fillMethodParameters(myMethod, myReplaceElements));
  }
}
