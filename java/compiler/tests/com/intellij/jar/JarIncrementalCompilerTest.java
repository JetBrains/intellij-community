package com.intellij.jar;

import com.intellij.compiler.CompilerTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class JarIncrementalCompilerTest extends CompilerTestCase {
  private final JarCompiler myCompiler;
  private VirtualFile myTempProjectRootDir;

  public JarIncrementalCompilerTest() {
    super("jar");
    myCompiler = new JarCompiler(){
      public void processOutdatedItem(final CompileContext context, String url, final ValidityState state) {
        ApplicationManager.getApplication().runReadAction(new Runnable(){
          public void run() {
            File jar = new File(VfsUtil.urlToPath(((MyValState)state).getOutputJarUrl()));
            String relativePath = relativeToModuleRoot(jar);
            myDeletedPaths.add(relativePath);
          }
        });
        super.processOutdatedItem(context, url, state);
      }

      @NotNull
      public FileProcessingCompiler.ProcessingItem[] getProcessingItems(final CompileContext context) {
        myRecompiledPaths.clear();
        return super.getProcessingItems(context);
      }

      public FileProcessingCompiler.ProcessingItem[] process(final CompileContext context,
                                                             final FileProcessingCompiler.ProcessingItem[] items) {
        for (ProcessingItem item : items) {
          MyProcItem procItem = (MyProcItem)item;
          File jar = new File(VfsUtil.urlToPath(procItem.getValidityState().getOutputJarUrl()));
          myRecompiledPaths.add(relativeToModuleRoot(jar));
        }
        return super.process(context, items);
      }
    };
  }

  protected void setUp() throws Exception {
    super.setUp();
    CompilerManager.getInstance(myProject).addCompiler(myCompiler);
  }

  protected void tearDown() throws Exception {
    ((ModuleManagerImpl)ModuleManager.getInstance(myProject)).projectClosed();
    CompilerManager.getInstance(myProject).removeCompiler(myCompiler);
    super.tearDown();
  }

  private String relativeToModuleRoot(final File jar) {
    String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(new File(myTempProjectRootDir.getPath()), jar));
    return relativePath;
  }

  protected String getSourceDirRelativePath() {
    return "source";
  }

  protected void setUpProject() throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        try {
          VirtualFile root = getDataRootDir(getTestName(true));
          myTempProjectRootDir = PsiTestUtil.createTestProjectStructure(null, root.getPath(), myFilesToDelete, false);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
    try {
      VirtualFile[] children = myTempProjectRootDir.getChildren();
      for (VirtualFile virtualFile : children) {
        if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile) == StdFileTypes.IDEA_PROJECT) {
          myProject = ProjectManagerEx.getInstanceEx().loadProject(virtualFile.getPath());
          break;
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }

    simulateProjectOpen();
    myModule = ModuleManager.getInstance(myProject).getModules()[0];
  }

  protected void copyTestProjectFiles(VirtualFileFilter filter) throws Exception {
    copyFiles(myDataDir, myModuleRoot, filter);
  }

  public void testNoChange() throws Exception {doTest();}
  public void testJarSimple() throws Exception {doTest();}
}
