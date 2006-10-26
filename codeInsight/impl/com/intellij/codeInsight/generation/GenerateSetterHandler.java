package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(CodeInsightBundle.message("generate.setter.fields.chooser.title"));
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof EncapsulatableClassMember) {
      final EncapsulatableClassMember encapsulatableClassMember = (EncapsulatableClassMember)original;
      final Object setter = encapsulatableClassMember.generateSetter();
      if (setter != null) {
        return new Object[]{setter};
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}