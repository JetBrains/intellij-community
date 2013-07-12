package com.intellij.codeInsight.completion.methodChains.completion.context;

import com.intellij.codeInsight.completion.methodChains.completion.lookup.sub.StaticMethodSubLookupElement;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.sub.SubLookupElement;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.sub.VariableSubLookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ContextRelevantStaticMethod {
  private final PsiMethod psiMethod;
  @Nullable
  private final TIntObjectHashMap<SubLookupElement> parameters;

  public ContextRelevantStaticMethod(final PsiMethod psiMethod, @Nullable final TIntObjectHashMap<PsiVariable> parameters) {
    this.psiMethod = psiMethod;
    if (parameters == null) {
      this.parameters = null;
    } else {
      this.parameters = new TIntObjectHashMap<SubLookupElement>(parameters.size());
      parameters.forEachEntry(new TIntObjectProcedure<PsiVariable>() {
        @SuppressWarnings("ConstantConditions")
        @Override
        public boolean execute(final int pos, final PsiVariable var) {
          ContextRelevantStaticMethod.this.parameters.put(pos, new VariableSubLookupElement(var));
          return false;
        }
      });
    }
  }

  private SubLookupElement cachedLookupElement;

  public SubLookupElement createLookupElement() {
    if (cachedLookupElement == null) {
      cachedLookupElement = new StaticMethodSubLookupElement(psiMethod, parameters);
    }
    return cachedLookupElement;
  }
}
