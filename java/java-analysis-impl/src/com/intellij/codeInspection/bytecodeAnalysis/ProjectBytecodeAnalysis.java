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

import com.intellij.ProjectTopics;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

/**
 * @author lambdamix
 */
public class ProjectBytecodeAnalysis {
  public static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis");
  public static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");
  private final Project myProject;

  private volatile Annotations myAnnotations = null;

  public static ProjectBytecodeAnalysis getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBytecodeAnalysis.class);
  }

  public ProjectBytecodeAnalysis(Project project) {
    myProject = project;
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        unloadAnnotations();
      }
    });
  }

  private void loadAnnotations() {
    Annotations annotations = new Annotations();
    loadParameterAnnotations(annotations);
    loadContractAnnotations(annotations);
    myAnnotations = annotations;
    LOG.debug("NotNull annotations: " + myAnnotations.notNulls.size());
    LOG.debug("Contract annotations: " + myAnnotations.contracts.size());
  }

  private void unloadAnnotations() {
    myAnnotations = null;
    LOG.debug("unloaded");
  }

  private void loadParameterAnnotations(Annotations annotations) {
    LOG.debug("initializing parameter annotations");
    final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.NotNull, Value.Top));

    processValues(true, new FileBasedIndex.ValueProcessor<Collection<IntIdEquation>>() {
      @Override
      public boolean process(VirtualFile file, Collection<IntIdEquation> value) {
        for (IntIdEquation intIdEquation : value) {
          solver.addEquation(intIdEquation);
        }
        return true;
      }
    });

    LOG.debug("parameter equations are constructed");
    LOG.debug("equations: " + solver.getSize());
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.debug("parameter equations are solved");
    BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, annotations);
  }

  private void processValues(final boolean parameters, final FileBasedIndex.ValueProcessor<Collection<IntIdEquation>> processor) {
    final GlobalSearchScope libScope = ProjectScope.getLibrariesScope(myProject);
    final FileBasedIndex index = FileBasedIndex.getInstance();
    index.iterateIndexableFiles(new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        ProgressManager.checkCanceled();
        if (!fileOrDir.isDirectory() && libScope.contains(fileOrDir)) {
          index.processValues(BytecodeAnalysisIndex.NAME, BytecodeAnalysisIndex.indexKey(fileOrDir, parameters),
                              fileOrDir, processor, GlobalSearchScope.fileScope(myProject, fileOrDir));
        }
        return false;
      }
    }, myProject, null);
  }

  private void loadContractAnnotations(Annotations annotations) {
    LOG.debug("initializing contract annotations");
    final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.Bot, Value.Top));
    processValues(false, new FileBasedIndex.ValueProcessor<Collection<IntIdEquation>>() {
      @Override
      public boolean process(VirtualFile file, Collection<IntIdEquation> value) {
        for (IntIdEquation intIdEquation : value) {
          solver.addEquation(intIdEquation);
        }
        return true;
      }
    });
    LOG.debug("contract equations are constructed");
    LOG.debug("equations: " + solver.getSize());
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.debug("contract equations are solved");
    BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, annotations);
  }

  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return null;
    }
    if (annotationFQN.equals("org.jetbrains.annotations.NotNull")) {
      return findNotNullAnnotation(listOwner);
    }
    else if (annotationFQN.equals("org.jetbrains.annotations.Contract")) {
      return findContractAnnotation(listOwner);
    }
    else {
      return null;
    }
  }

  @NotNull
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    if (!(listOwner instanceof PsiCompiledElement)) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    return collectInferredAnnotations(listOwner);
  }

  // TODO the best way to synchronize?
  @NotNull
  private synchronized PsiAnnotation[] collectInferredAnnotations(PsiModifierListOwner listOwner) {
    if (myAnnotations == null) {
      loadAnnotations();
    }
    try {
      int key = getKey(listOwner);
      if (key == -1) {
        return PsiAnnotation.EMPTY_ARRAY;
      }
      boolean notNull = myAnnotations.notNulls.contains(key);
      String contractValue = myAnnotations.contracts.get(key);

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

  @Nullable
  private synchronized PsiAnnotation findNotNullAnnotation(PsiModifierListOwner listOwner) {
    if (myAnnotations == null) {
      loadAnnotations();
    }
    try {
      int key = getKey(listOwner);
      if (key == -1) {
        return null;
      }
      return myAnnotations.notNulls.contains(key) ? getNotNullAnnotation() : null;
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
  }

  @Nullable
  private synchronized PsiAnnotation findContractAnnotation(PsiModifierListOwner listOwner) {
    if (myAnnotations == null) {
      loadAnnotations();
    }
    try {
      int key = getKey(listOwner);
      if (key == -1) {
        return null;
      }
      String contractValue = myAnnotations.contracts.get(key);
      return contractValue != null ? createContractAnnotation(contractValue) : null;
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
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
