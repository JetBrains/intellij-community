/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.MakeStaticRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.refactoring.makeStatic.Settings;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class MakeClassStaticRefactoringImpl extends RefactoringImpl<MakeClassStaticProcessor>
  implements MakeStaticRefactoring<PsiClass> {
  MakeClassStaticRefactoringImpl(Project project,
                                 PsiClass aClass,
                                 boolean replaceUsages,
                                 String classParameterName,
                                 PsiField[] fields,
                                 String[] names) {
    super(new MakeClassStaticProcessor(project, aClass, new Settings(replaceUsages, classParameterName, fields, names)));
  }

  public PsiClass getMember() {
    return myProcessor.getMember();
  }

  public boolean isReplaceUsages() {
    return myProcessor.getSettings().isReplaceUsages();
  }

  public String getClassParameterName() {
    return myProcessor.getSettings().getClassParameterName();
  }

  public List<PsiField> getFields() {
    final Settings settings = myProcessor.getSettings();
    List<PsiField> result = new ArrayList<PsiField>();
    final List<Settings.FieldParameter> parameterOrderList = settings.getParameterOrderList();
    for (final Settings.FieldParameter fieldParameter : parameterOrderList) {
      result.add(fieldParameter.field);
    }

    return result;
  }

  @Nullable
  public String getParameterNameForField(PsiField field) {
    return myProcessor.getSettings().getNameForField(field);
  }
}
