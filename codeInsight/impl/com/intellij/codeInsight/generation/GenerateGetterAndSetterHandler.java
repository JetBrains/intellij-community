package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class GenerateGetterAndSetterHandler extends GenerateGetterSetterHandlerBase{
  private final GenerateGetterHandler myGenerateGetterHandler = new GenerateGetterHandler();
  private final GenerateSetterHandler myGenerateSetterHandler = new GenerateSetterHandler();

  public GenerateGetterAndSetterHandler(){
    super(CodeInsightBundle.message("generate.getter.setter.title"));
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object original) throws IncorrectOperationException {
    ArrayList<Object> array = new ArrayList<Object>();
    Object[] getters = myGenerateGetterHandler.generateMemberPrototypes(aClass, original);
    Object[] setters = myGenerateSetterHandler.generateMemberPrototypes(aClass, original);

    if (getters.length > 0 && setters.length > 0){
      array.add(getters[0]);
      array.add(setters[0]);
    }

    return array.toArray(new Object[array.size()]);
  }
}