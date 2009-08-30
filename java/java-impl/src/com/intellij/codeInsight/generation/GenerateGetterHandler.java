package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;

public class GenerateGetterHandler extends GenerateGetterSetterHandlerBase {
  public GenerateGetterHandler() {
    super(CodeInsightBundle.message("generate.getter.fields.chooser.title"));
  }

  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass.isInterface()) {
      return ClassMember.EMPTY_ARRAY; // TODO
    }
    return super.chooseOriginalMembers(aClass, project);
  }

  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof EncapsulatableClassMember) {
      final EncapsulatableClassMember encapsulatableClassMember = (EncapsulatableClassMember)original;
      final GenerationInfo getter = encapsulatableClassMember.generateGetter();
      if (getter != null) {
        return new GenerationInfo[]{getter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }
}
