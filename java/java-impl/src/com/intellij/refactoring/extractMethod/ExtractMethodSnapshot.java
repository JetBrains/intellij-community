// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  @Nullable public final SmartTypePointer myReturnType;
  @NotNull public final List<SmartPsiElementPointer<PsiVariable>> myOutputVariables;
  @Nullable public final SmartPsiElementPointer<PsiVariable> myOutputVariable;
  @Nullable public final SmartPsiElementPointer<PsiVariable> myArtificialOutputVariable;
  @NotNull public final List<VariableDataSnapshot> myVariableDatum;
  public final boolean myFoldable;
  @Nullable public final SmartPsiElementPointer<PsiClass> myTargetClass;

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

  public ExtractMethodSnapshot(@NotNull ExtractMethodSnapshot from, @NotNull PsiElement[] pattern, @NotNull PsiElement[] copy) {
    myProject = from.myProject;
    myMethodName = from.myMethodName;
    myStatic = from.myStatic;
    myIsChainedConstructor = from.myIsChainedConstructor;
    myMethodVisibility = from.myMethodVisibility;
    myNullability = from.myNullability;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(from.myProject);
    PsiElement copyContext = copy[copy.length - 1];
    PsiType fromReturnType = from.myReturnType != null ? from.myReturnType.getType() : null;
    PsiType copyReturnType = fromReturnType != null ? factory.createTypeFromText(fromReturnType.getCanonicalText(), copyContext) : null;
    myReturnType = copyReturnType != null ? SmartTypePointerManager.getInstance(from.myProject)
                                                                   .createSmartTypePointer(copyReturnType) : null;

    Map<PsiVariable, PsiVariable> variableMap = new HashMap<>();
    ParametrizedDuplicates.collectCopyMapping(pattern, copy,
                                              unused -> false, (unused1, unused2) -> { },
                                              variableMap::put);

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
    myOutputVariables = StreamEx.of(from.myOutputVariables)
                                .map(e -> e.getElement())
                                .nonNull()
                                .map(variableMap::get)
                                .nonNull()
                                .map(smartPointerManager::createSmartPsiElementPointer).toList();
    myOutputVariable = ContainerUtil.getFirstItem(myOutputVariables);

    myArtificialOutputVariable = Optional.ofNullable(from.myArtificialOutputVariable)
                                         .map(SmartPsiElementPointer::getElement)
                                         .map(variableMap::get)
                                         .map(smartPointerManager::createSmartPsiElementPointer)
                                         .orElse(null);

    myVariableDatum = new ArrayList<>();
    for (VariableDataSnapshot fromData: from.myVariableDatum) {
      PsiVariable copyVariable = variableMap.get(fromData.getVariable());
      PsiType fromType = fromData.getType();
      PsiType copyType = fromType != null ? factory.createTypeFromText(fromType.getCanonicalText(), copyContext) : null;
      VariableDataSnapshot copyData =
        new VariableDataSnapshot(copyVariable, copyType, fromData.name, fromData.originalName, fromData.passAsParameter, from.myProject);
      myVariableDatum.add(copyData);
    }

    myFoldable = from.myFoldable;

    myTargetClass = Optional.ofNullable(from.getTargetClass())
                            .map(PsiElement::getTextRange)
                            .map(range -> findTargetClassInRange(copy[0].getContainingFile(), range))
                            .map(smartPointerManager::createSmartPsiElementPointer)
                            .orElse(null);
  }

  @Nullable
  private static PsiClass findTargetClassInRange(@Nullable PsiFile file, @NotNull TextRange range) {
    return file != null ? CodeInsightUtil.findElementInRange(file, range.getStartOffset(), range.getEndOffset(), PsiClass.class) : null;
  }

  @Nullable
  public PsiClass getTargetClass() {
    return myTargetClass != null ? myTargetClass.getElement() : null;
  }
}
