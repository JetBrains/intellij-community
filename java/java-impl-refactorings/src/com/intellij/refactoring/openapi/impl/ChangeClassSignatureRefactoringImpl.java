// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.ChangeClassSignatureRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;

public class ChangeClassSignatureRefactoringImpl extends RefactoringImpl<ChangeClassSignatureProcessor> implements ChangeClassSignatureRefactoring {
  public ChangeClassSignatureRefactoringImpl(Project project, PsiClass aClass, TypeParameterInfo[] newSignature) {
    super(new ChangeClassSignatureProcessor(project, aClass, newSignature));
  }
}
