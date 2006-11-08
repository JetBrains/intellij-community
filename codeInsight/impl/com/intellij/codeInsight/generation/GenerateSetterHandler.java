package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(CodeInsightBundle.message("generate.setter.fields.chooser.title"));
  }

  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof EncapsulatableClassMember) {
      final EncapsulatableClassMember encapsulatableClassMember = (EncapsulatableClassMember)original;
      final GenerationInfo setter = encapsulatableClassMember.generateSetter();
      if (setter != null) {
        return new GenerationInfo[]{setter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }
}
