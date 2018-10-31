// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter;
import com.intellij.codeInspection.bytecodeAnalysis.ClassDataIndexer;
import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.java.decompiler.IdeaDecompiler;

import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIntegrationTest extends LightCodeInsightFixtureTestCase {
  private static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private static final String INFERRED_TEST_METHOD =
    "org.apache.velocity.util.ExceptionUtils java.lang.Throwable createWithCause(java.lang.Class, java.lang.String, java.lang.Throwable)";
  private static final String EXTERNAL_TEST_METHOD = "java.lang.String String(java.lang.String)";

  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);

      List<RemoteRepositoryDescription> remoteRepositoryDescriptions =
        ContainerUtil.map(IntelliJProjectConfiguration.getRemoteRepositoryDescriptions(), repository ->
          new RemoteRepositoryDescription(repository.getId(), repository.getName(), repository.getUrl()));

      addFromMaven(module, model, "org.apache.velocity:velocity:1.7", remoteRepositoryDescriptions);
      addFromMaven(module, model, "commons-collections:commons-collections:3.2.1", remoteRepositoryDescriptions);

      VirtualFile annotationsRoot = getAnnotationsRoot();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          Library.ModifiableModel libModel = ((LibraryOrderEntry)entry).getLibrary().getModifiableModel();
          libModel.addRoot(annotationsRoot, AnnotationOrderRootType.getInstance());
          libModel.commit();
        }
      }
      Sdk sdk = model.getSdk();
      if (sdk != null) {
        sdk = PsiTestUtil.addRootsToJdk(sdk, AnnotationOrderRootType.getInstance(), annotationsRoot);
        model.setSdk(sdk);
      }

      Registry.get(ProjectBytecodeAnalysis.NULLABLE_METHOD).setValue(true, module);
    }

    public void addFromMaven(@NotNull Module module,
                             @NotNull ModifiableRootModel model,
                             String mavenCoordinates, List<RemoteRepositoryDescription> remoteRepositoryDescriptions) {
      RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(mavenCoordinates, true);
      Collection<OrderRoot> roots = JarRepositoryManager
        .loadDependenciesModal(module.getProject(), libraryProperties, false, false, null, remoteRepositoryDescriptions);
      LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
      Library.ModifiableModel libraryModel = tableModel.createLibrary(mavenCoordinates).getModifiableModel();
      for (OrderRoot root : roots) {
        libraryModel.addRoot(root.getFile(), root.getType());
      }
      libraryModel.commit();
      tableModel.commit();
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
    checkHasGutter("org.apache.velocity.util.ExceptionUtils",
                   "<html><i>Inferred</i> annotations available. Full signature:<p>\n" +
                   "<i>@Contract('null,_,_->null')</i> \n" +
                   "public static Throwable <b>createWithCause</b>(Class clazz,\n String message,\n Throwable cause)</html>");
  }

  public void testExternalAnnoGutter() {
    checkHasGutter("java.lang.String",
                   "<html>External and <i>inferred</i> annotations available. Full signature:<p>\n" +
                   "<i>@Contract(pure = true)</i> \n" +
                   "public <b>String</b>(@NotNull String var1)</html>");
  }

  private void checkHasGutter(String className, String expectedText) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());
    String documentText = myFixture.getEditor().getDocument().getText();
    assertThat(documentText).startsWith(IdeaDecompiler.BANNER);
    Set<String> gutters = myFixture.findAllGutters().stream()
      .map(GutterMark::getTooltipText)
      .filter(Objects::nonNull)
      .map(s -> s.replaceAll(" +", " ").replace("&nbsp;", " ").replace("&quot;", "'").replace("&gt;", ">"))
      .collect(Collectors.toSet());
    assertThat(gutters).contains(expectedText);
  }

  public void testSdkAndLibAnnotations() {
    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assertNotNull(rootPackage);

    MessageDigest digest = BytecodeAnalysisConverter.getMessageDigest();
    List<String> diffs = new ArrayList<>();
    JavaRecursiveElementVisitor visitor = new PackageVisitor(GlobalSearchScope.moduleWithLibrariesScope(myModule)) {
      @Override
      protected void visitSubPackage(PsiPackage aPackage, PsiClass[] classes) {
        for (PsiClass aClass : classes) {
          for (PsiMethod method : aClass.getMethods()) {
            checkMethodAnnotations(method);
          }
        }
      }

      private void checkMethodAnnotations(PsiMethod method) {
        if (ProjectBytecodeAnalysis.getInstance(getProject()).getKey(method, digest) == null) return;
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
    };
    rootPackage.accept(visitor);
    System.err.println(ClassDataIndexer.ourIndexSizeStatistics);
    assertEmpty(diffs);
  }

  @SuppressWarnings("unused")
  public void _testExportInferredAnnotations() {
    PsiPackage rootPackage = JavaPsiFacade.getInstance(getProject()).findPackage("");
    assertNotNull(rootPackage);

    VirtualFile annotationsRoot = getAnnotationsRoot();
    JavaRecursiveElementVisitor visitor = new PackageVisitor(GlobalSearchScope.moduleWithLibrariesScope(myModule)) {
      @Override
      protected void visitSubPackage(PsiPackage aPackage, PsiClass[] classes) {
        System.out.println(aPackage.getQualifiedName());
        Map<String, Map<String, PsiNameValuePair[]>> annotations = new TreeMap<>();
        for (PsiClass aClass : classes) processClass(aClass, annotations);
        saveXmlForPackage(aPackage.getQualifiedName(), annotations, annotationsRoot);
      }

      private void processClass(PsiClass aClass, Map<String, Map<String, PsiNameValuePair[]>> annotations) {
        for (PsiMethod method : aClass.getMethods()) annotateMethod(method, annotations);
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
          .mapValues(map -> EntryStream.of(map).mapKeyValue(ExternalAnnotationsManagerImpl::createAnnotationTag).joining())
          .mapKeyValue((externalName, content) -> "<item name=\'" + StringUtil.escapeXml(externalName) + "\'>\n" + content.trim() + "\n</item>\n")
          .joining("", "<root>\n", "</root>");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          XmlFile xml = ExternalAnnotationsManagerImpl.createAnnotationsXml(root, packageName, getPsiManager());
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
    public void visitPackage(PsiPackage aPackage) {
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