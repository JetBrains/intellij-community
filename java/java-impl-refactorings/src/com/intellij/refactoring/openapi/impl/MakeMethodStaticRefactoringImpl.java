// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.MakeStaticRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.Settings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class MakeMethodStaticRefactoringImpl extends RefactoringImpl<MakeMethodStaticProcessor>
  implements MakeStaticRefactoring<PsiMethod> {
  MakeMethodStaticRefactoringImpl(Project project,
                                  PsiMethod method,
                                  boolean replaceUsages,
                                  String classParameterName,
                                  PsiField @NotNull [] fields,
                                  String[] names) {
    super(new MakeMethodStaticProcessor(project, method, new Settings(replaceUsages, classParameterName, fields, names)));
  }

  @Override
  public PsiMethod getMember() {
    return myProcessor.getMember();
  }

  @Override
  public boolean isReplaceUsages() {
    return myProcessor.getSettings().isReplaceUsages();
  }

  @Override
  public String getClassParameterName() {
    return myProcessor.getSettings().getClassParameterName();
  }

  @Override
  public List<PsiField> getFields() {
    final Settings settings = myProcessor.getSettings();
    List<PsiField> result = new ArrayList<>();
    final List<Settings.FieldParameter> parameterOrderList = settings.getParameterOrderList();
    for (final Settings.FieldParameter fieldParameter : parameterOrderList) {
      result.add(fieldParameter.field);
    }

    return result;
  }

  @Override
  @Nullable
  public String getParameterNameForField(PsiField field) {
    return myProcessor.getSettings().getNameForField(field);
  }


}
