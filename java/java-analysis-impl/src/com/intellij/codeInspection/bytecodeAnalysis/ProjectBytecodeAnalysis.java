/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * @author lambdamix
 */
public class ProjectBytecodeAnalysis {
  public static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis");
  public static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
  private final Project myProject;

  public static ProjectBytecodeAnalysis getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBytecodeAnalysis.class);
  }

  public ProjectBytecodeAnalysis(Project project) {
    myProject = project;
  }

  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return null;
    }
    if (annotationFQN.equals(AnnotationUtil.NOT_NULL) || annotationFQN.equals(ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
      PsiAnnotation[] annotations = findInferredAnnotations(listOwner);
      for (PsiAnnotation annotation : annotations) {
        if (annotationFQN.equals(annotation.getQualifiedName())) {
          return annotation;
        }
      }
      return null;
    }
    else {
      return null;
    }
  }

  @NotNull
  public PsiAnnotation[] findInferredAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    return CachedValuesManager.getCachedValue(listOwner, new CachedValueProvider<PsiAnnotation[]>() {
      @Nullable
      @Override
      public Result<PsiAnnotation[]> compute() {
        return Result.create(collectInferredAnnotations(listOwner), listOwner);
      }
    });
  }

  @NotNull
  private PsiAnnotation[] collectInferredAnnotations(PsiModifierListOwner listOwner) {
    try {
      int ownerKey = getKey(listOwner);
      if (ownerKey == -1) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      IntArrayList allKeys = contractKeys(listOwner, ownerKey);
      Annotations annotations = loadAnnotations(listOwner, ownerKey, allKeys);
      boolean notNull = annotations.notNulls.contains(ownerKey);
      String contractValue = annotations.contracts.get(ownerKey);

      if (notNull && contractValue != null) {
        return new PsiAnnotation[]{
          getNotNullAnnotation(),
          createAnnotationFromText("@" + ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT + "(" + contractValue + ")")
        };
      }
      else if (notNull) {
        return new PsiAnnotation[]{
          getNotNullAnnotation()
        };
      }
      else if (contractValue != null) {
        return new PsiAnnotation[]{
          createAnnotationFromText("@" + ControlFlowAnalyzer.ORG_JETBRAINS_ANNOTATIONS_CONTRACT + "(" + contractValue + ")")
        };
      }
      else {
        return PsiAnnotation.EMPTY_ARRAY;
      }
    }
    catch (IOException e) {
      LOG.debug(e);
      return PsiAnnotation.EMPTY_ARRAY;
    }
  }

  private PsiAnnotation getNotNullAnnotation() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, new CachedValueProvider<PsiAnnotation>() {
      @Nullable
      @Override
      public Result<PsiAnnotation> compute() {
        return Result.create(createAnnotationFromText("@" + AnnotationUtil.NOT_NULL), ModificationTracker.NEVER_CHANGED);
      }
    });
  }

  public PsiAnnotation createContractAnnotation(String contractValue) {
    return createAnnotationFromText("@org.jetbrains.annotations.Contract(" + contractValue + ")");
  }

  public static int getKey(@NotNull PsiModifierListOwner owner) throws IOException {
    LOG.assertTrue(owner instanceof PsiCompiledElement, owner);

    if (owner instanceof PsiMethod) {
      return BytecodeAnalysisConverter.getInstance().mkPsiKey((PsiMethod)owner, new Out());
    }
    if (owner instanceof PsiParameter) {
      PsiElement parent = owner.getParent();
      if (parent instanceof PsiParameterList) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethod) {
          final int index = ((PsiParameterList)parent).getParameterIndex((PsiParameter)owner);
          return BytecodeAnalysisConverter.getInstance().mkPsiKey((PsiMethod)gParent, new In(index));
        }
      }
    }

    return -1;
  }

  public static IntArrayList contractKeys(@NotNull PsiModifierListOwner owner, int primaryKey) throws IOException {
    if (owner instanceof PsiMethod) {
      IntArrayList result = BytecodeAnalysisConverter.getInstance().mkInOutKeys((PsiMethod)owner);
      result.add(primaryKey);
      return result;
    }
    IntArrayList result = new IntArrayList(1);
    result.add(primaryKey);
    return result;
  }

  private Annotations loadAnnotations(@NotNull PsiModifierListOwner owner, int key, IntArrayList allKeys) throws IOException {
    Annotations result = new Annotations();
    if (owner instanceof PsiParameter) {
      final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.NotNull, Value.Top));
      collectEquations(allKeys, solver);
      TIntObjectHashMap<Value> solutions = solver.solve();
      BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, result);
    } else if (owner instanceof PsiMethod) {
      final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.Bot, Value.Top));
      collectEquations(allKeys, solver);
      TIntObjectHashMap<Value> solutions = solver.solve();
      BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, result);
    }
    return result;
  }

  // todo - should be some limit of equations
  private void collectEquations(IntArrayList keys, IntIdSolver solver) {
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
    TIntHashSet queued = new TIntHashSet();
    TIntStack queue = new TIntStack();

    for (int i = 0; i < keys.size(); i++) {
      int key = keys.get(i);
      queue.push(key);
      queued.add(key);
      // stable/unstable
      queue.push(-key);
      queued.add(-key);
    }

    FileBasedIndex index = FileBasedIndex.getInstance();
    while (queue.size() > 0) {
      List<IntIdEquation> equations = index.getValues(BytecodeAnalysisIndex.NAME, queue.pop(), librariesScope);
      for (IntIdEquation equation : equations) {
        IntIdResult rhs = equation.rhs;
        solver.addEquation(equation);
        if (rhs instanceof IntIdPending) {
          IntIdPending intIdPending = (IntIdPending)rhs;
          for (IntIdComponent component :intIdPending.delta) {
            for (int depKey : component.ids) {
              if (!queued.contains(depKey)) {
                queue.push(depKey);
                queued.add(depKey);
              }
              // stable/unstable
              int swapped = -depKey;
              if (!queued.contains(swapped)) {
                queue.push(swapped);
                queued.add(swapped);
              }
            }
          }
        }
      }
    }
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(myProject).createAnnotationFromText(text, null);
    annotation.putUserData(INFERRED_ANNOTATION, Boolean.TRUE);
    return annotation;
  }
}

class Annotations {
  // @NotNull keys
  final TIntHashSet notNulls = new TIntHashSet();
  // @Contracts
  final TIntObjectHashMap<String> contracts = new TIntObjectHashMap<String>();
}
