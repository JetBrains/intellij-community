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

public class GenerateGetterHandler extends GenerateGetterSetterHandlerBase {
  public GenerateGetterHandler() {
    super(CodeInsightBundle.message("generate.getter.fields.chooser.title"));
  }

  @Override
  protected String getHelpId() {
    return "Generate_Getter_Dialog";
  }

  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    if (aClass.isInterface()) {
      return ClassMember.EMPTY_ARRAY; // TODO
    }
    return super.chooseOriginalMembers(aClass, project);
  }

  @Nullable
  @Override
  protected JComponent getHeaderPanel(final Project project) {
    return getHeaderPanel(project, GetterTemplatesManager.getInstance(), CodeInsightBundle.message("generate.equals.hashcode.template"));
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (original instanceof PropertyClassMember) {
      final PropertyClassMember propertyClassMember = (PropertyClassMember)original;
      final GenerationInfo[] getters = propertyClassMember.generateGetters(aClass);
      if (getters != null) {
        return getters;
      }
    } else if (original instanceof EncapsulatableClassMember) {
      final EncapsulatableClassMember encapsulatableClassMember = (EncapsulatableClassMember)original;
      final GenerationInfo getter = encapsulatableClassMember.generateGetter();
      if (getter != null) {
        return new GenerationInfo[]{getter};
      }
    }
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  protected String getNothingFoundMessage() {
    return "No fields have been found to generate getters for";
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return "No fields without getter were found";
  }
}
