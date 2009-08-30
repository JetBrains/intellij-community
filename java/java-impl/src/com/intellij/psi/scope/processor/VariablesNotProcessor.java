package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.util.SmartList;

import java.util.List;

public class VariablesNotProcessor extends VariablesProcessor{
  private final PsiVariable myVariable;

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive, List<PsiVariable> list){
    super(staticSensitive, list);
    myVariable = var;
  }

  public VariablesNotProcessor(PsiVariable var, boolean staticSensitive){
    this(var, staticSensitive, new SmartList<PsiVariable>());
  }

  protected boolean check(PsiVariable var, ResolveState state) {
    String name = var.getName();
    return name != null && name.equals(myVariable.getName()) && !var.equals(myVariable);
  }
}
