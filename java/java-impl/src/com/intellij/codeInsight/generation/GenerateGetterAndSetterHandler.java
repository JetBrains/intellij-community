// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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
    return ContainerUtil.exists(GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass), ecm -> !ecm.isReadOnlyMember());
  }

  @Override
  protected @Nullable JComponent getHeaderPanel(Project project) {
    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(getHeaderPanel(project, GetterTemplatesManager.getInstance(), JavaBundle.message("generate.getter.template")), BorderLayout.NORTH);
    panel.add(getHeaderPanel(project, SetterTemplatesManager.getInstance(), JavaBundle.message("generate.setter.template")), BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember original) throws IncorrectOperationException {
    if (aClass == null) return GenerationInfo.EMPTY_ARRAY;
    myGenerateGetterHandler.myGenerateAnnotations = myGenerateAnnotations;
    myGenerateSetterHandler.myGenerateAnnotations = myGenerateAnnotations;
    ArrayList<GenerationInfo> array = new ArrayList<>();
    GenerationInfo[] getters = myGenerateGetterHandler.generateMemberPrototypes(aClass, original);
    GenerationInfo[] setters = myGenerateSetterHandler.generateMemberPrototypes(aClass, original);

    if (getters.length + setters.length > 0) {
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
    if (ContainerUtil.exists(members, member -> member instanceof PsiFieldMember fm &&
                                                GetterSetterPrototypeProvider.isReadOnlyProperty(fm.getElement()))) {
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