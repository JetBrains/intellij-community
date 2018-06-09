// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.Nullability;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodSnapshot {
  public static final Key<ExtractMethodSnapshot> SNAPSHOT_KEY = Key.create("ExtractMethodSnapshot");

  public final Project myProject;
  public final String myMethodName;
  public final boolean myStatic;
  public final boolean myIsChainedConstructor;
  public final String myMethodVisibility;
  public final Nullability myNullability;
  public final SmartTypePointer myReturnType;
  public final List<SmartPsiElementPointer<PsiVariable>> myOutputVariables;
  public final SmartPsiElementPointer<PsiVariable> myOutputVariable;
  public final SmartPsiElementPointer<PsiVariable> myArtificialOutputVariable;
  public final List<VariableDataSnapshot> myVariableDatum;
  public final boolean myFoldable;
  public final SmartPsiElementPointer<PsiClass> myTargetClass;

  public ExtractMethodSnapshot(@NotNull ExtractMethodProcessor from) {
    myProject = from.getProject();
    myMethodName = from.myMethodName;
    myStatic = from.myStatic;
    myIsChainedConstructor = from.myIsChainedConstructor;
    myMethodVisibility = from.myMethodVisibility;
    myNullability = from.myNullability;

    SmartTypePointerManager typePointerManager = SmartTypePointerManager.getInstance(myProject);
    myReturnType = typePointerManager.createSmartTypePointer(from.myReturnType);

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    myOutputVariables = StreamEx.of(from.myOutputVariables).map(smartPointerManager::createSmartPsiElementPointer).toList();
    myOutputVariable = ContainerUtil.getFirstItem(myOutputVariables);
    myArtificialOutputVariable = from.myArtificialOutputVariable != null
                                 ? smartPointerManager.createSmartPsiElementPointer(from.myArtificialOutputVariable) : null;

    myVariableDatum = StreamEx.of(from.myVariableDatum).map(data -> new VariableDataSnapshot(data, myProject)).toList();
    myFoldable = from.myInputVariables.isFoldable();

    myTargetClass = from.myTargetClass != null ? smartPointerManager.createSmartPsiElementPointer(from.myTargetClass) : null;
  }
}
