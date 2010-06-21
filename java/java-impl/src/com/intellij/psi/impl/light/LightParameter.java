package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LightParameter extends LightVariableBase implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];
  private final String myName;

  public LightParameter(PsiManager manager, @NotNull String name, PsiIdentifier nameIdentifier, @NotNull PsiType type, PsiElement scope) {
    super(manager, nameIdentifier, type, false, scope);
    myName = name;
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
