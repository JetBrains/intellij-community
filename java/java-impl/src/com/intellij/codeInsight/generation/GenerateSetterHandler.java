/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GenerateSetterHandler extends GenerateGetterSetterHandlerBase {

  public GenerateSetterHandler() {
    super(CodeInsightBundle.message("generate.setter.fields.chooser.title"));
  }

  @Override
  protected String getHelpId() {
    return "Generate_Setter_Dialog";
  }

  @Nullable
  @Override
  protected JComponent getHeaderPanel(final Project project) {
    return getHeaderPanel(project, SetterTemplatesManager.getInstance(), CodeInsightBundle.message("generate.equals.hashcode.template"));
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof PropertyClassMember) {
      final PropertyClassMember propertyClassMember = (PropertyClassMember)original;
      final GenerationInfo[] getters = propertyClassMember.generateSetters(aClass);
      if (getters != null) {
        return getters;
      }
    }
    else if (original instanceof EncapsulatableClassMember) {
      final EncapsulatableClassMember encapsulatableClassMember = (EncapsulatableClassMember)original;
      final GenerationInfo setter = encapsulatableClassMember.generateSetter();
      if (setter != null) {
        return new GenerationInfo[]{setter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  protected String getNothingFoundMessage() {
    return "No fields have been found to generate setters for";
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return "No fields without setter were found";
  }
}
