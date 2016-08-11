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
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.MakeStaticRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.Settings;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public class MakeMethodStaticRefactoringImpl extends RefactoringImpl<MakeMethodStaticProcessor>
  implements MakeStaticRefactoring<PsiMethod> {
  MakeMethodStaticRefactoringImpl(Project project,
                                  PsiMethod method,
                                  boolean replaceUsages,
                                  String classParameterName,
                                  PsiField[] fields,
                                  String[] names) {
    super(new MakeMethodStaticProcessor(project, method, new Settings(replaceUsages, classParameterName, fields, names)));
  }

  public PsiMethod getMember() {
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
    List<PsiField> result = new ArrayList<>();
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
