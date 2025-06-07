// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.codeInsight.daemon.impl.LineMarkerSettingsImpl;
import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisSuppressor;
import com.intellij.codeInspection.bytecodeAnalysis.ClassDataIndexer;
import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.java.decompiler.IdeaDecompilerKt.IDEA_DECOMPILER_BANNER;

public class BytecodeAnalysisIntegrationTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;
  private static final String INFERRED_TEST_METHOD =
    "org.apache.velocity.util.ExceptionUtils java.lang.Throwable createWithCause(java.lang.Class, java.lang.String, java.lang.Throwable)";
  private static final String EXTERNAL_TEST_METHOD = "java.lang.String String(java.lang.String)";
  
  private static final BytecodeAnalysisSuppressor TEST_SUPPRESSOR = file -> file.getPath().endsWith("/ParserTokenManager.class");

  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);

      addJetBrainsAnnotations(model);
      MavenDependencyUtil.addFromMaven(model, "org.apache.velocity:velocity:1.7");
      MavenDependencyUtil.addFromMaven(model, "commons-collections:commons-collections:3.2.1");

      VirtualFile annotationsRoot = getAnnotationsRoot();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          Library.ModifiableModel libModel = ((LibraryOrderEntry)entry).getLibrary().getModifiableModel();
          libModel.addRoot(annotationsRoot, AnnotationOrderRootType.getInstance());
          libModel.commit();
        }
      }
      Registry.get(ProjectBytecodeAnalysis.NULLABLE_METHOD).setValue(true, module);
    }

    @Override
    public Sdk getSdk() {
      Sdk sdk = super.getSdk();
      sdk = PsiTestUtil.modifyJdkRoots(sdk, modificator -> {
        modificator.setName(modificator.getName() + "-RootType" + AnnotationOrderRootType.getInstance().name());
        modificator.removeRoots(AnnotationOrderRootType.getInstance());
      });

      sdk = PsiTestUtil.addRootsToJdk(sdk, AnnotationOrderRootType.getInstance(), getAnnotationsRoot());
      return sdk;
    }
  };

  private static VirtualFile getAnnotationsRoot() {
    String annotationsPath = PathManagerEx.getTestDataPath() + "/codeInspection/bytecodeAnalysis/annotations";
    VirtualFile annotationsDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(annotationsPath);
    assertNotNull(annotationsDir);
    return annotationsDir;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  public void testInferredAnnoGutter() {
    LineMarkerSettingsImpl settings = (LineMarkerSettingsImpl)LineMarkerSettings.getSettings();
    final InferredContractAnnotationsLineMarkerProvider descriptor = new InferredContractAnnotationsLineMarkerProvider();
    settings.setEnabled(descriptor, true);
    Disposer.register(getTestRootDisposable(), () -> settings.resetEnabled(descriptor));

    checkHasGutter("org.apache.velocity.util.ExceptionUtils",
                   """
                     <html><p><i>Inferred</i> annotations available. Full signature:
                     <pre><code><b><i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.Contract"><span style="color:#808000;">Contract</span></a><span style="">(</span><span style="color:#008000;font-weight:bold;">"null,_,_->null"</span><span style="">)</span></i></b>\s
                     <span style="color:#000000;">Throwable</span> <span style="color:#000000;">createWithCause</span><span style="">(</span><span style="color:#000000;">Class</span><span style="">,</span>
                     <span style="color:#000000;">String</span><span style="">,</span>
                     <span style="color:#000000;">Throwable</span><span style="">)</span></code></pre></html>""");
  }

  public void testExternalAnnoGutter() {
    checkHasGutter("java.lang.String",
                   """
                     <html><p>External annotations available. Full signature:
                     <pre><code><span style="color:#000000;">String</span><span style="">(</span><b><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.NotNull"><span style="color:#808000;">NotNull</span></a></b> <span style="color:#000080;font-weight:bold;">char</span><span style="">[]</span><span style="">,</span>
                     <span style="color:#000080;font-weight:bold;">int</span><span style="">,</span>
                     <span style="color:#000080;font-weight:bold;">int</span><span style="">)</span></code></pre></html>""");
  }

  private void checkHasGutter(String className, String expectedText) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.moduleWithLibrariesScope(getModule()));
    assertNotNull(psiClass);
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());
    String documentText = myFixture.getEditor().getDocument().getText();
    assertThat(documentText).startsWith(IDEA_DECOMPILER_BANNER);
    Set<String> gutters = myFixture.findAllGutters().stream()
      .map(GutterMark::getTooltipText)
      .filter(Objects::nonNull)
      .map(s -> s.replaceAll(" +", " ").replace("&nbsp;", " ").replace("&quot;", "'").replace("&gt;", ">"))
      .collect(Collectors.toSet());
    assertThat(gutters).contains(expectedText);
  }

  public void testSdkAndLibAnnotations() {
    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), BytecodeAnalysisSuppressor.EP_NAME, TEST_SUPPRESSOR,
                                           getTestRootDisposable());
    assertNotNull(rootPackage);

    List<String> diffs = new ArrayList<>();
    JavaRecursiveElementVisitor visitor = new PackageVisitor(GlobalSearchScope.moduleWithLibrariesScope(getModule())) {
      @Override
      protected void visitSubPackage(PsiPackage aPackage, PsiClass[] classes) {
        for (PsiClass aClass : classes) {
          for (PsiMethod method : aClass.getMethods()) {
            if (method.isPhysical()) {
              checkMethodAnnotations(method, diffs);
            }
          }
          for (PsiField field : aClass.getFields()) {
            checkFieldAnnotations(field, diffs);
          }
        }
      }
    };
    rootPackage.accept(visitor);
    LOG.debug(String.valueOf(ClassDataIndexer.ourIndexSizeStatistics));
    assertEmpty(diffs);
  }

  private void checkMethodAnnotations(PsiMethod method, List<? super String> diffs) {
    if (ProjectBytecodeAnalysis.getInstance(getProject()).getKey(method) == null) return;
    String methodKey = PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE);
    if (INFERRED_TEST_METHOD.equals(methodKey)) return;

    String externalNotNullMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
    String inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
    if (!externalNotNullMethodAnnotation.equals(inferredNotNullMethodAnnotation)) {
      diffs.add(methodKey + ": " + externalNotNullMethodAnnotation + " != " + inferredNotNullMethodAnnotation + "\n");
    }
    String externalNullableMethodAnnotation = findExternalAnnotation(method, AnnotationUtil.NULLABLE) == null ? "-" : "@Nullable";
    String inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE) == null ? "-" : "@Nullable";
    if (!externalNullableMethodAnnotation.equals(inferredNullableMethodAnnotation)) {
      diffs.add(methodKey + ": " + externalNullableMethodAnnotation + " != " + inferredNullableMethodAnnotation + "\n");
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      String parameterKey = PsiFormatUtil.getExternalName(parameter, false, Integer.MAX_VALUE);
      String externalNotNull = findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
      String inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
      if (!externalNotNull.equals(inferredNotNull)) {
        diffs.add(parameterKey + ": " + externalNotNull + " != " + inferredNotNull + "\n");
      }
      String externalNullable = findExternalAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "-" : "@Nullable";
      String inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE) == null ? "-" : "@Nullable";
      if (!externalNullable.equals(inferredNullable)) {
        diffs.add(parameterKey + ": " + externalNullable + " != " + inferredNullable + "\n");
      }
    }

    if (!EXTERNAL_TEST_METHOD.equals(methodKey)) {
      PsiAnnotation externalContractAnnotation = findExternalAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
      PsiAnnotation inferredContractAnnotation = findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
      String externalContractAnnotationText = externalContractAnnotation == null ? "-" : externalContractAnnotation.getText();
      String inferredContractAnnotationText = inferredContractAnnotation == null ? "-" : inferredContractAnnotation.getText();
      if (!externalContractAnnotationText.equals(inferredContractAnnotationText)) {
        diffs.add(methodKey + ": " + externalContractAnnotationText + " != " + inferredContractAnnotationText + "\n");
      }
    }
  }

  private void checkFieldAnnotations(PsiField field, List<? super String> diffs) {
    if (ProjectBytecodeAnalysis.getInstance(getProject()).getKey(field) == null) return;
    String fieldKey = PsiFormatUtil.getExternalName(field, false, Integer.MAX_VALUE);

    String externalNotNullMethodAnnotation = findExternalAnnotation(field, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
    String inferredNotNullMethodAnnotation = findInferredAnnotation(field, AnnotationUtil.NOT_NULL) == null ? "-" : "@NotNull";
    if (!externalNotNullMethodAnnotation.equals(inferredNotNullMethodAnnotation)) {
      diffs.add(fieldKey + ": " + externalNotNullMethodAnnotation + " != " + inferredNotNullMethodAnnotation + "\n");
    }
  }

  @SuppressWarnings("unused")
  public void _testExportInferredAnnotations() {
    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assertNotNull(rootPackage);
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), BytecodeAnalysisSuppressor.EP_NAME, TEST_SUPPRESSOR,
                                           getTestRootDisposable());

    VirtualFile annotationsRoot = getAnnotationsRoot();
    JavaRecursiveElementVisitor visitor = new PackageVisitor(GlobalSearchScope.moduleWithLibrariesScope(getModule())) {
      @Override
      protected void visitSubPackage(PsiPackage aPackage, PsiClass[] classes) {
        LOG.debug(aPackage.getQualifiedName());
        Map<String, Map<String, PsiNameValuePair[]>> annotations = new TreeMap<>();
        for (PsiClass aClass : classes) processClass(aClass, annotations);
        saveXmlForPackage(aPackage.getQualifiedName(), annotations, annotationsRoot);
      }

      private void processClass(PsiClass aClass, Map<String, Map<String, PsiNameValuePair[]>> annotations) {
        for (PsiMethod method : aClass.getMethods()) annotateMethod(method, annotations);
        for (PsiField field : aClass.getFields()) annotateField(field, annotations);
        for (PsiClass innerClass : aClass.getInnerClasses()) processClass(innerClass, annotations);
      }

      private void annotateMethod(PsiMethod method, Map<String, Map<String, PsiNameValuePair[]>> annotations) {
        String methodKey = PsiFormatUtil.getExternalName(method, false, Integer.MAX_VALUE);
        if (INFERRED_TEST_METHOD.equals(methodKey)) return;

        if (!EXTERNAL_TEST_METHOD.equals(methodKey)) {
          PsiAnnotation inferredContractAnnotation = findInferredAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
          if (inferredContractAnnotation != null) {
            PsiNameValuePair[] attributes = inferredContractAnnotation.getParameterList().getAttributes();
            annotate(annotations, method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT, attributes);
          }
        }

        PsiAnnotation inferredNotNullMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NOT_NULL);
        if (inferredNotNullMethodAnnotation != null) {
          annotate(annotations, method, AnnotationUtil.NOT_NULL, PsiNameValuePair.EMPTY_ARRAY);
        }
        PsiAnnotation inferredNullableMethodAnnotation = findInferredAnnotation(method, AnnotationUtil.NULLABLE);
        if (inferredNullableMethodAnnotation != null) {
          annotate(annotations, method, AnnotationUtil.NULLABLE, PsiNameValuePair.EMPTY_ARRAY);
        }

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          PsiAnnotation inferredNotNull = findInferredAnnotation(parameter, AnnotationUtil.NOT_NULL);
          if (inferredNotNull != null) {
            annotate(annotations, parameter, AnnotationUtil.NOT_NULL, PsiNameValuePair.EMPTY_ARRAY);
          }
          PsiAnnotation inferredNullable = findInferredAnnotation(parameter, AnnotationUtil.NULLABLE);
          if (inferredNullable != null) {
            annotate(annotations, parameter, AnnotationUtil.NULLABLE, PsiNameValuePair.EMPTY_ARRAY);
          }
        }
      }

      private void annotateField(PsiField field, Map<String, Map<String, PsiNameValuePair[]>> annotations) {
        PsiAnnotation inferredNotNullMethodAnnotation = findInferredAnnotation(field, AnnotationUtil.NOT_NULL);
        if (inferredNotNullMethodAnnotation != null) {
          annotate(annotations, field, AnnotationUtil.NOT_NULL, PsiNameValuePair.EMPTY_ARRAY);
        }
      }

      private void annotate(Map<String, Map<String, PsiNameValuePair[]>> annotations,
                            PsiModifierListOwner owner,
                            String annotationFQN,
                            PsiNameValuePair[] attributes) {
        String key = PsiFormatUtil.getExternalName(owner, false, Integer.MAX_VALUE);
        annotations.computeIfAbsent(key, k -> new TreeMap<>()).put(annotationFQN, attributes);
      }

      private void saveXmlForPackage(String packageName, Map<String, Map<String, PsiNameValuePair[]>> annotations, VirtualFile root) {
        if (annotations.isEmpty()) return;
        String xmlContent = EntryStream.of(annotations)
          .mapValues(map -> EntryStream.of(map).mapKeyValue(ModCommandAwareExternalAnnotationsManager::createAnnotationTag).joining())
          .mapKeyValue((externalName, content) -> "<item name='" + StringUtil.escapeXmlEntities(externalName) +
                                                  "'>\n" + content.trim() + "\n</item>\n")
          .joining("", "<root>\n", "</root>");
        PsiDirectory directory = getPsiManager().findDirectory(root);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          XmlFile xml = ExternalAnnotationsManagerImpl.createAnnotationsXml(null, directory, packageName);
          if (xml == null) throw new IllegalStateException("Unable to get XML for package " + packageName + "; root = " + root);
          xml.getVirtualFile().refresh(false, false);
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
          Document document = documentManager.getDocument(xml);
          document.setText(xmlContent);
          documentManager.commitDocument(document);
          CodeStyleManager.getInstance(getProject()).reformat(xml);
          FileDocumentManager.getInstance().saveDocument(document);
        });
      }
    };
    rootPackage.accept(visitor);
  }

  @Nullable
  private PsiAnnotation findInferredAnnotation(PsiModifierListOwner owner, String fqn) {
    return ProjectBytecodeAnalysis.getInstance(getProject()).findInferredAnnotation(owner, fqn);
  }

  @Nullable
  private PsiAnnotation findExternalAnnotation(PsiModifierListOwner owner, String fqn) {
    return ExternalAnnotationsManager.getInstance(getProject()).findExternalAnnotation(owner, fqn);
  }

  private abstract static class PackageVisitor extends JavaRecursiveElementVisitor {
    private final GlobalSearchScope myScope;

    PackageVisitor(GlobalSearchScope scope) {
      myScope = scope;
    }

    @Override
    public void visitPackage(@NotNull PsiPackage aPackage) {
      if (!"org.intellij.lang.annotations".equals(aPackage.getQualifiedName())) {
        for (PsiPackage subPackage : aPackage.getSubPackages(myScope)) {
          visitPackage(subPackage);
        }
        visitSubPackage(aPackage, aPackage.getClasses(myScope));
      }
    }

    protected abstract void visitSubPackage(PsiPackage aPackage, PsiClass[] classes);
  }
}