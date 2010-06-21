package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class LightParameterListBuilder extends LightElement implements PsiParameterList {
  private final List<LightParameter> myParameters = Collections.synchronizedList(new ArrayList<LightParameter>());

  public LightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public void addParameter(LightParameter parameter) {
    myParameters.add(parameter);
  }

  @Override
  public String toString() {
    return "Light parameter lsit";
  }

  @NotNull
  @Override
  public PsiParameter[] getParameters() {
    return myParameters.toArray(new LightParameter[myParameters.size()]);
  }

  @Override
  public int getParameterIndex(PsiParameter parameter) {
    return myParameters.indexOf(parameter);
  }

  @Override
  public int getParametersCount() {
    return myParameters.size();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitParameterList(this);
    }
  }

}
