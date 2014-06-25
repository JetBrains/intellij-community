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
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.search.ProjectScope;
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
public class ProjectBytecodeAnalysis extends AbstractProjectComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis");
  private static final CharTableImpl charTable = new CharTableImpl();
  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };
  private final PsiAnnotation notNullAnnotation;

  private final PsiManager myPsiManager;

  private volatile Annotations myAnnotations = null;

  private static final NotNullLazyKey<ProjectBytecodeAnalysis, Project>
    INSTANCE_KEY = ServiceManager.createLazyKey(ProjectBytecodeAnalysis.class);

  public static ProjectBytecodeAnalysis getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  public ProjectBytecodeAnalysis(Project project, PsiManager psiManager) {
    super(project);
    myPsiManager = psiManager;

    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        unloadAnnotations();
      }
    });
    notNullAnnotation = createAnnotationFromText("@org.jetbrains.annotations.NotNull");
  }

  private void loadAnnotations() {
    Annotations annotations = new Annotations();
    loadParameterAnnotations(annotations);
    loadContractAnnotations(annotations);
    myAnnotations = annotations;
    LOG.info("NotNull annotations: " + myAnnotations.notNulls.size());
    LOG.info("Contract annotations: " + myAnnotations.contracts.size());
  }

  private void unloadAnnotations() {
    myAnnotations = null;
    LOG.info("unloaded");
  }

  private void loadParameterAnnotations(Annotations annotations) {
    LOG.info("initializing parameter annotations");
    final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.NotNull, Value.Top));
    FileBasedIndex.getInstance().processValues(
      BytecodeAnalysisIndex.NAME, BytecodeAnalysisIndex.PARAMETERS, null, new FileBasedIndex.ValueProcessor<Collection<IntIdEquation>>() {
        @Override
        public boolean process(VirtualFile file, Collection<IntIdEquation> value) {
          for (IntIdEquation intIdEquation : value) {
            solver.addEquation(intIdEquation);
          }
          return true;
        }
      }, ProjectScope.getLibrariesScope(myProject));
    LOG.info("parameter equations are constructed");
    LOG.info("equations: " + solver.getSize());
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.info("parameter equations are solved");
    BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, annotations);
  }

  private void loadContractAnnotations(Annotations annotations) {
    LOG.info("initializing contract annotations");
    final IntIdSolver solver = new IntIdSolver(new ELattice<Value>(Value.Bot, Value.Top));
    FileBasedIndex.getInstance().processValues(
      BytecodeAnalysisIndex.NAME, BytecodeAnalysisIndex.CONTRACTS, null, new FileBasedIndex.ValueProcessor<Collection<IntIdEquation>>() {
        @Override
        public boolean process(VirtualFile file, Collection<IntIdEquation> value) {
          for (IntIdEquation intIdEquation : value) {
            solver.addEquation(intIdEquation);
          }
          return true;
        }
      }, ProjectScope.getLibrariesScope(myProject));
    LOG.info("contract equations are constructed");
    LOG.info("equations: " + solver.getSize());
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.info("contract equations are solved");
    BytecodeAnalysisConverter.getInstance().addAnnotations(solutions, annotations);
  }

  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
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

  @Nullable
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    return collectInferredAnnotations(listOwner);
  }

  // TODO the best way to synchronize?
  @Nullable
  private synchronized PsiAnnotation[] collectInferredAnnotations(PsiModifierListOwner listOwner) {
    if (myAnnotations == null) {
      loadAnnotations();
    }
    try {
      int key = getKey(listOwner);
      if (key == -1) {
        return null;
      }
      boolean notNull = myAnnotations.notNulls.contains(key);
      String contractValue = myAnnotations.contracts.get(key);

      if (notNull && contractValue != null) {
        return new PsiAnnotation[]{
          notNullAnnotation,
          createAnnotationFromText("@org.jetbrains.annotations.Contract(" + contractValue + ")")
        };
      }
      else if (notNull) {
        return new PsiAnnotation[]{
          notNullAnnotation
        };
      }
      else if (contractValue != null) {
        return new PsiAnnotation[]{
          createAnnotationFromText("@org.jetbrains.annotations.Contract(" + contractValue + ")")
        };
      }
      else {
        return null;
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
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
      return myAnnotations.notNulls.contains(key) ? notNullAnnotation : null;
    }
    catch (IOException e) {
      LOG.error(e);
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
      return contractValue != null ? createAnnotationFromText("@org.jetbrains.annotations.Contract(" + contractValue + ")") : null;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public static int getKey(@NotNull PsiModifierListOwner owner) throws IOException {
    if (owner instanceof PsiMethod) {
      return BytecodeAnalysisConverter.getInstance().mkKey((PsiMethod)owner, new Out());
    }
    else if (owner instanceof PsiParameter) {
      final PsiElement declarationScope = ((PsiParameter)owner).getDeclarationScope();
      if (!(declarationScope instanceof PsiMethod)) {
        return -1;
      }
      final PsiMethod psiMethod = (PsiMethod)declarationScope;
      final int index = psiMethod.getParameterList().getParameterIndex((PsiParameter)owner);
      return BytecodeAnalysisConverter.getInstance().mkKey(psiMethod, new In(index));
    } else {
      return -1;
    }
  }

  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    synchronized (charTable) {
      final DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager,
                                                                 new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null,
                                                                 charTable);
      final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
      return (PsiAnnotation) element;
    }
  }
}

class Annotations {
  // @NotNull keys
  final TIntHashSet notNulls = new TIntHashSet();
  // @Contracts
  final TIntObjectHashMap<String> contracts = new TIntObjectHashMap<String>();
}
