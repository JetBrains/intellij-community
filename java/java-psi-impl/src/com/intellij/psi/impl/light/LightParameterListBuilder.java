package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class LightParameterListBuilder extends LightElement implements PsiParameterList {
  private final List<PsiParameter> myParameters = new ArrayList<PsiParameter>();
  private PsiParameter[] myCachedParameters;

  public LightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  public void addParameter(PsiParameter parameter) {
    myParameters.add(parameter);
    myCachedParameters = null;
  }

  @Override
  public String toString() {
    return "Light parameter lsit";
  }

  @NotNull
  @Override
  public PsiParameter[] getParameters() {
    if (myCachedParameters == null) {
      if (myParameters.isEmpty()) {
        myCachedParameters = PsiParameter.EMPTY_ARRAY;
      }
      else {
        myCachedParameters = myParameters.toArray(new PsiParameter[myParameters.size()]);
      }
    }
    
    return myCachedParameters;
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
