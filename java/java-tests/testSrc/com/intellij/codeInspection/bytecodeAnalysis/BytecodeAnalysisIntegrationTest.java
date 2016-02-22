/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.IdeaDecompiler;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIntegrationTest extends JavaCodeInsightFixtureTestCase {
  private static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  private MessageDigest myMessageDigest;
  private List<String> myDiffs = new ArrayList<>();
  private boolean myNullableMethodRegistryValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMessageDigest = BytecodeAnalysisConverter.getMessageDigest();
    RegistryValue registryValue = Registry.get(ProjectBytecodeAnalysis.NULLABLE_METHOD);
    myNullableMethodRegistryValue = registryValue.asBoolean();
    registryValue.setValue(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Registry.get(ProjectBytecodeAnalysis.NULLABLE_METHOD).setValue(myNullableMethodRegistryValue);
    }
    finally {
      super.tearDown();
    }
  }

  private void setUpLibraries() {
    String libDir = PathManagerEx.getCommunityHomePath() + "/lib";
    PsiTestUtil.addLibrary(myModule, "velocity", libDir, new String[]{"/velocity.jar!/"}, ArrayUtil.EMPTY_STRING_ARRAY);
    //PsiTestUtil.addLibrary(myModule, "velocity", libDir, "velocity.jar");
  }

  private void setUpExternalUpAnnotations() {
    String annotationsPath = PathManagerEx.getTestDataPath() + "/codeInspection/bytecodeAnalysis/annotations";
    VirtualFile annotationsDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(annotationsPath);
    assertNotNull(annotationsDir);

    ModuleRootModificationUtil.updateModel(myModule, new AsynchConsumer<ModifiableRootModel>() {
      @Override
      public void finished() { }

      @Override
      public void consume(ModifiableRootModel modifiableRootModel) {
        LibraryTable libraryTable = modifiableRootModel.getModuleLibraryTable();
        Library[] libs = libraryTable.getLibraries();
        for (Library library : libs) {
          Library.ModifiableModel libraryModel = library.getModifiableModel();
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

  private void openDecompiledClass(String name) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(name, GlobalSearchScope.allScope(getProject()));
    assertNotNull(psiClass);
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());

    String documentText = myFixture.getEditor().getDocument().getText();
    assertTrue(documentText, documentText.startsWith(IdeaDecompiler.BANNER));
  }

  public void testInferredAnnoGutter() {
    setUpLibraries();
    openDecompiledClass("org.apache.velocity.util.ExceptionUtils");
    checkHasGutter("<html><i>Inferred</i> annotations available. Full signature:<p>\n" +
                   "<i>@Contract(&quot;null,_,_-&gt;null&quot;)</i>&nbsp;\n" +
                   "public static&nbsp;Throwable&nbsp;<b>createWithCause</b>(");
  }

  public void testExternalAnnoGutter() {
    setUpExternalUpAnnotations();
    openDecompiledClass("java.lang.Boolean");
    checkHasGutter("<html>External and <i>inferred</i> annotations available. Full signature:<p>\n" +
                   "@org.jetbrains.annotations.Contract(&quot;null-&gt;false&quot;)&nbsp;\n" +
                   "private static&nbsp;boolean&nbsp;<b>toBoolean</b>(@org.jetbrains.annotations.Nullable&nbsp;String&nbsp;var0)</html>");
  }

  private void checkHasGutter(String expectedText) {
    Collection<String> gutters = ContainerUtil.mapNotNull(myFixture.findAllGutters(), GutterMark::getTooltipText);
    String contractMark = ContainerUtil.find(gutters, mark -> mark.contains(expectedText));
    assertNotNull(StringUtil.join(gutters, "\n"), contractMark);
  }

  public void testSdkAndLibAnnotations() {
    setUpLibraries();
    setUpExternalUpAnnotations();

    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assert rootPackage != null;

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {
      @Override
      public void visitPackage(PsiPackage aPackage) {
        // annotations are in class paths, but we are not interested in inferred annotations for them
        if ("org.intellij.lang.annotations".equals(aPackage.getQualifiedName())) {
          return;
        }
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
    assertEmpty(myDiffs);
  }

  @SuppressWarnings("unused")
  public void _testExportInferredAnnotations() {
    exportInferredAnnotations();
  }

  private void exportInferredAnnotations() {
    setUpLibraries();
    setUpExternalUpAnnotations();

    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assert rootPackage != null;

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {
      @Override
      public void visitPackage(PsiPackage aPackage) {
        // annotations are in class paths, but we are not interested in inferred annotations for them
        if ("org.intellij.lang.annotations".equals(aPackage.getQualifiedName())) {
          return;
        }
        for (PsiPackage subPackage : aPackage.getSubPackages(scope)) {
          visitPackage(subPackage);
        }
        for (PsiClass aClass : aPackage.getClasses(scope)) {
          processClass(aClass);
          for (PsiClass innerClass : aClass.getInnerClasses()) {
            processClass(innerClass);
          }
        }
      }

      private void processClass(PsiClass aClass) {
        for (PsiMethod method : aClass.getMethods()) {
          exportMethodAnnotations(method);
        }
        for (PsiClass innerClass : aClass.getInnerClasses()) {
          processClass(innerClass);
        }
      }
    };

    rootPackage.accept(visitor);
  }

  private void exportMethodAnnotations(PsiMethod method) {
    // @Contract
    PsiAnnotation inferredContractAnnotation = findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    if (inferredContractAnnotation != null) {
      PsiNameValuePair[] attributes = inferredContractAnnotation.getParameterList().getAttributes();
      ExternalAnnotationsManager.getInstance(myModule.getProject()).annotateExternally(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT, method.getContainingFile(), attributes);
    }

    {
      // @NotNull method
      PsiAnnotation inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL);
      if (inferredNotNullMethodAnnotation != null) {
        ExternalAnnotationsManager.getInstance(myModule.getProject()).annotateExternally(method, AnnotationUtil.NOT_NULL, method.getContainingFile(), null);
      }
    }

    {
      // @Nullable method
      PsiAnnotation inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE);
      if (inferredNullableMethodAnnotation != null) {
        ExternalAnnotationsManager.getInstance(myModule.getProject()).annotateExternally(method, AnnotationUtil.NULLABLE, method.getContainingFile(), null);
      }
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      {
        // @NotNull parameter
        PsiAnnotation inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL);
        if (inferredNotNull != null) {
          ExternalAnnotationsManager.getInstance(myModule.getProject()).annotateExternally(parameter, AnnotationUtil.NOT_NULL, method.getContainingFile(), null);
        }
      }

      {
        // @Nullable parameter
        PsiAnnotation inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE);
        if (inferredNullable != null) {
          ExternalAnnotationsManager.getInstance(myModule.getProject()).annotateExternally(parameter, AnnotationUtil.NULLABLE, method.getContainingFile(), null);
        }
      }
    }
  }

  private void checkMethodAnnotations(PsiMethod method) {
    if (ProjectBytecodeAnalysis.getKey(method, myMessageDigest) == null) {
      return;
    }

    String methodKey = PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE);

    {
      // @NotNull method
      String externalNotNullMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
      String inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";

      if (!externalNotNullMethodAnnotation.equals(inferredNotNullMethodAnnotation)) {
        myDiffs.add(methodKey + ": " + externalNotNullMethodAnnotation + " != " + inferredNotNullMethodAnnotation);
      }
    }

    {
      // @Nullable method
      String externalNullableMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
      String inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";

      if (!externalNullableMethodAnnotation.equals(inferredNullableMethodAnnotation)) {
        myDiffs.add(methodKey + ": " + externalNullableMethodAnnotation + " != " + inferredNullableMethodAnnotation);
      }
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      String parameterKey = PsiFormatUtil.getExternalName(parameter, false, Integer.MAX_VALUE);

      {
        // @NotNull parameter
        String externalNotNull = findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        String inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        if (!externalNotNull.equals(inferredNotNull)) {
          myDiffs.add(parameterKey + ": " + externalNotNull + " != " + inferredNotNull);
        }
      }

      {
        // @Nullable parameter
        String externalNullable = findExternalAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        String inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        if (!externalNullable.equals(inferredNullable)) {
          myDiffs.add(parameterKey + ": " + externalNullable + " != " + inferredNullable);
        }
      }
    }

    // @Contract
    PsiAnnotation externalContractAnnotation = findExternalAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    PsiAnnotation inferredContractAnnotation = findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);

    String externalContractAnnotationText =
      externalContractAnnotation == null ? "null" : externalContractAnnotation.getText();
    String inferredContractAnnotationText =
      inferredContractAnnotation == null ? "null" : inferredContractAnnotation.getText();

    if (!externalContractAnnotationText.equals(inferredContractAnnotationText)) {
      myDiffs.add(methodKey + ": " + externalContractAnnotationText + " != " + inferredContractAnnotationText);
    }
  }

  @Nullable
  private PsiAnnotation findInferredAnnotation(PsiModifierListOwner owner, String fqn) {
    return ProjectBytecodeAnalysis.getInstance(getProject()).findInferredAnnotation(owner, fqn);
  }

  @Nullable
  private PsiAnnotation findExternalAnnotation(PsiModifierListOwner owner, String fqn) {
    return ExternalAnnotationsManager.getInstance(myModule.getProject()).findExternalAnnotation(owner, fqn);
  }
}
