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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BytecodeAnalysisHandler extends AbstractProjectComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisHandler");
  protected final PsiManager myPsiManager;

  private MostlySingularMultiMap<String, AnnotationData> myAnnotations = new MostlySingularMultiMap<String, AnnotationData>();

  void setAnnotations(MostlySingularMultiMap<String, AnnotationData> annotations) {
    this.myAnnotations = annotations;
  }

  public BytecodeAnalysisHandler(Project project, PsiManager psiManager) {
    super(project);
    myPsiManager = psiManager;

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        doIndex();
      }
    });
    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        doIndex();
      }
    });
  }

  @Nullable
  public PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    String key = getExternalName(listOwner);
    if (key == null) {
      return null;
    }
    SmartList<AnnotationData> list = collectInferredAnnotations(listOwner);
    AnnotationData data = findByFQN(list, annotationFQN);
    if (data == null) {
      return null;
    }
    LOG.info("annotation: " + key + " " + data);
    return data.getAnnotation(this);
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

  @Nullable
  public PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner) {
    SmartList<AnnotationData> result = collectInferredAnnotations(listOwner);
    if (result == null || result.isEmpty()) return null;
    PsiAnnotation[] myResult = ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY, new Function<AnnotationData, PsiAnnotation>() {
      @Override
      public PsiAnnotation fun(AnnotationData data) {
        return data.getAnnotation(BytecodeAnalysisHandler.this);
      }
    });
    String key = getExternalName(listOwner);
    LOG.info("annotations: " + key + " " + result);
    return myResult;
  }

  private SmartList<AnnotationData> collectInferredAnnotations(PsiModifierListOwner listOwner) {
    String key = getExternalName(listOwner);
    if (key == null) {
      return null;
    }
    SmartList<AnnotationData> result = new SmartList<AnnotationData>();
    Iterable<AnnotationData> inferred = myAnnotations.get(key);
    ContainerUtil.addAll(result, inferred);
    return result;
  }

  @Nullable
  protected static String getExternalName(@NotNull PsiModifierListOwner listOwner) {
    return PsiFormatUtil.getExternalName(listOwner, false, Integer.MAX_VALUE);
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

  private void doIndex() {
    DumbService.getInstance(myProject).queueTask(new BytecodeAnalysisTask(myProject));
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
  PsiAnnotation getAnnotation(@NotNull BytecodeAnalysisHandler context) {
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

  @Override
  public String toString() {
    return "AnnotationData{" +
           "annotationClassFqName='" + annotationClassFqName + '\'' +
           ", annotationParameters='" + annotationParameters + '\'' +
           '}';
  }
}

class BytecodeAnalysisTask extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisTask");
  private final Project myProject;
  private long myFileCount = 0;

  BytecodeAnalysisTask(Project project) {
    myProject = project;
  }

  private VirtualFileVisitor<?> myCountFileVisitor = new VirtualFileVisitor() {
    @Override
    public boolean visitFile(@NotNull VirtualFile file) {
      if (!file.isDirectory() && "class".equals(file.getExtension())) {
        myFileCount ++;
      }
      return true;
    }
  };

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    indicator.setText("Bytecode analysis");
    HashSet<VirtualFile> classRoots = new HashSet<VirtualFile>();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      OrderEntry[] entries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : entries) {
        if (!(entry instanceof JdkOrderEntry)) {
          Collections.addAll(classRoots, entry.getFiles(OrderRootType.CLASSES));
        }
      }
    }
    // to display progress
    for (VirtualFile classRoot : classRoots) {
      VfsUtilCore.visitChildrenRecursively(classRoot, myCountFileVisitor);
    }
    indicator.setFraction(0.01);
    LOG.info("Found " + myFileCount + " classes to Index");
    ClassProcessor myClassProcessor = new ClassProcessor(indicator, myFileCount);
    for (VirtualFile classRoot : classRoots) {
      VfsUtilCore.visitChildrenRecursively(classRoot, myClassProcessor);
    }
    myProject.getComponent(BytecodeAnalysisHandler.class).setAnnotations(myClassProcessor.annotations());
  }


}
