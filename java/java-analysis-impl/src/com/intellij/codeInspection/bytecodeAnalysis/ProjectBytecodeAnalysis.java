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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.LongStack;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongObjectHashMap;
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
  public static final int EQUATIONS_LIMIT = 1000;
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
      long ownerKey = getKey(listOwner);
      if (ownerKey == -1) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      TLongArrayList allKeys = contractKeys(listOwner, ownerKey);
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
    catch (EquationsLimitException e) {
      String externalName = PsiFormatUtil.getExternalName(listOwner, false, Integer.MAX_VALUE);
      LOG.info("Too many equations for " + externalName);
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

  public static long getKey(@NotNull PsiModifierListOwner owner) throws IOException {
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

  public static TLongArrayList contractKeys(@NotNull PsiModifierListOwner owner, long primaryKey) throws IOException {
    if (owner instanceof PsiMethod) {
      TLongArrayList result = BytecodeAnalysisConverter.getInstance().mkInOutKeys((PsiMethod)owner, primaryKey);
      result.add(primaryKey);
      return result;
    }
    TLongArrayList result = new TLongArrayList(1);
    result.add(primaryKey);
    return result;
  }

  private Annotations loadAnnotations(@NotNull PsiModifierListOwner owner, long key, TLongArrayList allKeys)
    throws IOException, EquationsLimitException {
    Annotations result = new Annotations();
    if (owner instanceof PsiParameter) {
      final Solver solver = new Solver(new ELattice<Value>(Value.NotNull, Value.Top));
      collectEquations(allKeys, solver);
      TLongObjectHashMap<Value> solutions = solver.solve();
      BytecodeAnalysisConverter.getInstance().addParameterAnnotations(solutions, result);
    } else if (owner instanceof PsiMethod) {
      final Solver solver = new Solver(new ELattice<Value>(Value.Bot, Value.Top));
      collectEquations(allKeys, solver);
      TLongObjectHashMap<Value> solutions = solver.solve();
      BytecodeAnalysisConverter.getInstance().addMethodAnnotations(solutions, result, key,
                                                                   ((PsiMethod)owner).getParameterList().getParameters().length);
    }
    return result;
  }

  private void collectEquations(TLongArrayList keys, Solver solver) throws EquationsLimitException {
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
    TLongHashSet queued = new TLongHashSet();
    LongStack queue = new LongStack();

    for (int i = 0; i < keys.size(); i++) {
      long key = keys.get(i);
      queue.push(key);
      queued.add(key);
      // stable/unstable
      queue.push(-key);
      queued.add(-key);
    }

    FileBasedIndex index = FileBasedIndex.getInstance();
    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      List<IdEquation> equations = index.getValues(BytecodeAnalysisIndex.NAME, queue.pop(), librariesScope);
      for (IdEquation equation : equations) {
        IdResult rhs = equation.rhs;
        solver.addEquation(equation);
        if (rhs instanceof IdPending) {
          IdPending intIdPending = (IdPending)rhs;
          for (IntIdComponent component : intIdPending.delta) {
            for (long depKey : component.ids) {
              if (!queued.contains(depKey)) {
                queue.push(depKey);
                queued.add(depKey);
              }
              // stable/unstable
              long swapped = -depKey;
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
  final TLongHashSet notNulls = new TLongHashSet();
  // @Contracts
  final TLongObjectHashMap<String> contracts = new TLongObjectHashMap<String>();
}

class EquationsLimitException extends Exception {}
