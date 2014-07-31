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
import com.intellij.util.containers.Stack;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      HKey ownerKey = getKey(listOwner, md);
      if (ownerKey == null) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      ArrayList<HKey> allKeys = contractKeys(listOwner, ownerKey);
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
    catch (EquationsLimitException e) {
      String externalName = PsiFormatUtil.getExternalName(listOwner, false, Integer.MAX_VALUE);
      LOG.info("Too many equations for " + externalName);
      return PsiAnnotation.EMPTY_ARRAY;
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
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

  @Nullable
  public static HKey getKey(@NotNull PsiModifierListOwner owner, MessageDigest md) {
    LOG.assertTrue(owner instanceof PsiCompiledElement, owner);
    if (owner instanceof PsiMethod) {
      return BytecodeAnalysisConverter.psiKey((PsiMethod)owner, new Out(), md);
    }
    if (owner instanceof PsiParameter) {
      PsiElement parent = owner.getParent();
      if (parent instanceof PsiParameterList) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethod) {
          final int index = ((PsiParameterList)parent).getParameterIndex((PsiParameter)owner);
          return BytecodeAnalysisConverter.psiKey((PsiMethod)gParent, new In(index), md);
        }
      }
    }
    return null;
  }

  public static ArrayList<HKey> contractKeys(@NotNull PsiModifierListOwner owner, HKey primaryKey) {
    if (owner instanceof PsiMethod) {
      ArrayList<HKey> result = BytecodeAnalysisConverter.mkInOutKeys((PsiMethod)owner, primaryKey);
      result.add(primaryKey);
      return result;
    }
    ArrayList<HKey> result = new ArrayList<HKey>(1);
    result.add(primaryKey);
    return result;
  }

  private Annotations loadAnnotations(@NotNull PsiModifierListOwner owner, @NotNull HKey key, ArrayList<HKey> allKeys)
    throws EquationsLimitException {
    Annotations result = new Annotations();
    if (owner instanceof PsiParameter) {
      final Solver solver = new Solver(new ELattice<Value>(Value.NotNull, Value.Top));
      collectEquations(allKeys, solver);
      HashMap<HKey, Value> solutions = solver.solve();
      BytecodeAnalysisConverter.addParameterAnnotations(solutions, result);
    } else if (owner instanceof PsiMethod) {
      final Solver solver = new Solver(new ELattice<Value>(Value.Bot, Value.Top));
      collectEquations(allKeys, solver);
      HashMap<HKey, Value> solutions = solver.solve();
      int arity = ((PsiMethod)owner).getParameterList().getParameters().length;
      BytecodeAnalysisConverter.addMethodAnnotations(solutions, result, key, arity);
    }
    return result;
  }

  private void collectEquations(ArrayList<HKey> keys, Solver solver) throws EquationsLimitException {
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
    HashSet<HKey> queued = new HashSet<HKey>();
    Stack<HKey> queue = new Stack<HKey>();

    for (HKey key : keys) {
      queue.push(key);
      queued.add(key);
      // stable/unstable
      HKey nKey = key.negate();
      queue.push(nKey);
      queued.add(nKey);
    }

    FileBasedIndex index = FileBasedIndex.getInstance();
    while (!queue.empty()) {
      if (queued.size() > EQUATIONS_LIMIT) {
        throw new EquationsLimitException();
      }
      ProgressManager.checkCanceled();
      HKey hKey = queue.pop();
      List<HResult> results = index.getValues(BytecodeAnalysisIndex.NAME, hKey, librariesScope);
      for (HResult result : results) {
        solver.addEquation(new HEquation(hKey, result));
        if (result instanceof HPending) {
          HPending pending = (HPending)result;
          for (HComponent component : pending.delta) {
            for (HKey depKey : component.ids) {
              if (!queued.contains(depKey)) {
                queue.push(depKey);
                queued.add(depKey);
              }
              // stable/unstable
              HKey swapped = depKey.negate();
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
  final HashSet<HKey> notNulls = new HashSet<HKey>();
  // @Contracts
  final HashMap<HKey, String> contracts = new HashMap<HKey, String>();
}

class EquationsLimitException extends Exception {}
