package com.intellij.refactoring.introduceparameterobject;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

class ParameterSpec {
  private final PsiParameter myParameter;
  private final boolean setterRequired;
    private final String name;
    private final PsiType type;

  ParameterSpec(final PsiParameter parameter, final String name, final PsiType type, final boolean setterRequired) {
    myParameter = parameter;
    this.setterRequired = setterRequired;
    this.name = name;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public PsiType getType() {
    return type;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  public boolean isSetterRequired() {
        return setterRequired;
    }
}
