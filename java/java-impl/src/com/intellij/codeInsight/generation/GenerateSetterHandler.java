// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(JavaBundle.message("generate.setter.fields.chooser.title"));
  }

  @Override
  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
      () -> ContainerUtil.exists(GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass), ecm -> !ecm.isReadOnlyMember()));
  }

  @Override
  protected String getHelpId() {
    return "Generate_Setter_Dialog";
  }

  @Nullable
  @Override
  protected JComponent getHeaderPanel(final Project project) {
    return getHeaderPanel(project, SetterTemplatesManager.getInstance(), JavaBundle.message("generate.equals.hashcode.template"));
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof PropertyClassMember propertyClassMember) {
      final GenerationInfo[] getters = propertyClassMember.generateSetters(aClass);
      if (getters != null) {
        return getters;
      }
    }
    else if (original instanceof EncapsulatableClassMember encapsulatableClassMember) {
      final GenerationInfo setter = encapsulatableClassMember.generateSetter();
      if (setter != null) {
        return new GenerationInfo[]{setter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  protected String getNothingFoundMessage() {
    return JavaBundle.message("generate.setters.no.fields");
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return JavaBundle.message("generate.setters.no.fields.without.setters");
  }
}
