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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GenerateGetterAndSetterHandler extends GenerateGetterSetterHandlerBase{
  private final GenerateGetterHandler myGenerateGetterHandler = new GenerateGetterHandler();
  private final GenerateSetterHandler myGenerateSetterHandler = new GenerateSetterHandler();

  public GenerateGetterAndSetterHandler(){
    super(JavaBundle.message("generate.getter.setter.title"));
  }

  @Override
  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass).stream().anyMatch(ecm -> !ecm.isReadOnlyMember());
  }

  @Nullable
  @Override
  protected JComponent getHeaderPanel(Project project) {
    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(getHeaderPanel(project, GetterTemplatesManager.getInstance(), JavaBundle.message("generate.getter.template")), BorderLayout.NORTH);
    panel.add(getHeaderPanel(project, SetterTemplatesManager.getInstance(), JavaBundle.message("generate.setter.template")), BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    ArrayList<GenerationInfo> array = new ArrayList<>();
    GenerationInfo[] getters = myGenerateGetterHandler.generateMemberPrototypes(aClass, original);
    GenerationInfo[] setters = myGenerateSetterHandler.generateMemberPrototypes(aClass, original);

    if (getters.length + setters.length > 0){
      Collections.addAll(array, getters);
      Collections.addAll(array, setters);
    }

    return array.toArray(GenerationInfo.EMPTY_ARRAY);
  }

  @Override
  protected void notifyOnSuccess(Editor editor,
                                 ClassMember[] members,
                                 List<? extends GenerationInfo> generatedMembers) {
    super.notifyOnSuccess(editor, members, generatedMembers);
    if (Arrays.stream(members).anyMatch(fm -> fm instanceof PsiFieldMember &&
                                              GetterSetterPrototypeProvider.isReadOnlyProperty(((PsiFieldMember)fm).getElement()))) {
      HintManager.getInstance().showErrorHint(editor,
                                              JavaBundle.message("generate.getter.and.setter.error.setters.for.read.only.not.generated"), HintManager.ABOVE);
    }
  }

  @Override
  protected String getNothingFoundMessage() {
    return JavaBundle.message("generate.getter.and.setter.error.no.fields");
  }

  @Override
  protected String getNothingAcceptedMessage() {
    return JavaBundle.message("generate.getter.and.setter.error.no.fields.without.getters.and.setters");
  }
}