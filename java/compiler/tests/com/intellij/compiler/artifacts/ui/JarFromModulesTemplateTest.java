package com.intellij.compiler.artifacts.ui;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.artifacts.PackagingElementsTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.JarArtifactType;
import com.intellij.packaging.impl.artifacts.JarFromModulesTemplate;

/**
 * @author nik
 */
public class JarFromModulesTemplateTest extends PackagingElementsTestCase {
  private Artifact myArtifact;

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    CompilerConfiguration.getInstance(getProject()).addResourceFilePattern("?*.MF");
  }

  public void testSimpleModule() throws Exception {
    final Module a = addModule("a", null);
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a");
  }

  public void testSimpleModuleWithMainClass() throws Exception {
    final VirtualFile file = createFile("src/A.java");
    final Module a = addModule("a", file.getParent());
    createFromTemplate(a, "A", file.getParent().getPath(), true);

    assertLayout("a.jar\n" +
                 " module:a");
    assertManifest("A", null);
  }

  public void testSimpleModuleWithExternalManifest() throws Exception {
    final VirtualFile file = createFile("src/A.java");
    final VirtualFile baseDir = file.getParent().getParent();
    final Module a = addModule("a", file.getParent());
    createFromTemplate(a, "A", baseDir.getPath(), true);

    assertLayout("a.jar\n" +
                 " META-INF/\n" +
                 "  file:" + baseDir.getPath() + "/META-INF/MANIFEST.MF\n" +
                 " module:a");
    assertManifest("A", null);

  }

  public void testModuleWithLibraryJar() throws Exception {
    final Module module = addModule("a", null);
    addProjectLibrary(module, "jdom", getJDomJar());
    createFromTemplate(module, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " extracted:" + getLocalJarPath(getJDomJar()) + "!/");
  }

  public void testModuleWithLibraryJarWithManifest() throws Exception {
    final VirtualFile file = createFile("src/A.java");
    final Module module = addModule("a", file.getParent());
    addProjectLibrary(module, "jdom", getJDomJar());
    createFromTemplate(module, null, file.getParent().getPath(), false);
    assertLayout("<root>\n" +
                 " a.jar\n" +
                 "  module:a\n" +
                 " lib:jdom(project)");
    assertManifest(null, "jdom.jar");
  }

  public void testSkipTestLibrary() throws Exception {
    final Module a = addModule("a", null);
    addProjectLibrary(a, "jdom", DependencyScope.TEST, getJDomJar());
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a");
  }

  public void testSkipProvidedLibrary() throws Exception {
    final Module a = addModule("a", null);
    addProjectLibrary(a, "jdom", DependencyScope.PROVIDED, getJDomJar());
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a");
  }

  public void testIncludeTests() {
    final Module a = addModule("a", null);
    addProjectLibrary(a, "jdom", DependencyScope.TEST, getJDomJar());
    createFromTemplate(a, null, null, true, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " module-tests:a\n" +
                 " extracted:" + getLocalJarPath(getJDomJar()) + "!/");
  }

  public void testTwoIndependentModules() throws Exception {
    final Module a = addModule("a", null);
    addModule("b", null);
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a");
  }

  public void testJarForProject() throws Exception {
    addModule("a", null);
    addModule("b", null);
    createFromTemplate(null, null, null, true);
    assertLayout(getProject().getName() + ".jar\n" +
                 " module:a\n" +
                 " module:b");
  }

  public void testDependentModule() {
    final Module a = addModule("a", null);
    final Module b = addModule("b", null);
    addModuleDependency(a, b);
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " module:b");
  }

  public void testDependentModuleWithLibrary() throws Exception {
    final Module a = addModule("a", null);
    final Module b = addModule("b", null);
    addModuleDependency(a, b);
    addProjectLibrary(b, "jdom", getJDomJar());
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " module:b\n" +
                 " extracted:" + getLocalJarPath(getJDomJar()) + "!/");
  }

  public void testExtractedLibraryWithDirectories() throws Exception {
    final VirtualFile dir = createDir("lib");
    final Module a = addModule("a", null);
    addProjectLibrary(a, "dir", dir);
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " lib:dir(project)");
  }

  public void testCopiedLibraryWithDirectories() throws Exception {
    final VirtualFile dir = createDir("lib");
    final Module a = addModule("a", null);
    addProjectLibrary(a, "dir", dir);
    final String basePath = getBaseDir().getPath();
    createFromTemplate(a, null, basePath, false);
    assertLayout("<root>\n" +
                 " a.jar\n" +
                 "  META-INF/\n" +
                 "   file:" + basePath + "/META-INF/MANIFEST.MF\n" +
                 "  module:a\n" +
                 "  dir:" + dir.getPath());
  }

  public void testExtractedLibraryWithJarsAndDirs() throws Exception {
    final VirtualFile dir = createDir("lib");
    final Module a = addModule("a", null);
    addProjectLibrary(a, "dir", dir, getJDomJar());
    createFromTemplate(a, null, null, true);
    assertLayout("a.jar\n" +
                 " module:a\n" +
                 " dir:" + dir.getPath() + "\n" +
                 " extracted:" + getLocalJarPath(getJDomJar()) + "!/");
  }

  public void testCopiedLibraryWithJarsAndDirs() throws Exception {
    final VirtualFile dir = createDir("lib");
    final Module a = addModule("a", null);
    addProjectLibrary(a, "dir", dir, getJDomJar());
    final String basePath = getBaseDir().getPath();
    createFromTemplate(a, null, basePath, false);
    assertLayout("<root>\n" +
                 " a.jar\n" +
                 "  META-INF/\n" +
                 "   file:" + basePath + "/META-INF/MANIFEST.MF\n" +
                 "  module:a\n" +
                 "  dir:" + dir.getPath() + "\n" +
                 " file:" + getLocalJarPath(getJDomJar()));
    assertManifest(null, "jdom.jar");
  }

  private void assertManifest(final String mainClass, final String classpath) {
    if (myArtifact.getArtifactType() instanceof JarArtifactType) {
      ArtifactsTestUtil.assertManifest(myArtifact, getContext(), mainClass, classpath);
    }
    else {
      final CompositePackagingElement<?> archive = (CompositePackagingElement<?>)myArtifact.getRootElement().getChildren().get(0);
      ArtifactsTestUtil.assertManifest(archive, getContext(), myArtifact.getArtifactType(), mainClass, classpath);
    }
  }

  private void assertLayout(final String expected) {
    assertLayout(myArtifact, expected);
  }

  private void createFromTemplate(final Module module, final String mainClassName, final String directoryForManifest,
                                  final boolean extractLibrariesToJar) {
    createFromTemplate(module, mainClassName, directoryForManifest, extractLibrariesToJar, false);
  }

  private void createFromTemplate(final Module module, final String mainClassName, final String directoryForManifest,
                                  final boolean extractLibrariesToJar, final boolean includeTests) {
    final JarFromModulesTemplate template = new JarFromModulesTemplate(getContext());
    final Module[] modules = module != null ? new Module[]{module} : ModuleManager.getInstance(getProject()).getModules();
    final ArtifactTemplate.NewArtifactConfiguration configuration =
      template.doCreateArtifact(modules, mainClassName, directoryForManifest, extractLibrariesToJar, includeTests);
    assertNotNull(configuration);
    myArtifact = addArtifact(configuration.getArtifactName(), configuration.getArtifactType(), configuration.getRootElement());
  }
}
