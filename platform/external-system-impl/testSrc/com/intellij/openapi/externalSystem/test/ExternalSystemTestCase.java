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
package com.intellij.openapi.externalSystem.test;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.TestFileSystemItem;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
public abstract class ExternalSystemTestCase extends UsefulTestCase {
  private File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;

  protected Project myProject;

  protected File myTestDir;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myProjectConfig;
  protected List<VirtualFile> myAllConfigs = new ArrayList<VirtualFile>();

  private List<String> myAllowedRoots = new ArrayList<String>();

  static {
    IdeaTestCase.initPlatformPrefix();
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ensureTempDirCreated();

    myTestDir = new File(ourTempDir, getTestName(false));
    FileUtil.ensureExists(myTestDir);

    setUpFixtures();
    myProject = myTestFixture.getProject();

    edt(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              setUpInWriteAction();
            }
            catch (Throwable e) {
              try {
                tearDown();
              }
              catch (Exception e1) {
                e1.printStackTrace();
              }
              throw new RuntimeException(e);
            }
          }
        });
      }
    });

    ArrayList<String> allowedRoots = new ArrayList<String>();
    collectAllowedRoots(allowedRoots);
    registerAllowedRoots(allowedRoots, myTestRootDisposable);

    CompilerTestUtil.enableExternalCompiler();
  }

  protected void collectAllowedRoots(List<String> roots) throws IOException {
  }

  public void registerAllowedRoots(List<String> roots, @NotNull Disposable disposable) {
    final List<String> newRoots = new ArrayList<String>(roots);
    newRoots.removeAll(myAllowedRoots);

    final String[] newRootsArray = ArrayUtil.toStringArray(newRoots);
    VfsRootAccess.allowRootAccess(newRootsArray);
    myAllowedRoots.addAll(newRoots);

    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        VfsRootAccess.disallowRootAccess(newRootsArray);
        myAllowedRoots.removeAll(newRoots);
      }
    });
  }

  private void ensureTempDirCreated() throws IOException {
    if (ourTempDir != null) return;

    ourTempDir = new File(FileUtil.getTempDirectory(), getTestsTempDir());
    FileUtil.delete(ourTempDir);
    FileUtil.ensureExists(ourTempDir);
  }

  protected abstract String getTestsTempDir();

  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).getFixture();
    myTestFixture.setUp();
  }

  protected void setUpInWriteAction() throws Exception {
    File projectDir = new File(myTestDir, "project");
    FileUtil.ensureExists(projectDir);
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  @After
  @Override
  public void tearDown() throws Exception {
    try {
      edt(new Runnable() {
        @Override
        public void run() {
          try {
            CompilerTestUtil.disableExternalCompiler(myProject);
            tearDownFixtures();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
      myProject = null;
      if (!FileUtil.delete(myTestDir) && myTestDir.exists()) {
        System.err.println("Cannot delete " + myTestDir);
        //printDirectoryContent(myDir);
        myTestDir.deleteOnExit();
      }
    }
    finally {
      super.tearDown();
      resetClassFields(getClass());
    }
  }

  private static void printDirectoryContent(File dir) {
    File[] files = dir.listFiles();
    if (files == null) return;

    for (File file : files) {
      System.out.println(file.getAbsolutePath());

      if (file.isDirectory()) {
        printDirectoryContent(file);
      }
    }
  }

  protected void tearDownFixtures() throws Exception {
    myTestFixture.tearDown();
    myTestFixture = null;
  }

  private void resetClassFields(final Class<?> aClass) {
    if (aClass == null) return;

    final Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      final int modifiers = field.getModifiers();
      if ((modifiers & Modifier.FINAL) == 0
          && (modifiers & Modifier.STATIC) == 0
          && !field.getType().isPrimitive()) {
        field.setAccessible(true);
        try {
          field.set(this, null);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    if (aClass == ExternalSystemTestCase.class) return;
    resetClassFields(aClass.getSuperclass());
  }

  @Override
  protected void runTest() throws Throwable {
    try {
      if (runInWriteAction()) {
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            ExternalSystemTestCase.super.runTest();
          }
        }.executeSilently().throwException();
      }
      else {
        ExternalSystemTestCase.super.runTest();
      }
    }
    catch (Exception throwable) {
      Throwable each = throwable;
      do {
        if (each instanceof HeadlessException) {
          printIgnoredMessage("Doesn't work in Headless environment");
          return;
        }
      }
      while ((each = each.getCause()) != null);
      throw throwable;
    }
  }

  @Override
  protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
    runnable.run();
  }

  protected boolean runInWriteAction() {
    return false;
  }

  protected static String getRoot() {
    if (SystemInfo.isWindows) return "c:";
    return "";
  }

  protected static String getEnvVar() {
    if (SystemInfo.isWindows) return "TEMP";
    else if (SystemInfo.isLinux) return "HOME";
    return "TMPDIR";
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  protected String pathFromBasedir(String relPath) {
    return pathFromBasedir(myProjectRoot, relPath);
  }

  protected static String pathFromBasedir(VirtualFile root, String relPath) {
    return FileUtil.toSystemIndependentName(root.getPath() + "/" + relPath);
  }

  protected Module createModule(String name) throws IOException {
    return createModule(name, StdModuleTypes.JAVA);
  }

  protected Module createModule(final String name, final ModuleType type) throws IOException {
    return new WriteCommandAction<Module>(myProject) {
      @Override
      protected void run(Result<Module> moduleResult) throws Throwable {
        VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
        Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), type.getId());
        PsiTestUtil.addContentRoot(module, f.getParent());
        moduleResult.setResult(module);
      }
    }.execute().getResultObject();
  }

  protected VirtualFile createProjectConfig(@NonNls String config) throws IOException {
    return myProjectConfig = createConfigFile(myProjectRoot, config);
  }

  protected VirtualFile createConfigFile(final VirtualFile dir, String config) throws IOException {
    final String configFileName = getExternalSystemConfigFileName();
    VirtualFile f = dir.findChild(configFileName);
    if (f == null) {
      f = new WriteAction<VirtualFile>() {
        @Override
        protected void run(Result<VirtualFile> result) throws Throwable {
          VirtualFile res = dir.createChildData(null, configFileName);
          result.setResult(res);
        }
      }.execute().getResultObject();
      myAllConfigs.add(f);
    }
    setFileContent(f, config, true);
    return f;
  }

  protected abstract String getExternalSystemConfigFileName();

  protected void createStdProjectFolders() throws IOException {
    createProjectSubDirs("src/main/java",
                         "src/main/resources",
                         "src/test/java",
                         "src/test/resources");
  }

  protected void createProjectSubDirs(String... relativePaths) throws IOException {
    for (String path : relativePaths) {
      createProjectSubDir(path);
    }
  }

  protected VirtualFile createProjectSubDir(String relativePath) throws IOException {
    File f = new File(getProjectPath(), relativePath);
    FileUtil.ensureExists(f);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected VirtualFile createProjectSubFile(String relativePath) throws IOException {
    File f = new File(getProjectPath(), relativePath);
    FileUtil.ensureExists(f.getParentFile());
    FileUtil.ensureCanCreateFile(f);
    final boolean created = f.createNewFile();
    if(!created) {
      throw new AssertionError("Unable to create the project sub file: " + f.getAbsolutePath());
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  @NotNull
  protected VirtualFile createProjectJarSubFile(String relativePath, Pair<ByteSequence, String>... contentEntries) throws IOException {
    assertTrue("Use 'jar' extension for JAR files: '" + relativePath + "'", FileUtilRt.extensionEquals(relativePath, "jar"));
    File f = new File(getProjectPath(), relativePath);
    FileUtil.ensureExists(f.getParentFile());
    FileUtil.ensureCanCreateFile(f);
    final boolean created = f.createNewFile();
    if (!created) {
      throw new AssertionError("Unable to create the project sub file: " + f.getAbsolutePath());
    }

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    JarOutputStream target = new JarOutputStream(new FileOutputStream(f), manifest);
    for (Pair<ByteSequence, String> contentEntry : contentEntries) {
      addJarEntry(contentEntry.first.getBytes(), contentEntry.second, target);
    }
    target.close();

    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
    assertNotNull(virtualFile);
    final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
    assertNotNull(jarFile);
    return jarFile;
  }

  private static void addJarEntry(byte[] bytes, String path, JarOutputStream target) throws IOException {
    JarEntry entry = new JarEntry(path.replace("\\", "/"));
    target.putNextEntry(entry);
    target.write(bytes);
    target.close();
  }

  protected VirtualFile createProjectSubFile(String relativePath, String content) throws IOException {
    VirtualFile file = createProjectSubFile(relativePath);
    setFileContent(file, content, false);
    return file;
  }


  protected void compileModules(final String... moduleNames) {
    compile(createModulesCompileScope(moduleNames));
  }

  protected void buildArtifacts(String... artifactNames) {
    compile(createArtifactsScope(artifactNames));
  }

  private void compile(final CompileScope scope) {
    try {
      CompilerTester tester = new CompilerTester(myProject, Arrays.asList(scope.getAffectedModules()));
      try {
        List<CompilerMessage> messages = tester.make(scope);
        for (CompilerMessage message : messages) {
          switch (message.getCategory()) {
            case ERROR:
              fail("Compilation failed with error: " + message.getMessage());
              break;
            case WARNING:
              System.out.println("Compilation warning: " + message.getMessage());
              break;
            case INFORMATION:
              break;
            case STATISTICS:
              break;
          }
        }
      }
      finally {
        tester.tearDown();
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  private CompileScope createModulesCompileScope(final String[] moduleNames) {
    final List<Module> modules = new ArrayList<Module>();
    for (String name : moduleNames) {
      modules.add(getModule(name));
    }
    return new ModuleCompileScope(myProject, modules.toArray(new Module[modules.size()]), false);
  }

  private CompileScope createArtifactsScope(String[] artifactNames) {
    List<Artifact> artifacts = new ArrayList<Artifact>();
    for (String name : artifactNames) {
      artifacts.add(ArtifactsTestUtil.findArtifact(myProject, name));
    }
    return ArtifactCompileScope.createArtifactsScope(myProject, artifacts);
  }

  protected Sdk setupJdkForModule(final String moduleName) {
    final Sdk sdk = true ? JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk() : createJdk("Java 1.5");
    ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk);
    return sdk;
  }

  protected static Sdk createJdk(String versionName) {
    return IdeaTestUtil.getMockJdk17(versionName);
  }

  protected Module getModule(final String name) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      Module m = ModuleManager.getInstance(myProject).findModuleByName(name);
      assertNotNull("Module " + name + " not found", m);
      return m;
    }
    finally {
      accessToken.finish();
    }
  }

  protected void assertExplodedLayout(String artifactName, String expected) {
    assertJarLayout(artifactName + " exploded", expected);
  }

  protected void assertJarLayout(String artifactName, String expected) {
    ArtifactsTestUtil.assertLayout(myProject, artifactName, expected);
  }

  protected void assertArtifactOutputPath(final String artifactName, final String expected) {
    ArtifactsTestUtil.assertOutputPath(myProject, artifactName, expected);
  }

  protected void assertArtifactOutputFileName(final String artifactName, final String expected) {
    ArtifactsTestUtil.assertOutputFileName(myProject, artifactName, expected);
  }

  protected void assertArtifactOutput(String artifactName, TestFileSystemItem fs) {
    final Artifact artifact = ArtifactsTestUtil.findArtifact(myProject, artifactName);
    final VirtualFile outputFile = artifact.getOutputFile();
    assert outputFile != null;
    final File file = VfsUtilCore.virtualToIoFile(outputFile);
    fs.assertFileEqual(file);
  }

  private static void setFileContent(final VirtualFile file, final String content, final boolean advanceStamps) throws IOException {
    new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        if (advanceStamps) {
          file.setBinaryContent(content.getBytes(CharsetToolkit.UTF8_CHARSET), -1, file.getTimeStamp() + 4000);
        }
        else {
          file.setBinaryContent(content.getBytes(CharsetToolkit.UTF8_CHARSET), file.getModificationStamp(), file.getTimeStamp());
        }
      }
    }.execute().getResultObject();
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, Collection<T> expected) {
    assertOrderedElementsAreEqual(actual, expected.toArray());
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<T> actual, Collection<T> expected) {
    assertEquals(new HashSet<T>(expected), new HashSet<T>(actual));
  }
  protected static void assertUnorderedPathsAreEqual(Collection<String> actual, Collection<String> expected) {
    assertEquals(new SetWithToString<String>(new THashSet<String>(expected, FileUtil.PATH_HASHING_STRATEGY)),
                 new SetWithToString<String>(new THashSet<String>(actual, FileUtil.PATH_HASHING_STRATEGY)));
  }

  protected static <T> void assertUnorderedElementsAreEqual(T[] actual, T... expected) {
    assertUnorderedElementsAreEqual(Arrays.asList(actual), expected);
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<T> actual, T... expected) {
    assertUnorderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<U>(actual);
    assertEquals(s, expected.length, actual.size());

    java.util.List<U> actualList = new ArrayList<U>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertEquals(s, expectedElement, actualElement);
    }
  }

  protected static <T> void assertContain(java.util.List<? extends T> actual, T... expected) {
    java.util.List<T> expectedList = Arrays.asList(expected);
    assertTrue("expected: " + expectedList + "\n" + "actual: " + actual.toString(), actual.containsAll(expectedList));
  }

  protected static <T> void assertDoNotContain(java.util.List<T> actual, T... expected) {
    java.util.List<T> actualCopy = new ArrayList<T>(actual);
    actualCopy.removeAll(Arrays.asList(expected));
    assertEquals(actual.toString(), actualCopy.size(), actual.size());
  }

  protected boolean ignore() {
    printIgnoredMessage(null);
    return true;
  }

  private void printIgnoredMessage(String message) {
    String toPrint = "Ignored";
    if (message != null) {
      toPrint += ", because " + message;
    }
    toPrint += ": " + getClass().getSimpleName() + "." + getName();
    System.out.println(toPrint);
  }

  private static class SetWithToString<T> extends AbstractSet<T> {

    private final Set<T> myDelegate;

    public SetWithToString(@NotNull Set<T> delegate) {
      myDelegate = delegate;
    }

    @Override
    public int size() {
      return myDelegate.size();
    }

    @Override
    public boolean contains(Object o) {
      return myDelegate.contains(o);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
      return myDelegate.iterator();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return myDelegate.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
      return myDelegate.equals(o);
    }

    @Override
    public int hashCode() {
      return myDelegate.hashCode();
    }
  }

}
