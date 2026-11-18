// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

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
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.javadoc.MissingPackageInfoInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.List;
import java.util.Objects;

/**
 * Checks that a package finds its {@code package-info.java} when no directory belongs to the package.
 * This is the case under a source root whose {@code packageMatchesDirectory} is {@code false}, and for single-file source roots.
 * A package can also have a {@code package-info.java} both in a directory and in a single-file source root.
 */
public final class MissingPackageInfoInspectionMixModeTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String PACKAGE_INFO_TEXT = "@Deprecated\npackage com.example.server;\n";
  private static final String SERVER_TEXT = "package com.example.server;\n\npublic class Server {\n}\n";
  private static final String PACKAGE_INFO_PATH = "singleFileRoots/a/b/package-info.java";
  private static final String SERVER_PATH = "singleFileRoots/a/b/Server.java";

  private static final LayoutDescriptor DIRECTORY_ROOT = new LayoutDescriptor(false, null, List.of());
  private static final LayoutDescriptor SINGLE_FILE_ROOTS = new LayoutDescriptor(true, "", List.of(PACKAGE_INFO_PATH, SERVER_PATH));
  private static final LayoutDescriptor SINGLE_FILE_ROOTS_WITH_PREFIX =
    new LayoutDescriptor(true, "com.example.server", List.of(PACKAGE_INFO_PATH, SERVER_PATH));
  private static final LayoutDescriptor MIXED = new LayoutDescriptor(false, "", List.of(PACKAGE_INFO_PATH));
  private static final LayoutDescriptor DIRECTORY_AND_SINGLE_FILE_ROOT = new LayoutDescriptor(true, "", List.of(PACKAGE_INFO_PATH));

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return switch (getTestName(false)) {
      case "SingleFileRoots" -> SINGLE_FILE_ROOTS;
      case "SingleFileRootsWithPackagePrefix" -> SINGLE_FILE_ROOTS_WITH_PREFIX;
      case "SingleFileRootPackageInfoAndDirectoryRootClass" -> MIXED;
      case "PackageInfoInDirectoryAndInSingleFileRoot" -> DIRECTORY_AND_SINGLE_FILE_ROOT;
      default -> DIRECTORY_ROOT;
    };
  }

  public void testDirectoryRootWithoutDirectoryPackages() {
    VirtualFile packageInfo = myFixture.addFileToProject("a/b/package-info.java", PACKAGE_INFO_TEXT).getVirtualFile();
    VirtualFile server = myFixture.addFileToProject("a/b/Server.java", SERVER_TEXT).getVirtualFile();
    doTest(packageInfo, server);
  }

  public void testSingleFileRoots() {
    doTest(singleFileRoot(PACKAGE_INFO_PATH), singleFileRoot(SERVER_PATH));
  }

  public void testSingleFileRootsWithPackagePrefix() {
    doTest(singleFileRoot(PACKAGE_INFO_PATH), singleFileRoot(SERVER_PATH));
  }

  public void testSingleFileRootPackageInfoAndDirectoryRootClass() {
    VirtualFile server = myFixture.addFileToProject("a/b/Server.java", SERVER_TEXT).getVirtualFile();
    doTest(singleFileRoot(PACKAGE_INFO_PATH), server);
  }

  public void testPackageInfoInDirectoryAndInSingleFileRoot() {
    VirtualFile directoryPackageInfo =
      myFixture.addFileToProject("com/example/server/package-info.java", "@Marker\npackage com.example.server;\n").getVirtualFile();
    VirtualFile marker = myFixture.addFileToProject("com/example/server/Marker.java",
                                                    "package com.example.server;\n\npublic @interface Marker {\n}\n").getVirtualFile();
    doTest(singleFileRoot(PACKAGE_INFO_PATH), directoryPackageInfo, marker);
    PsiPackage aPackage = Objects.requireNonNull(JavaPsiFacade.getInstance(getProject()).findPackage("com.example.server"));
    assertEquals(2, aPackage.getPackageInfoFiles(GlobalSearchScope.allScope(getProject())).size());
    PsiModifierList annotations = Objects.requireNonNull(aPackage.getAnnotationList());
    assertNotNull("The package com.example.server has no @Marker", annotations.findAnnotation("com.example.server.Marker"));
  }

  private void doTest(VirtualFile @NotNull ... files) {
    myFixture.enableInspections(new MissingPackageInfoInspection());
    for (VirtualFile file : files) {
      myFixture.configureFromExistingVirtualFile(file);
      myFixture.checkHighlighting();
    }
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage("com.example.server");
    assertNotNull("The package com.example.server is not found", aPackage);
    PsiModifierList annotations = aPackage.getAnnotationList();
    assertNotNull("The package com.example.server has no annotations", annotations);
    assertNotNull("The package com.example.server has no @Deprecated", annotations.findAnnotation("java.lang.Deprecated"));
  }

  private static @NotNull VirtualFile singleFileRoot(@NotNull String path) {
    VirtualFile file = LightPlatformTestCase.getSourceRoot().getParent().findFileByRelativePath(path);
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
