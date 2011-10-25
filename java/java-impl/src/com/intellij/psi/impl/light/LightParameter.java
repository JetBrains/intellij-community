package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LightParameter extends LightVariableBuilder<LightVariableBuilder> implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];
  private final String myName;
  private final PsiElement myDeclarationScope;
  private final boolean myVarArgs;

  public LightParameter(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    this(name, type, declarationScope, language, false);
  }

  public LightParameter(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language, boolean isVarArgs) {
    super(declarationScope.getManager(), name, type, language);
    myName = name;
    myDeclarationScope = declarationScope;
    myVarArgs = isVarArgs;
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
  }

  public String toString() {
    return "Light Parameter";
  }

  @Override
  public boolean isVarArgs() {
    return myVarArgs;
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

}
