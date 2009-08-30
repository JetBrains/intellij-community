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

  public GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<GenerationInfo>();
    GenerationInfo[] getters = myGenerateGetterHandler.generateMemberPrototypes(aClass, original);
    GenerationInfo[] setters = myGenerateSetterHandler.generateMemberPrototypes(aClass, original);

    if (getters.length > 0 && setters.length > 0){
      array.add(getters[0]);
      array.add(setters[0]);
    }

    return array.toArray(new GenerationInfo[array.size()]);
  }
}