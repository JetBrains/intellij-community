// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.codeInsight.javadoc.JavaDocInfoHtmlPrinter;
import com.intellij.ide.actions.QualifiedNameProviderUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.OpenProjectTaskBuilderKt.createTestOpenProjectOptions;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.disposableFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@NullMarked
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class JavaDocInfoGeneratorJdkTest {
  private static final int MAX_UNRESOLVED_LINKS = 15;
  private static final Map<String, List<String>> EXCLUDED_UNRESOLVED_LINKS = Map.of(
    // TODO {@inheritDoc} link resolution issues, see IDEA-395508
    "METHOD java.lang.classfile.constantpool.ConstantDynamicEntry#typeKind",
    List.of("ldc2_w", "ldc", "ldc_w"),
    "METHOD javax.tools.ForwardingJavaFileManager#getJavaFileForOutputForOriginatingFiles",
    List.of("Filer.createSourceFile", "Elements.getFileObjectOf"),
    "METHOD javax.tools.ForwardingJavaFileManager#getFileForOutputForOriginatingFiles",
    List.of("Filer.createResource", "Elements.getFileObjectOf"),
    "METHOD javax.swing.JDesktopPane#addImpl",
    List.of("LayoutManager2.addLayoutComponent(Component,Object)", "LayoutManager.addLayoutComponent(String,Component)"),

    // TODO resolution to methods (and classes?) with non-actualized type (eg. Comparable<T>#compareTo(T t)), see IDEA-395513
    "METHOD javax.imageio.plugins.tiff.TIFFTagSet#getTagNumbers",
    List.of("Integer.compareTo(Object)"),
    "METHOD javax.imageio.plugins.tiff.TIFFTagSet#getTagNames",
    List.of("String.compareTo(Object)")
  );

  private final TestFixture<Path> myProjectPath = tempPathFixture();
  private final TestFixture<Project> myProject = projectFixture(myProjectPath, createTestOpenProjectOptions(), true);
  private final TestFixture<Module> myModule = moduleFixture(myProject, myProjectPath, true, "JAVA_MODULE");
  private final TestFixture<Path> myJdkPath = tempPathFixture();
  private final TestFixture<Disposable> myDisposable = disposableFixture();

  @BeforeAll
  void setUpProject() throws Exception {
    var project = myProject.get();
    var item = JdkListDownloader.getInstance().downloadModelForJdkInstaller(null,JdkPredicate.Companion.forCurrentProcess())
      .stream().filter(item1-> item1.matchesVendor("Oracle")).findFirst();
    assertTrue(item.isPresent());

    var installer = JdkInstaller.getInstance();
    var request = installer.prepareJdkInstallation(item.get(), myJdkPath.get().resolve("jdk"));
    installer.installJdk(request, null, project);

    var sdk = JavaSdk.getInstance().createJdk(getClass().getSimpleName(), request.getJavaHome().toString(), false);
    WriteAction.runAndWait(() -> {
      ProjectJdkTable.getInstance().addJdk(sdk, myDisposable.get());
      ProjectRootManager.getInstance(project).setProjectSdk(sdk);
      ModuleRootModificationUtil.setSdkInherited(myModule.get());
    });
    IndexingTestUtil.waitUntilIndexesAreReady(project);
  }

  @Test
  void jdkDocumentationHasNoUnresolvedLinks() {
    var project = myProject.get();
    var unresolvedLinks = ReadAction.nonBlocking(() -> {
      var collector = new UnresolvedLinksCollector(MAX_UNRESOLVED_LINKS, EXCLUDED_UNRESOLVED_LINKS);
      ensureJdkHasJavadocSources(project);

      var sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      var scope = new JdkScope(project, sdk.getRootProvider().getFiles(OrderRootType.CLASSES), VirtualFile.EMPTY_ARRAY, sdk.getName());
      AllClassesSearch.search(scope, project).forEach(psiClass -> {
        if (collector.isLimitReached()) return false;
        if (shouldTestClass(psiClass)) {
          if (!testDocumentation(project, psiClass, collector)) return false;
          for (var method : filterPrivate(psiClass.getMethods())) {
            if (!testDocumentation(project, method, collector)) return false;
          }
          for (var field : filterPrivate(psiClass.getFields())) {
            if (!testDocumentation(project, field, collector)) return false;
          }
        }
        return !collector.isLimitReached();
      });
      return collector.getUnresolvedLinks();
    }).inSmartMode(project).executeSynchronously();
    assertEquals(List.of(), unresolvedLinks, "Unresolved links: " + unresolvedLinks);
  }

  /// {@return Whether the file content should be tested.}
  ///
  /// In practice, only the public API is not getting filtered.
  private static boolean shouldTestClass(PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE) || modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      return false;
    }
    PsiFile file = psiClass.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) return false;
    String packageName = javaFile.getPackageName();
    if (packageName.startsWith("jdk.internal") ||
        packageName.startsWith("com.sun.") ||
        packageName.startsWith("sun.") ||
        packageName.startsWith("jdk.")) {
      return false;
    }
    if (packageName.contains(".internal.")) return false;
    return true;
  }

  private static boolean testDocumentation(Project project,
                                           PsiJavaDocumentedElement element,
                                           UnresolvedLinksCollector collector) {
    if (element.getNavigationElement() instanceof PsiJavaDocumentedElement originalElement && originalElement.getDocComment() == null) {
      return !collector.isLimitReached();
    }

    var printer = new TestPrinter();
    JavaDocInfoGeneratorFactory.getBuilder(project)
      .setPsiElement(element)
      .setIsGenerationForRenderedDoc(true)
      .setPrinter(printer)
      .create()
      .generateDocInfo(null);

    return collector.addLinks(getPrettyName(element), printer.getUnresolvedLinks());
  }

  private static <T extends PsiDocCommentOwner> Iterable<T> filterPrivate(T[] methods) {
    return Arrays.stream(methods).filter(method -> {
      PsiModifierList modifierList = method.getModifierList();
      return !(modifierList.hasModifierProperty(PsiModifier.PRIVATE) || modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    }).toList();
  }

  /// {@return prettified name for easier reading}
  private static String getPrettyName(PsiElement element) {
    String qualifiedName = QualifiedNameProviderUtil.getQualifiedName(element);
    if (qualifiedName == null) {
      qualifiedName = element.toString();
    }
    return switch (element) {
      case PsiClass _ -> "CLASS ";
      case PsiMethod _ -> "METHOD ";
      case PsiField _ -> "FIELD ";
      default -> element.toString();
    } + qualifiedName;
  }

  record UnresolvedLink(String elementName, String label) {
  }

  static final class UnresolvedLinksCollector {
    private final int maxLinks;
    private final Map<String, List<String>> exclusions;
    private final List<UnresolvedLink> unresolvedLinks = new ArrayList<>();
    private boolean limitReached;

    UnresolvedLinksCollector(int maxLinks, Map<String, List<String>> exclusions) {
      if (maxLinks < 1) throw new IllegalArgumentException("The link limit must be positive");
      this.maxLinks = maxLinks;
      this.exclusions = exclusions;
    }

    boolean addLinks(String elementName, List<String> labels) {
      if (limitReached) return false;

      var excludedLabels = exclusions.getOrDefault(elementName, List.of());
      for (String label : labels) {
        if (!excludedLabels.contains(label)) {
          unresolvedLinks.add(new UnresolvedLink(elementName, label));
        }
      }
      limitReached = unresolvedLinks.size() >= maxLinks;
      return !limitReached;
    }

    boolean isLimitReached() {
      return limitReached;
    }

    List<UnresolvedLink> getUnresolvedLinks() {
      return List.copyOf(unresolvedLinks);
    }
  }

  /// Verifies the sdk comes with the javadoc by checking the string class content
  private void ensureJdkHasJavadocSources(Project project) {
    var sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    assertNotNull(sdk);
    assertTrue(sdk.getRootProvider().getFiles(OrderRootType.SOURCES).length > 0, "The JDK has no sources");
    var stringClass = JavaPsiFacade.getInstance(project)
      .findClass(CommonClassNames.JAVA_LANG_STRING, GlobalSearchScope.moduleWithLibrariesScope(myModule.get()));
    assertNotNull(stringClass);
    var sourceClass = assertInstanceOf(PsiClass.class, stringClass.getNavigationElement());
    assertNotNull(sourceClass.getDocComment(), "String has no Javadoc source");
  }

  /// Variant of [com.intellij.codeInsight.javadoc.JavaDocInfoPrinter] that is aware of unresolved links for testing purposes
  private static class TestPrinter extends JavaDocInfoHtmlPrinter {
    private final List<String> unresolvedLinks = new ArrayList<>();

    private List<String> getUnresolvedLinks() {
      return unresolvedLinks;
    }

    @Override
    public StringBuilder printUnresolvedLink(StringBuilder builder, String label) {
      unresolvedLinks.add(label);
      return super.printUnresolvedLink(builder, label);
    }
  }
}
