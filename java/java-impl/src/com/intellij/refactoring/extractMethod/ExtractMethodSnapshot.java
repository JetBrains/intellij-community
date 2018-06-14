// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.Nullability;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
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
                            .map(fromClass -> findClassInCopy(pattern, copy, fromClass))
                            .map(smartPointerManager::createSmartPsiElementPointer)
                            .orElse(null);
  }

  @Nullable
  private static PsiClass findClassInCopy(PsiElement[] pattern, PsiElement[] copy, @NotNull PsiClass fromClass) {
    PsiFile patternFile = pattern[0].getContainingFile();
    PsiFile copyFile = copy[0].getContainingFile();
    if (patternFile == null || copyFile == null) {
      return null;
    }

    List<PathNode> path = new ArrayList<>();
    PsiElement element = fromClass;
    while (element != patternFile) {
      IElementType type = element.getNode().getElementType();
      String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : null;
      int indexByType = 0;
      for (PsiElement prev = element.getPrevSibling(); prev != null; prev = prev.getPrevSibling()) {
        if (prev.getNode().getElementType() == type) {
          indexByType++;
        }
      }
      path.add(new PathNode(type, indexByType, name));
      element = element.getParent();
      if (element == null) {
        return null;
      }
    }


    ListIterator<PathNode> pathIterator = path.listIterator(path.size());
    PsiElement where = copyFile;
    while (true) {
      if (!pathIterator.hasPrevious()) {
        return ObjectUtils.tryCast(where, PsiClass.class);
      }

      PathNode pathNode = pathIterator.previous();
      int indexByType = 0;
      for (PsiElement next = where.getFirstChild(); next != null; next = next.getNextSibling()) {
        if (next.getNode().getElementType() != pathNode.myType) {
          continue;
        }
        if (indexByType < pathNode.myIndexByType) {
          indexByType++;
          continue;
        }
        String name = next instanceof PsiNamedElement ? ((PsiNamedElement)next).getName() : null;
        if (Objects.equals(name, pathNode.myExpectedName)) {
          where = next;
          break;
        }
        return null;
      }
    }
  }

  @Nullable
  public PsiClass getTargetClass() {
    return myTargetClass != null ? myTargetClass.getElement() : null;
  }

  private static class PathNode {
    final IElementType myType;
    final int myIndexByType;
    final String myExpectedName;

    public PathNode(@NotNull IElementType type, int indexByType, @Nullable String expectedName) {
      myType = type;
      myIndexByType = indexByType;
      myExpectedName = expectedName;
    }

    @Override
    public String toString() {
      return myType + "[" + myIndexByType + "]" + (myExpectedName != null ? " = " + myExpectedName : "");
    }
  }
}
