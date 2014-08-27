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
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.AsynchConsumer;
import org.jetbrains.annotations.Contract;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIntegrationTest extends JavaCodeInsightFixtureTestCase {
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  private InferredAnnotationsManager myInferredAnnotationsManager;
  private ExternalAnnotationsManager myExternalAnnotationsManager;
  private MessageDigest myMessageDigest;
  private List<String> diffs = new ArrayList<String>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setUpLibraries();
    setUpExternalUpAnnotations();

    myInferredAnnotationsManager = InferredAnnotationsManager.getInstance(myModule.getProject());
    myExternalAnnotationsManager = ExternalAnnotationsManager.getInstance(myModule.getProject());
    myMessageDigest = BytecodeAnalysisConverter.getMessageDigest();
  }

  private void setUpLibraries() {
    VirtualFile lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib");
    assertNotNull(lib);
    PsiTestUtil.addLibrary(myModule, "velocity", lib.getPath(), new String[]{"/velocity.jar!/"}, new String[]{});
  }

  private void setUpExternalUpAnnotations() {
    String annotationsPath = PathManagerEx.getTestDataPath() + "/codeInspection/bytecodeAnalysis/annotations";
    final VirtualFile annotationsDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(annotationsPath);
    assertNotNull(annotationsDir);

    ModuleRootModificationUtil.updateModel(myModule, new AsynchConsumer<ModifiableRootModel>() {
      @Override
      public void finished() {
      }

      @Override
      public void consume(ModifiableRootModel modifiableRootModel) {
        final LibraryTable libraryTable = modifiableRootModel.getModuleLibraryTable();
        Library[] libs = libraryTable.getLibraries();
        for (Library library : libs) {
          final Library.ModifiableModel libraryModel = library.getModifiableModel();
          libraryModel.addRoot(annotationsDir, AnnotationOrderRootType.getInstance());
          libraryModel.commit();
        }
        Sdk sdk = modifiableRootModel.getSdk();
        if (sdk != null) {
          SdkModificator sdkModificator = sdk.getSdkModificator();
          sdkModificator.addRoot(annotationsDir, AnnotationOrderRootType.getInstance());
          sdkModificator.commitChanges();
        }
      }
    });

    VfsUtilCore.visitChildrenRecursively(annotationsDir, new VirtualFileVisitor() { });
    annotationsDir.refresh(false, true);
  }

  public void testSdkAndLibAnnotations() {

    final PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assert rootPackage != null;

    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {
      @Override
      public void visitPackage(PsiPackage aPackage) {
        for (PsiPackage subPackage : aPackage.getSubPackages(scope)) {
          visitPackage(subPackage);
        }
        for (PsiClass aClass : aPackage.getClasses(scope)) {
          for (PsiMethod method : aClass.getMethods()) {
            checkMethodAnnotations(method);
          }
        }
      }
    };

    rootPackage.accept(visitor);
    assertEmpty(diffs);
  }

  private void checkMethodAnnotations(PsiMethod method) {

    if (ProjectBytecodeAnalysis.getKey(method, myMessageDigest) == null) {
      return;
    }

    // not null-result
    String externalOutAnnotation =
      myExternalAnnotationsManager.findExternalAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
    String inferredOutAnnotation =
      myInferredAnnotationsManager.findInferredAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
    String methodKey = PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE);

    if (!externalOutAnnotation.equals(inferredOutAnnotation)) {
      diffs.add(methodKey + ": " + externalOutAnnotation + " != " + inferredOutAnnotation);
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      String parameterKey = PsiFormatUtil.getExternalName(parameter, false, Integer.MAX_VALUE);

      {
        // @NotNull
        String externalNotNull =
          myExternalAnnotationsManager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        String inferredNotNull =
          myInferredAnnotationsManager.findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        if (!externalNotNull.equals(inferredNotNull)) {
          diffs.add(parameterKey + ": " + externalNotNull + " != " + inferredNotNull);
        }
      }

      {
        // @Nullable
        String externalNullable =
          myExternalAnnotationsManager.findExternalAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        String inferredNullable =
          myInferredAnnotationsManager.findInferredAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        if (!externalNullable.equals(inferredNullable)) {
          diffs.add(parameterKey + ": " + externalNullable + " != " + inferredNullable);
        }
      }
    }

    PsiAnnotation externalContractAnnotation =
      myExternalAnnotationsManager.findExternalAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    PsiAnnotation inferredContractAnnotation =
      myInferredAnnotationsManager.findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);

    String externalContractAnnotationString =
      externalContractAnnotation == null ? "null" : "@Contract(" + AnnotationUtil.getStringAttributeValue(externalContractAnnotation, null) + ")";
    String inferredContractAnnotationString =
      inferredContractAnnotation == null ? "null" : "@Contract(" + AnnotationUtil.getStringAttributeValue(inferredContractAnnotation, null) + ")";

    if (!externalContractAnnotationString.equals(inferredContractAnnotationString)) {
      diffs.add(methodKey + ": " + externalContractAnnotationString + " != " + inferredContractAnnotationString);
    }

  }

}
