package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.SuperParentFilter;

/**
 * @author ven
 */
public class Java15CompletionData extends JavaCompletionData {

  protected void initVariantsInFileScope() {
    super.initVariantsInFileScope();
    //static keyword in static import
    {
      final CompletionVariant variant = new CompletionVariant(PsiImportList.class, new LeftNeighbour(new TextFilter (PsiKeyword.IMPORT)));
      variant.addCompletion(PsiKeyword.STATIC, TailType.SPACE);

      registerVariant(variant);
    }

    {
      final ElementFilter position = new AndFilter(new LeftNeighbour(new TextFilter("@")),
                                                   new NotFilter(new SuperParentFilter(
                                                     new OrFilter(new ClassFilter(PsiNameValuePair.class),
                                                         new ClassFilter(PsiParameterList.class))))
                                                   );

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.INTERFACE, TailType.SPACE);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, CLASS_START);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.ENUM, TailType.SPACE);
      registerVariant(variant);
    }

  }

  protected void initVariantsInClassScope() {
    super.initVariantsInClassScope();
    {
      //Completion of "extends" & "super" inside wildcards
      final CompletionVariant variant = new CompletionVariant(JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN);
      variant.includeScopeClass(PsiVariable.class, true);
      variant.includeScopeClass(PsiExpressionStatement.class, true);
      variant.addCompletion(PsiKeyword.SUPER, TailType.SPACE);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.SPACE);
      registerVariant(variant);
    }
  }
}
