// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter;
import com.intellij.codeInspection.bytecodeAnalysis.ClassDataIndexer;
import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.IdeaDecompiler;

import java.security.MessageDigest;
import java.util.*;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIntegrationTest extends JavaCodeInsightFixtureTestCase {
  private static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();

  private MessageDigest myMessageDigest;
  private final List<String> myDiffs = new ArrayList<>();
  private boolean myNullableMethodRegistryValue;
  private VirtualFile myAnnotationsDir;

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
  }

  private void setUpExternalUpAnnotations() {
    String annotationsPath = PathManagerEx.getTestDataPath() + "/codeInspection/bytecodeAnalysis/annotations";
    myAnnotationsDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(annotationsPath);
    assertNotNull(myAnnotationsDir);

    ModuleRootModificationUtil.updateModel(myModule, new AsynchConsumer<ModifiableRootModel>() {
      @Override
      public void finished() { }

      @Override
      public void consume(ModifiableRootModel modifiableRootModel) {
        LibraryTable libraryTable = modifiableRootModel.getModuleLibraryTable();
        Library[] libs = libraryTable.getLibraries();
        for (Library library : libs) {
          Library.ModifiableModel libraryModel = library.getModifiableModel();
          libraryModel.addRoot(myAnnotationsDir, AnnotationOrderRootType.getInstance());
          libraryModel.commit();
        }
        Sdk sdk = modifiableRootModel.getSdk();
        if (sdk != null) {
          Sdk clone;
          try {
            clone = (Sdk)sdk.clone();
          }
          catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
          }
          SdkModificator sdkModificator = clone.getSdkModificator();
          sdkModificator.addRoot(myAnnotationsDir, AnnotationOrderRootType.getInstance());
          sdkModificator.commitChanges();
          modifiableRootModel.setSdk(clone);
        }
      }
    });

    VfsUtilCore.visitChildrenRecursively(myAnnotationsDir, new VirtualFileVisitor() {
    });
    myAnnotationsDir.refresh(false, true);
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
    ModuleRootModificationUtil.setModuleSdk(myModule, PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk17()));
    openDecompiledClass("java.lang.String");
    checkHasGutter("<html>External and <i>inferred</i> annotations available. Full signature:<p>\n" +
                   "<i>@Contract(pure = true)</i>&nbsp;\n" +
                   "public&nbsp;<b>String</b>(@NotNull&nbsp;String&nbsp;var1)</html>");
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
    System.err.println(ClassDataIndexer.ourIndexSizeStatistics);
    assertEmpty(myDiffs);
  }

  public void _testExportInferredAnnotations() {
    exportInferredAnnotations();
  }

  private void exportInferredAnnotations() {
    setUpLibraries();
    setUpExternalUpAnnotations();

    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assert rootPackage != null;

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    JavaRecursiveElementVisitor visitor = new AnnotationExporter(scope);

    rootPackage.accept(visitor);
  }

  private void checkMethodAnnotations(PsiMethod method) {
    if (ProjectBytecodeAnalysis.getInstance(getProject()).getKey(method, myMessageDigest) == null) {
      return;
    }

    String methodKey = PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE);

    {
      // @NotNull method
      String externalNotNullMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
      String inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";

      if (!externalNotNullMethodAnnotation.equals(inferredNotNullMethodAnnotation)) {
        myDiffs.add(methodKey + ": " + externalNotNullMethodAnnotation + " != " + inferredNotNullMethodAnnotation + "\n");
      }
    }

    {
      // @Nullable method
      String externalNullableMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
      String inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";

      if (!externalNullableMethodAnnotation.equals(inferredNullableMethodAnnotation)) {
        myDiffs.add(methodKey + ": " + externalNullableMethodAnnotation + " != " + inferredNullableMethodAnnotation + "\n");
      }
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      String parameterKey = PsiFormatUtil.getExternalName(parameter, false, Integer.MAX_VALUE);

      {
        // @NotNull parameter
        String externalNotNull = findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        String inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "null" : "@NotNull";
        if (!externalNotNull.equals(inferredNotNull)) {
          myDiffs.add(parameterKey + ": " + externalNotNull + " != " + inferredNotNull + "\n");
        }
      }

      {
        // @Nullable parameter
        String externalNullable = findExternalAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        String inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "null" : "@Nullable";
        if (!externalNullable.equals(inferredNullable)) {
          myDiffs.add(parameterKey + ": " + externalNullable + " != " + inferredNullable + "\n");
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
      myDiffs.add(methodKey + ": " + externalContractAnnotationText + " != " + inferredContractAnnotationText + "\n");
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

  private class AnnotationExporter extends JavaRecursiveElementVisitor {
    private final GlobalSearchScope myScope;

    public AnnotationExporter(GlobalSearchScope scope) {myScope = scope;}

    @Override
    public void visitPackage(PsiPackage aPackage) {
      // annotations are in class paths, but we are not interested in inferred annotations for them
      if ("org.intellij.lang.annotations".equals(aPackage.getQualifiedName())) {
        return;
      }
      for (PsiPackage subPackage : aPackage.getSubPackages(myScope)) {
        visitPackage(subPackage);
      }
      Map<String, Map<String, PsiNameValuePair[]>> packageAnnotations = new TreeMap<>();
      for (PsiClass aClass : aPackage.getClasses(myScope)) {
        processClass(aClass, packageAnnotations);
      }
      saveXmlForPackage(myAnnotationsDir, aPackage, convertToXml(packageAnnotations));
    }

    private void saveXmlForPackage(VirtualFile root, PsiPackage aPackage, XmlTag newContent) {
      XmlTag[] tags = newContent.getSubTags();
      if (tags.length == 0) return;
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          XmlFile xml = ExternalAnnotationsManagerImpl.createAnnotationsXml(root, aPackage.getQualifiedName(), aPackage.getManager());
          if (xml == null) {
            throw new IllegalStateException("Unable to get XML for package " + aPackage.getQualifiedName() + "; root = " + root);
          }
          XmlTag rootTag = xml.getRootTag();
          if (rootTag == null) {
            throw new IllegalStateException("No root tag in " + xml);
          }
          XmlTag[] existingItems = rootTag.getSubTags();
          if (existingItems.length > 0) {
            rootTag.deleteChildRange(ArrayUtil.getFirstElement(existingItems), ArrayUtil.getLastElement(existingItems));
          }
          rootTag.collapseIfEmpty();
          for (XmlTag item : tags) {
            rootTag.addSubTag(item, false);
          }
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(xml.getProject());
          Document doc = documentManager.getDocument(xml);
          documentManager.doPostponedOperationsAndUnblockDocument(doc);
          FileDocumentManager.getInstance().saveDocument(doc);
        }
      }.execute();
    }

    @NotNull
    private XmlTag convertToXml(Map<String, Map<String, PsiNameValuePair[]>> annotations) {
      String xmlContent = EntryStream.of(annotations)
        .mapValues(map -> EntryStream.of(map).mapKeyValue(ExternalAnnotationsManagerImpl::createAnnotationTag).joining())
        .mapKeyValue((externalName, content) -> "<item name=\'" + StringUtil.escapeXml(externalName) + "\'>\n" + content + "</item>")
        .joining("", "<root>\n", "</root>\n");
      XmlElementFactory factory = XmlElementFactory.getInstance(getProject());
      return factory.createTagFromText(xmlContent);
    }

    private void processClass(PsiClass aClass, Map<String, Map<String, PsiNameValuePair[]>> packageAnnotations) {
      for (PsiMethod method : aClass.getMethods()) {
        annotateMethod(method, packageAnnotations);
      }
      for (PsiClass innerClass : aClass.getInnerClasses()) {
        processClass(innerClass, packageAnnotations);
      }
    }

    private void annotateMethod(PsiMethod method, Map<String, Map<String, PsiNameValuePair[]>> packageAnnotations) {
      // @Contract
      PsiAnnotation inferredContractAnnotation = findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
      if (inferredContractAnnotation != null) {
        PsiNameValuePair[] attributes = inferredContractAnnotation.getParameterList().getAttributes();
        annotate(packageAnnotations, method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT, attributes);
      }

      // @NotNull method
      PsiAnnotation inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL);
      if (inferredNotNullMethodAnnotation != null) {
        annotate(packageAnnotations, method, AnnotationUtil.NOT_NULL, PsiNameValuePair.EMPTY_ARRAY);
      }
      // @Nullable method
      PsiAnnotation inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE);
      if (inferredNullableMethodAnnotation != null) {
        annotate(packageAnnotations, method, AnnotationUtil.NULLABLE, PsiNameValuePair.EMPTY_ARRAY);
      }

      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        // @NotNull parameter
        PsiAnnotation inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL);
        if (inferredNotNull != null) {
          annotate(packageAnnotations, parameter, AnnotationUtil.NOT_NULL, PsiNameValuePair.EMPTY_ARRAY);
        }
        // @Nullable parameter
        PsiAnnotation inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE);
        if (inferredNullable != null) {
          annotate(packageAnnotations, parameter, AnnotationUtil.NULLABLE, PsiNameValuePair.EMPTY_ARRAY);
        }
      }
    }

    private void annotate(Map<String, Map<String, PsiNameValuePair[]>> packageAnnotations,
                          PsiModifierListOwner owner,
                          String annotationFQN,
                          PsiNameValuePair[] attributes) {
      packageAnnotations.computeIfAbsent(PsiFormatUtil.getExternalName(owner, false, Integer.MAX_VALUE),
                                         k -> new TreeMap<>()).put(annotationFQN, attributes);
    }
  }
}
