// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.javadoc.MissingPackageInfoInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that a package finds its {@code package-info.java} when no directory belongs to the package.
 * This is the case under a source root whose {@code packageMatchesDirectory} is {@code false}, and for single-file source roots.
 * A package can also have a {@code package-info.java} both in a directory and in a single-file source root.
 */
@TestApplication
public final class MissingPackageInfoInspectionMixModeTest {
  private static final String PACKAGE_INFO_TEXT = "@Deprecated\npackage com.example.server;\n";
  private static final String SERVER_TEXT = "package com.example.server;\n\npublic class Server {\n}\n";
  private static final String PACKAGE_INFO_PATH = "singleFileRoots/a/b/package-info.java";
  private static final String SERVER_PATH = "singleFileRoots/a/b/Server.java";

  private static final TestFixture<LightJavaProject> directoryRootProject =
    lightJavaProjectFixture(new LayoutDescriptor(false, null, List.of()));
  private static final TestFixture<LightJavaProject> singleFileRootsProject =
    lightJavaProjectFixture(new LayoutDescriptor(true, "", List.of(PACKAGE_INFO_PATH, SERVER_PATH)));
  private static final TestFixture<LightJavaProject> singleFileRootsWithPrefixProject =
    lightJavaProjectFixture(new LayoutDescriptor(true, "com.example.server", List.of(PACKAGE_INFO_PATH, SERVER_PATH)));
  private static final TestFixture<LightJavaProject> mixedProject =
    lightJavaProjectFixture(new LayoutDescriptor(false, "", List.of(PACKAGE_INFO_PATH)));
  private static final TestFixture<LightJavaProject> directoryAndSingleFileRootProject =
    lightJavaProjectFixture(new LayoutDescriptor(true, "", List.of(PACKAGE_INFO_PATH)));

  private final TestFixture<LightJavaCodeInsightTestFixture> directoryRootFixture = lightJavaCodeInsightFixture(directoryRootProject);
  private final TestFixture<LightJavaCodeInsightTestFixture> singleFileRootsFixture =
    lightJavaCodeInsightFixture(singleFileRootsProject);
  private final TestFixture<LightJavaCodeInsightTestFixture> singleFileRootsWithPrefixFixture =
    lightJavaCodeInsightFixture(singleFileRootsWithPrefixProject);
  private final TestFixture<LightJavaCodeInsightTestFixture> mixedFixture = lightJavaCodeInsightFixture(mixedProject);
  private final TestFixture<LightJavaCodeInsightTestFixture> directoryAndSingleFileRootFixture =
    lightJavaCodeInsightFixture(directoryAndSingleFileRootProject);

  @Test
  public void testDirectoryRootWithoutDirectoryPackages() {
    LightJavaCodeInsightTestFixture fixture = directoryRootFixture.get();
    VirtualFile packageInfo = fixture.addFileToProject("a/b/package-info.java", PACKAGE_INFO_TEXT).getVirtualFile();
    VirtualFile server = fixture.addFileToProject("a/b/Server.java", SERVER_TEXT).getVirtualFile();
    doTest(fixture, packageInfo, server);
  }

  @Test
  public void testSingleFileRoots() {
    LightJavaCodeInsightTestFixture fixture = singleFileRootsFixture.get();
    doTest(fixture, singleFileRoot(singleFileRootsProject, PACKAGE_INFO_PATH), singleFileRoot(singleFileRootsProject, SERVER_PATH));
  }

  @Test
  public void testSingleFileRootsWithPackagePrefix() {
    LightJavaCodeInsightTestFixture fixture = singleFileRootsWithPrefixFixture.get();
    doTest(fixture, singleFileRoot(singleFileRootsWithPrefixProject, PACKAGE_INFO_PATH),
           singleFileRoot(singleFileRootsWithPrefixProject, SERVER_PATH));
  }

  @Test
  public void testSingleFileRootPackageInfoAndDirectoryRootClass() {
    LightJavaCodeInsightTestFixture fixture = mixedFixture.get();
    VirtualFile server = fixture.addFileToProject("a/b/Server.java", SERVER_TEXT).getVirtualFile();
    doTest(fixture, singleFileRoot(mixedProject, PACKAGE_INFO_PATH), server);
  }

  @Test
  public void testPackageInfoInDirectoryAndInSingleFileRoot() {
    LightJavaCodeInsightTestFixture fixture = directoryAndSingleFileRootFixture.get();
    VirtualFile directoryPackageInfo =
      fixture.addFileToProject("com/example/server/package-info.java", "@Marker\npackage com.example.server;\n").getVirtualFile();
    VirtualFile marker = fixture.addFileToProject("com/example/server/Marker.java",
                                                  "package com.example.server;\n\npublic @interface Marker {\n}\n").getVirtualFile();
    doTest(fixture, singleFileRoot(directoryAndSingleFileRootProject, PACKAGE_INFO_PATH), directoryPackageInfo, marker);
    ReadAction.runBlocking(() -> {
      PsiPackage aPackage = Objects.requireNonNull(JavaPsiFacade.getInstance(fixture.getProject()).findPackage("com.example.server"));
      assertEquals(2, aPackage.getPackageInfoFiles(GlobalSearchScope.allScope(fixture.getProject())).size());
      PsiModifierList annotations = Objects.requireNonNull(aPackage.getAnnotationList());
      assertNotNull(annotations.findAnnotation("com.example.server.Marker"), "The package com.example.server has no @Marker");
    });
  }

  private static void doTest(@NotNull LightJavaCodeInsightTestFixture fixture, VirtualFile @NotNull ... files) {
    fixture.enableInspections(new MissingPackageInfoInspection());
    for (VirtualFile file : files) {
      fixture.configureFromExistingVirtualFile(file);
      fixture.checkHighlighting();
    }
    ReadAction.runBlocking(() -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(fixture.getProject()).findPackage("com.example.server");
      assertNotNull(aPackage, "The package com.example.server is not found");
      PsiModifierList annotations = aPackage.getAnnotationList();
      assertNotNull(annotations, "The package com.example.server has no annotations");
      assertNotNull(annotations.findAnnotation("java.lang.Deprecated"), "The package com.example.server has no @Deprecated");
    });
  }

  private static @NotNull VirtualFile singleFileRoot(@NotNull TestFixture<LightJavaProject> project, @NotNull String path) {
    VirtualFile file = project.get().getSourceRoot().getParent().findFileByRelativePath(path);
    return Objects.requireNonNull(file, path);
  }

  /**
   * Sets {@code packageMatchesDirectory} on the light source root.
   * When {@code singleFileRootPrefix} is not {@code null}, it also adds each of {@code singleFileRootPaths} as a single-file source
   * root next to the light source root, with that package prefix.
   */
  private static final class LayoutDescriptor extends ProjectDescriptor {
    private final boolean myPackageMatchesDirectory;
    private final @Nullable String mySingleFileRootPrefix;
    private final @NotNull List<String> mySingleFileRootPaths;

    LayoutDescriptor(boolean packageMatchesDirectory, @Nullable String singleFileRootPrefix, @NotNull List<String> singleFileRootPaths) {
      super(LanguageLevel.HIGHEST);
      myPackageMatchesDirectory = packageMatchesDirectory;
      mySingleFileRootPrefix = singleFileRootPrefix;
      mySingleFileRootPaths = singleFileRootPaths;
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        JavaSourceRootProperties properties =
          Objects.requireNonNull(sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES));
        properties.setPackageMatchesDirectory(myPackageMatchesDirectory);
      }
      if (mySingleFileRootPrefix == null) return;
      VirtualFile parent = Objects.requireNonNull(contentEntry.getFile()).getParent();
      for (String path : mySingleFileRootPaths) {
        String text = path.equals(PACKAGE_INFO_PATH) ? PACKAGE_INFO_TEXT : SERVER_TEXT;
        VirtualFile file = VfsTestUtil.createFile(parent, path, text);
        model.addContentEntry(file).addSourceFolder(file, false, mySingleFileRootPrefix);
      }
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof LayoutDescriptor other &&
             myPackageMatchesDirectory == other.myPackageMatchesDirectory &&
             Objects.equals(mySingleFileRootPrefix, other.mySingleFileRootPrefix) &&
             mySingleFileRootPaths.equals(other.mySingleFileRootPaths);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myPackageMatchesDirectory, mySingleFileRootPrefix, mySingleFileRootPaths);
    }
  }
}
