// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static com.intellij.util.PathUtil.toSystemIndependentName;

/**
 * @author Vladislav.Soroka
 */
public abstract class ExternalSystemTestCase extends UsefulTestCase {

  private static final BiPredicate<Object, Object> EQUALS_PREDICATE = (t, u) -> Objects.equals(t, u);

  private File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;
  protected Project myProject;
  protected File myTestDir;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myProjectConfig;
  protected List<VirtualFile> myAllConfigs = new ArrayList<>();
  protected @Nullable WSLDistribution myWSLDistribution;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpFixtures();
    myProject = myTestFixture.getProject();

    setupWsl();
    ensureTempDirCreated();

    String testDirName = "testDir" + System.currentTimeMillis();
    myTestDir = new File(ourTempDir, testDirName);
    FileUtil.ensureExists(myTestDir);


    EdtTestUtil.runInEdtAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
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
    }));

    List<String> allowedRoots = new ArrayList<>();
    collectAllowedRoots(allowedRoots);
    if (!allowedRoots.isEmpty()) {
      VfsRootAccess.allowRootAccess(myTestFixture.getTestRootDisposable(), ArrayUtilRt.toStringArray(allowedRoots));
    }
  }

  protected void setupWsl() {
    String wslMsId = System.getProperty("wsl.distribution.name");
    if (wslMsId == null) return;
    List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
    if (distributions.isEmpty()) throw new IllegalStateException("no WSL distributions configured!");
    myWSLDistribution = distributions.stream().filter(it -> wslMsId.equals(it.getMsId())).findFirst().orElseThrow(
      () -> new IllegalStateException("Distribution " + wslMsId + " was not found"));
  }

  protected void collectAllowedRoots(List<String> roots) {
  }

  public static Collection<String> collectRootsInside(@NotNull String root) {
    final List<String> roots = new SmartList<>();
    roots.add(root);
    FileUtil.processFilesRecursively(new File(root), file -> {
      try {
        String path = file.getCanonicalPath();
        if (!FileUtil.isAncestor(path, path, false)) {
          roots.add(path);
        }
      }
      catch (IOException ignore) {
      }
      return true;
    });

    return roots;
  }

  private void ensureTempDirCreated() throws IOException {
    if (ourTempDir != null) return;

    if (myWSLDistribution == null) {
      ourTempDir = new File(FileUtil.getTempDirectory(), getTestsTempDir());
    }
    else {
      ourTempDir = new File(myWSLDistribution.getWindowsPath("/tmp"), getTestsTempDir());
    }


    FileUtil.delete(ourTempDir);
    FileUtil.ensureExists(ourTempDir);
  }

  protected abstract String getTestsTempDir();

  protected void setUpFixtures() throws Exception {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName(), useDirectoryBasedStorageFormat()).getFixture();
    myTestFixture.setUp();
  }

  protected boolean useDirectoryBasedStorageFormat() {
    return false;
  }

  protected void setUpInWriteAction() throws Exception {
    setUpProjectRoot();
  }

  protected void setUpProjectRoot() throws Exception {
    File projectDir = new File(myTestDir, "project");
    FileUtil.ensureExists(projectDir);
    myProjectRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(projectDir);
  }

  @Override
  public void tearDown() throws Exception {
    new RunAll(
      () -> {
        if (myProject != null && !myProject.isDisposed()) {
          PathKt.delete(ProjectUtil.getExternalConfigurationDir(myProject));
        }
      },
      () -> EdtTestUtil.runInEdtAndWait(() -> tearDownFixtures()),
      () -> myProject = null,
      () -> PathKt.delete(myTestDir.toPath()),
      () -> ExternalSystemProgressNotificationManagerImpl.assertListenersReleased(),
      () -> ExternalSystemProgressNotificationManagerImpl.cleanupListeners(),
      () -> super.tearDown(),
      () -> resetClassFields(getClass())
    ).run();
  }

  protected void tearDownFixtures() throws Exception {
    RunAll.runAll(
      () -> myTestFixture.tearDown(),
      () -> myTestFixture = null
    );
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
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable);
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

  protected static String getRoot() {
    if (SystemInfo.isWindows) return "c:";
    return "";
  }

  protected String getProjectPath() {
    return myProjectRoot.getPath();
  }

  protected String getParentPath() {
    return myProjectRoot.getParent().getPath();
  }

  @SystemIndependent
  protected String path(@NotNull String relativePath) {
    return toSystemIndependentName(file(relativePath).getPath());
  }

  protected File file(@NotNull String relativePath) {
    return new File(getProjectPath(), relativePath);
  }

  protected Module createModule(final String name, final ModuleType type) {
    try {
      return WriteCommandAction.writeCommandAction(myProject).compute(() -> {
        VirtualFile f = createProjectSubFile(name + "/" + name + ".iml");
        Module module = ModuleManager.getInstance(myProject).newModule(f.getPath(), type.getId());
        PsiTestUtil.addContentRoot(module, f.getParent());
        return module;
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected VirtualFile createProjectConfig(@NonNls String config) {
    return myProjectConfig = createConfigFile(myProjectRoot, config);
  }

  protected VirtualFile createConfigFile(final VirtualFile dir, String config) {
    final String configFileName = getExternalSystemConfigFileName();
    VirtualFile configFile;
    try {
        configFile = WriteAction.computeAndWait(() -> {
          VirtualFile file = dir.findChild(configFileName);
          return file == null ? dir.createChildData(null, configFileName) : file;
        });
        myAllConfigs.add(configFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    setFileContent(configFile, config, true);
    return configFile;
  }

  protected abstract String getExternalSystemConfigFileName();

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

  public VirtualFile createProjectSubFile(String relativePath) throws IOException {
    File f = new File(getProjectPath(), relativePath);
    FileUtil.ensureExists(f.getParentFile());
    FileUtil.ensureCanCreateFile(f);
    final boolean created = f.createNewFile();
    if (!created && !f.exists()) {
      throw new AssertionError("Unable to create the project sub file: " + f.getAbsolutePath());
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  @NotNull
  protected VirtualFile createProjectJarSubFile(String relativePath, Pair<ByteArraySequence, String>... contentEntries) throws IOException {
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
    for (Pair<ByteArraySequence, String> contentEntry : contentEntries) {
      addJarEntry(contentEntry.first.toBytes(), contentEntry.second, target);
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

  public VirtualFile createProjectSubFile(String relativePath, String content) throws IOException {
    VirtualFile file = createProjectSubFile(relativePath);
    setFileContent(file, content, false);
    return file;
  }

  protected Module getModule(final String name) {
    return getModule(myProject, name);
  }

  protected Module getModule(Project project, String name) {
    Module m = ReadAction.compute(() -> ModuleManager.getInstance(project).findModuleByName(name));
    assertNotNull("Module " + name + " not found", m);
    return m;
  }

  public static void setFileContent(final VirtualFile file, final String content, final boolean advanceStamps) {
    try {
      WriteAction.runAndWait(() -> {
        if (advanceStamps) {
          file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), -1, file.getTimeStamp() + 4000);
        }
        else {
          file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8), file.getModificationStamp(), file.getTimeStamp());
        }
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<? extends U> actual, Collection<? extends T> expected) {
    assertOrderedElementsAreEqual(actual, expected.toArray());
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<? extends T> actual, Collection<? extends T> expected) {
    assertEquals(new HashSet<>(expected), new HashSet<>(actual));
  }

  protected static void assertUnorderedPathsAreEqual(Collection<String> actual, Collection<String> expected) {
    assertEquals(new SetWithToString<>(CollectionFactory.createFilePathSet(expected)),
                 new SetWithToString<>(CollectionFactory.createFilePathSet(actual)));
  }

  protected static <T> void assertUnorderedElementsAreEqual(T[] actual, T... expected) {
    assertUnorderedElementsAreEqual(Arrays.asList(actual), expected);
  }

  protected static <T> void assertUnorderedElementsAreEqual(Collection<? extends T> actual, T... expected) {
    assertUnorderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected static <T, U> void assertOrderedElementsAreEqual(Collection<? extends U> actual, T... expected) {
    assertOrderedElementsAreEqual(equalsPredicate(), actual, expected);
  }

  protected static <T, U> void assertOrderedElementsAreEqual(BiPredicate<? super U, ? super T> predicate, Collection<? extends U> actual, T... expected) {
    String s = "\nexpected: " + Arrays.asList(expected) + "\nactual: " + new ArrayList<>(actual);
    assertEquals(s, expected.length, actual.size());

    java.util.List<U> actualList = new ArrayList<>(actual);
    for (int i = 0; i < expected.length; i++) {
      T expectedElement = expected[i];
      U actualElement = actualList.get(i);
      assertTrue(s, predicate.test(actualElement, expectedElement));
    }
  }

  protected static <T> void assertContain(java.util.List<? extends T> actual, T... expected) {
    java.util.List<T> expectedList = Arrays.asList(expected);
    assertTrue("expected: " + expectedList + "\n" + "actual: " + actual.toString(), actual.containsAll(expectedList));
  }

  protected boolean ignore() {
    printIgnoredMessage(null);
    return true;
  }

  protected static <T, U> BiPredicate<T, U> equalsPredicate() {
    //noinspection unchecked
    return (BiPredicate<T, U>)EQUALS_PREDICATE;
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

    SetWithToString(@NotNull Set<T> delegate) {
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
