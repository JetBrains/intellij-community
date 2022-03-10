// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ChangeSignatureRefactoring;
import com.intellij.refactoring.RefactoringImpl;

public class ChangeSignatureRefactoringImpl extends RefactoringImpl<ChangeSignatureProcessorBase> implements ChangeSignatureRefactoring {
  public ChangeSignatureRefactoringImpl(ChangeSignatureProcessorBase processor) {
    super(processor);
  }


  public BaseRefactoringProcessor getProcessor() {
    return myProcessor;
  }
}
