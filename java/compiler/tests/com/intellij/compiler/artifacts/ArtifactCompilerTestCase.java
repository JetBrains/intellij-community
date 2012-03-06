package com.intellij.compiler.artifacts;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.ParameterizedRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.io.TestFileSystemItem;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public abstract class ArtifactCompilerTestCase extends PackagingElementsTestCase {
  @Override
  protected void tearDown() throws Exception {
    for (Artifact artifact : ArtifactManager.getInstance(myProject).getArtifacts()) {
      final String outputPath = artifact.getOutputPath();
      if (!StringUtil.isEmpty(outputPath)) {
        FileUtil.delete(new File(FileUtil.toSystemDependentName(outputPath)));
      }
    }
    super.tearDown();
  }

  protected CompilationLog compileProject() {
    return compile(ArtifactManager.getInstance(myProject).getArtifacts());
  }

  protected CompilationLog compile(final Artifact... artifacts) {
    final CompileScope scope = ArtifactCompileScope.createArtifactsScope(myProject, Arrays.asList(artifacts));
    return compile(scope, CompilerFilter.ALL);
  }

  protected CompilationLog compile(Module module) {
    return compile(CompilerManager.getInstance(myProject).createModuleCompileScope(module, false), CompilerFilter.ALL);
  }

  protected CompilationLog compile(boolean force, VirtualFile... files) {
    return compile(CompilerManager.getInstance(myProject).createFilesCompileScope(files), CompilerFilter.ALL, force);
  }

  protected CompilationLog compile(final CompileScope scope, final CompilerFilter filter) {
    return compile(scope, filter, false);
  }

  protected CompilationLog compile(final CompileScope scope, final CompilerFilter filter, final boolean forceCompile) {
    return compile(scope, filter, forceCompile, false);
  }

  protected CompilationLog compile(final CompileScope scope, final CompilerFilter filter, final boolean forceCompile,
                                   final boolean errorsExpected) {
    return compile(errorsExpected, new ParameterizedRunnable<CompileStatusNotification>() {
      @Override
      public void run(CompileStatusNotification callback) {
        final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
        if (forceCompile) {
          assertSame("Only 'ALL' filter is supported for forced compilation", CompilerFilter.ALL, filter);
          compilerManager.compile(scope, callback);
        }
        else {
          compilerManager.make(scope, filter, callback);
        }
      }
    });
  }

  protected void rebuild() {
    compile(false, new ParameterizedRunnable<CompileStatusNotification>() {
      @Override
      public void run(CompileStatusNotification compileStatusNotification) {
        CompilerManager.getInstance(myProject).rebuild(compileStatusNotification);
      }
    });
  }

  protected CompilationLog compile(final boolean errorsExpected, final ParameterizedRunnable<CompileStatusNotification> action) {
    final Ref<CompilationLog> result = Ref.create(null);
    final Semaphore semaphore = new Semaphore();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        semaphore.down();

        CompilerManagerImpl.testSetup();
        final CompileStatusNotification callback = new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            try {
              if (aborted) {
                fail("compilation aborted");
              }
              if (errorsExpected && errors == 0) {
                fail("compilation finished without errors");
              }
              else if (!errorsExpected && errors > 0) {
                fail("compilation finished with errors: " + Arrays.toString(compileContext.getMessages(CompilerMessageCategory.ERROR)));
              }
              result.set(new CompilationLog(CompilerManagerImpl.getPathsToRecompile(), CompilerManagerImpl.getPathsToDelete()));
            }
            finally {
              semaphore.up();
            }
          }
        };

        action.run(callback);
      }
    });

    long start = System.currentTimeMillis();
    while (!semaphore.waitFor(10)) {
      //if (System.currentTimeMillis() - start > 60 * 1000) {
      //  throw new Exception("timeout");
      //}
      UIUtil.dispatchAllInvocationEvents();
    }
    UIUtil.dispatchAllInvocationEvents();

    return result.get();
  }

  private Set<String> getRelativePaths(String[] paths) {
    final Set<String> set = new THashSet<String>();
    final String basePath = myProject.getBaseDir().getPath();
    for (String path : paths) {
      set.add(StringUtil.trimStart(StringUtil.trimStart(FileUtil.toSystemIndependentName(path), basePath), "/"));
    }
    return set;
  }

  protected void changeFileInJar(String jarPath, String pathInJar) throws Exception {
    final VirtualFile jarFile = LocalFileSystem.getInstance().findFileByPath(jarPath);
    assertNotNull(jarFile);
    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    assertNotNull(jarRoot);
    VirtualFile jarEntry = jarRoot.findFileByRelativePath(pathInJar);
    assertNotNull(jarEntry);
    assertNotNull(jarFile);
    changeFile(jarFile);
    jarFile.refresh(false, false);

    jarEntry = jarRoot.findFileByRelativePath(pathInJar);
    assertNotNull(jarEntry);
  }

  protected void changeFile(VirtualFile file) throws Exception {
    changeFile(file, null);
  }

  protected void changeFile(VirtualFile file, final String newText) throws Exception {
    if (newText != null) {
      VfsUtil.saveText(file, newText);
    }
    ((NewVirtualFile)file).setTimeStamp(file.getTimeStamp() + 10);
  }

  protected void deleteFile(final VirtualFile file) throws IOException {
    new WriteAction() {
      @Override
      protected void run(final Result result) {
        try {
          file.delete(this);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      }
    }.execute();
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    final String baseUrl = myProject.getBaseDir().getUrl();
    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(baseUrl + "/out");
  }

  protected static TestFileSystemBuilder fs() {
    return TestFileSystemItem.fs();
  }

  public static void assertNoOutput(Artifact artifact) {
    final String outputPath = artifact.getOutputPath();
    assertNotNull("output path not specified for " + artifact.getName(), outputPath);
    assertFalse(new File(FileUtil.toSystemDependentName(outputPath)).exists());
  }

  public static void assertEmptyOutput(Artifact a1) throws IOException {
    assertOutput(a1, ArtifactCompilerTestCase.fs());
  }

  public static void assertOutput(Artifact artifact, TestFileSystemBuilder item) {
    final String output = artifact.getOutputPath();
    assertNotNull("output path not specified for " + artifact.getName(), output);
    final VirtualFile outputFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(output);
    assertNotNull("output file not found " + output, outputFile);
    outputFile.refresh(false, true);
    item.build().assertDirectoryEqual(VfsUtil.virtualToIoFile(outputFile));
  }


  protected class CompilationLog {
    private final Set<String> myRecompiledPaths;
    private final Set<String> myDeletedPaths;

    public CompilationLog(String[] recompiledPaths, String[] deletedPaths) {
      myRecompiledPaths = getRelativePaths(recompiledPaths);
      myDeletedPaths = getRelativePaths(deletedPaths);
    }

    public void assertUpToDate() {
      checkRecompiled();
      checkDeleted();
    }

    public void assertRecompiled(String... expected) {
      checkRecompiled(expected);
      checkDeleted();
    }

    public void assertDeleted(String... expected) {
      checkRecompiled();
      checkDeleted(expected);
    }

    public void assertRecompiledAndDeleted(String[] recompiled, String... deleted) {
      checkRecompiled(recompiled);
      checkDeleted(deleted);
    }

    private void checkRecompiled(String... expected) {
      assertSet("recompiled", myRecompiledPaths, expected);
    }

    private void checkDeleted(String... expected) {
      assertSet("deleted", myDeletedPaths, expected);
    }

    private void assertSet(String name, Set<String> actual, String[] expected) {
      for (String path : expected) {
        if (!actual.remove(path)) {
          fail("'" + path + "' is not " + name + ". " + name + ": " + new HashSet<String>(actual));
        }
      }
      if (!actual.isEmpty()) {
        fail("'" + actual.iterator().next() + "' must not be " + name);
      }
    }
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}
