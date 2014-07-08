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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
public abstract class ExternalSystemTestCase extends UsefulTestCase {
  private static File ourTempDir;

  protected IdeaProjectTestFixture myTestFixture;

  protected Project myProject;

  protected File myTestDir;
  protected VirtualFile myProjectRoot;
  protected VirtualFile myProjectConfig;
  protected List<VirtualFile> myAllConfigs = new ArrayList<VirtualFile>();

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
      myProject = null;
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          try {
            tearDownFixtures();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      });
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
    f.createNewFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
  }

  protected VirtualFile createProjectSubFile(String relativePath, String content) throws IOException {
    VirtualFile file = createProjectSubFile(relativePath);
    setFileContent(file, content, false);
    return file;
  }

  private static void setFileContent(final VirtualFile file, final String content, final boolean advanceStamps) throws IOException {
    new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        if (advanceStamps) {
          file.setBinaryContent(content.getBytes("UTF-8"), -1, file.getTimeStamp() + 4000);
        }
        else {
          file.setBinaryContent(content.getBytes("UTF-8"), file.getModificationStamp(), file.getTimeStamp());
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
