// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodSnapshot {
  final Project myProject;
  final String myMethodName;
  final boolean myStatic;
  final boolean myIsChainedConstructor;
  final String myMethodVisibility;
  final Nullness myNullness;
  final SmartTypePointer myReturnType;
  final List<SmartPsiElementPointer<PsiVariable>> myOutputVariables;
  final SmartPsiElementPointer<PsiVariable> myOutputVariable;
  final SmartPsiElementPointer<PsiVariable> myArtificialOutputVariable;
  final List<VariableDataSnapshot> myVariableDatum;

  public ExtractMethodSnapshot(@NotNull ExtractMethodProcessor from) {
    myProject = from.getProject();
    myMethodName = from.myMethodName;
    myStatic = from.myStatic;
    myIsChainedConstructor = from.myIsChainedConstructor;
    myMethodVisibility = from.myMethodVisibility;
    myNullness = from.myNullness;

    SmartTypePointerManager typePointerManager = SmartTypePointerManager.getInstance(myProject);
    myReturnType = typePointerManager.createSmartTypePointer(from.myReturnType);

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    myOutputVariables = StreamEx.of(from.myOutputVariables).map(v -> smartPointerManager.createSmartPsiElementPointer(v)).toList();
    myOutputVariable = ContainerUtil.getFirstItem(myOutputVariables);
    myArtificialOutputVariable = from.myArtificialOutputVariable != null
                                 ? smartPointerManager.createSmartPsiElementPointer(from.myArtificialOutputVariable) : null;

    myVariableDatum = StreamEx.of(from.myVariableDatum).map(data -> new VariableDataSnapshot(data, myProject)).toList();
  }
}
