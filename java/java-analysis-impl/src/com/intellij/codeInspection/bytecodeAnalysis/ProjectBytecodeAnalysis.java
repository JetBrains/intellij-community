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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author lambdamix
 */
public class ProjectBytecodeAnalysis extends AbstractProjectComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis");
  private static final List<AnnotationData> NO_DATA = new ArrayList<AnnotationData>(0);

  private final PsiManager myPsiManager;

  private Annotations myAnnotations = null;

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
  }

  private void loadAnnotations() {
    Annotations parameterAnnotations = loadParameterAnnotations();
    Annotations contractAnnotations = loadContractAnnotations();
    myAnnotations = new Annotations(contractAnnotations.outs, parameterAnnotations.params, contractAnnotations.contracts);
  }

  private void unloadAnnotations() {
    myAnnotations = null;
    LOG.info("unloaded");
  }

  private Annotations loadParameterAnnotations() {
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
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.info("parameter equations are solved");
    return BytecodeAnalysisConverter.getInstance().makeAnnotations(solutions);
  }

  private Annotations loadContractAnnotations() {
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
    TIntObjectHashMap<Value> solutions = solver.solve();
    LOG.info("contract equations are solved");
    return BytecodeAnalysisConverter.getInstance().makeAnnotations(solutions);
  }


  // TODO: what follows was just copied/modified from BaseExternalAnnotationsManager
  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    List<AnnotationData> list = collectInferredAnnotations(listOwner);
    AnnotationData data = findByFQN(list, annotationFQN);
    if (data == null) {
      return null;
    }
    return data.getAnnotation(this);
  }

  @Nullable
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    List<AnnotationData> result = collectInferredAnnotations(listOwner);
    if (result == null || result.isEmpty()) return null;
    return ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY, new Function<AnnotationData, PsiAnnotation>() {
      @Override
      public PsiAnnotation fun(AnnotationData data) {
        return data.getAnnotation(ProjectBytecodeAnalysis.this);
      }
    });
  }

  @Nullable
  private static AnnotationData findByFQN(@NotNull List<AnnotationData> map, @NotNull final String annotationFQN) {
    return ContainerUtil.find(map, new Condition<AnnotationData>() {
      @Override
      public boolean value(AnnotationData data) {
        return data.annotationClassFqName.equals(annotationFQN);
      }
    });
  }

  // TODO the best way to synchronize?
  private synchronized List<AnnotationData> collectInferredAnnotations(PsiModifierListOwner listOwner) {
    if (myAnnotations == null) {
      loadAnnotations();
    }
    try {
      int key = getKey(listOwner);
      if (key == -1) {
        return NO_DATA;
      }
      SmartList<AnnotationData> result = new SmartList<AnnotationData>();
      ContainerUtil.addIfNotNull(result, myAnnotations.contracts.get(key));
      ContainerUtil.addIfNotNull(result, myAnnotations.outs.get(key));
      ContainerUtil.addIfNotNull(result, myAnnotations.params.get(key));
      return result;
    }
    catch (IOException e) {
      return NO_DATA;
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

  // interner for storing annotation FQN
  private final CharTableImpl charTable = new CharTableImpl();
  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };
  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    // synchronize during interning in charTable
    synchronized (charTable) {
      final DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager,
                                                                 new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null,
                                                                 charTable);
      final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
      if (!(element instanceof PsiAnnotation)) {
        throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
      }
      return (PsiAnnotation)element;
    }
  }
}

class Annotations {
  final TIntObjectHashMap<AnnotationData> outs;
  final TIntObjectHashMap<AnnotationData> params;
  final TIntObjectHashMap<AnnotationData> contracts;

  Annotations(TIntObjectHashMap<AnnotationData> outs, TIntObjectHashMap<AnnotationData> params, TIntObjectHashMap<AnnotationData> contracts) {
    this.outs = outs;
    this.params = params;
    this.contracts = contracts;
  }
}

class AnnotationData {
  @NotNull final String annotationClassFqName;
  @NotNull final String annotationParameters;
  private volatile PsiAnnotation annotation;

  AnnotationData(@NotNull String annotationClassFqName, @NotNull String annotationParameters) {
    this.annotationClassFqName = annotationClassFqName;
    this.annotationParameters = annotationParameters;
  }

  @NotNull
  PsiAnnotation getAnnotation(@NotNull ProjectBytecodeAnalysis context) {
    PsiAnnotation a = annotation;
    if (a == null) {
      annotation = a = context.createAnnotationFromText("@" + annotationClassFqName + (annotationParameters.isEmpty() ? "" : "("+annotationParameters+")"));
    }
    return a;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnnotationData data = (AnnotationData)o;

    return annotationClassFqName.equals(data.annotationClassFqName) && annotationParameters.equals(data.annotationParameters);
  }

  @Override
  public int hashCode() {
    int result = annotationClassFqName.hashCode();
    result = 31 * result + annotationParameters.hashCode();
    return result;
  }

}
