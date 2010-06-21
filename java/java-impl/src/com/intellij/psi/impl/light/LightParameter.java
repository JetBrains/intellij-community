package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LightParameter extends LightVariableBuilder implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];
  private final String myName;
  private final PsiElement myDeclarationScope;

  public LightParameter(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope) {
    super(declarationScope.getManager(), name, type);
    myName = name;
    myDeclarationScope = declarationScope;
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
  }

  public String toString() {
    return "Light Parameter";
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @NotNull
  public String getName() {
    return myName;
  }

}
