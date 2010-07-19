package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class DirectoryIndexImplTest extends IdeaTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexTest");

  private DirectoryIndex myIndex;

  private Module myModule2;
  private Module myModule3;
  private VirtualFile myRootVFile;
  private VirtualFile myModule1Dir;
  private VirtualFile myModule2Dir;
  private VirtualFile myModule3Dir;
  private VirtualFile mySrcDir1;
  private VirtualFile mySrcDir2;
  private VirtualFile myTestSrc1;
  private VirtualFile myPack1Dir;
  private VirtualFile myPack2Dir;
  private VirtualFile myLibDir;
  private VirtualFile myLibSrcDir;
  private VirtualFile myLibClsDir;
  private VirtualFile myCvsDir;
  private VirtualFile myExcludeDir;
  private VirtualFile myOutputDir;
  private VirtualFile myModule1OutputDir;

  public DirectoryIndexImplTest() {
    myRunCommandForTest = true;
  }


  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          myRootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
          /*
            root
                module1
                    src1
                        pack1
                        testSrc
                            pack2
                    lib
                        src
                        cls
                    module2
                        src2
                            CVS
                            excluded
                module3
                out
                    module1
          */

          myModule1Dir = myRootVFile.createChildDirectory(null, "module1");
          mySrcDir1 = myModule1Dir.createChildDirectory(null, "src1");
          myPack1Dir = mySrcDir1.createChildDirectory(null, "pack1");
          myTestSrc1 = mySrcDir1.createChildDirectory(null, "testSrc");
          myPack2Dir = myTestSrc1.createChildDirectory(null, "pack2");
          myLibDir = myModule1Dir.createChildDirectory(null, "lib");
          myLibSrcDir = myLibDir.createChildDirectory(null, "src");
          myLibClsDir = myLibDir.createChildDirectory(null, "cls");

          myModule2Dir = myModule1Dir.createChildDirectory(null, "module2");
          mySrcDir2 = myModule2Dir.createChildDirectory(null, "src2");
          myCvsDir = mySrcDir2.createChildDirectory(null, "CVS");
          myExcludeDir = mySrcDir2.createChildDirectory(null, "excluded");

          myModule3Dir = myRootVFile.createChildDirectory(null, "module3");

          myOutputDir = myRootVFile.createChildDirectory(null, "out");
          myModule1OutputDir = myOutputDir.createChildDirectory(null, "module1");

          CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(myOutputDir.getUrl());

          // fill roots of module1
          {
            ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();

            rootModel.setSdk(null);

            ContentEntry contentEntry1 = rootModel.addContentEntry(myModule1Dir);
            contentEntry1.addSourceFolder(myTestSrc1, true);
            contentEntry1.addSourceFolder(mySrcDir1, false);

            rootModel.commit();
          }

          ModuleManager moduleManager = ModuleManager.getInstance(myProject);

          // fill roots of module2
          {
            VirtualFile moduleFile = myModule2Dir.createChildData(null, "module2.iml");
            myModule2 = moduleManager.newModule(moduleFile.getPath(), StdModuleTypes.JAVA);

            ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule2).getModifiableModel();
            ContentEntry contentEntry = rootModel.addContentEntry(myModule2Dir);
            contentEntry.addSourceFolder(mySrcDir2, false);
            contentEntry.addExcludeFolder(myExcludeDir);

            Library.ModifiableModel libraryModel = rootModel.getModuleLibraryTable().createLibrary().getModifiableModel();
            libraryModel.addRoot(myLibClsDir, OrderRootType.CLASSES);
            libraryModel.addRoot(myLibSrcDir, OrderRootType.SOURCES);
            libraryModel.commit();

            rootModel.commit();
          }

          // fill roots of module3
          {
            VirtualFile moduleFile = myModule3Dir.createChildData(null, "module3.iml");
            myModule3 = moduleManager.newModule(moduleFile.getPath(), StdModuleTypes.JAVA);

            ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule3).getModifiableModel();
            rootModel.addContentEntry(myModule3Dir);
            rootModel.addModuleOrderEntry(myModule2); // module3 depends on module2

            rootModel.commit();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });

    myIndex = DirectoryIndex.getInstance(myProject);
  }

  public void testDirInfos() {
    checkInfoNull(myRootVFile);

    checkInfo(myModule1Dir, myModule, false, false, false, false, null, new Module[]{});
    checkInfo(mySrcDir1, myModule, true, false, false, false, "", new Module[]{myModule});
    checkInfo(myPack1Dir, myModule, true, false, false, false, "pack1", new Module[]{myModule});
    checkInfo(myTestSrc1, myModule, true, true, false, false, "", new Module[]{myModule});
    checkInfo(myPack2Dir, myModule, true, true, false, false, "pack2", new Module[]{myModule});
    checkInfo(myLibDir, myModule, false, false, false, false, null, new Module[]{});

    checkInfo(myLibSrcDir, myModule, false, false, false, true, "", new Module[]{myModule2});
    checkInfo(myLibClsDir, myModule, false, false, true, false, "", new Module[]{myModule2});

    checkInfo(myModule2Dir, myModule2, false, false, false, false, null, new Module[]{});
    checkInfo(mySrcDir2, myModule2, true, false, false, false, "", new Module[]{myModule2, myModule3});
    checkInfoNull(myCvsDir);
    checkInfoNull(myExcludeDir);

    checkInfo(myModule3Dir, myModule3, false, false, false, false, null, new Module[]{});
  }

  public void testDirsByPackageName() {
    checkPackage(new VirtualFile[]{mySrcDir1, myTestSrc1, myLibSrcDir, myLibClsDir, mySrcDir2}, "");
    checkPackage(new VirtualFile[]{myPack1Dir}, "pack1");
    checkPackage(new VirtualFile[]{myPack2Dir}, "pack2");
  }

  public void testCreateDir() throws Exception {
    String path = mySrcDir1.getPath().replace('/', File.separatorChar);
    assertTrue(new File(path + File.separatorChar + "dir1" + File.separatorChar + "dir2").mkdirs());
    assertTrue(new File(path + File.separatorChar + "CVS").mkdirs());
    VirtualFileManager.getInstance().refresh(false);

    myIndex.checkConsistency();
  }

  public void testDeleteDir() throws Exception {
    VirtualFile subdir1 = mySrcDir1.createChildDirectory(null, "subdir1");
    VirtualFile subdir2 = subdir1.createChildDirectory(null, "subdir2");
    subdir2.createChildDirectory(null, "subdir3");

    myIndex.checkConsistency();

    subdir1.delete(null);

    myIndex.checkConsistency();
  }

  public void testMoveDir() throws Exception {
    VirtualFile subdir = mySrcDir2.createChildDirectory(null, "subdir1");
    subdir.createChildDirectory(null, "subdir2");

    myIndex.checkConsistency();

    subdir.move(null, mySrcDir1);

    myIndex.checkConsistency();
  }

  public void testRenameDir() throws Exception {
    VirtualFile subdir = mySrcDir2.createChildDirectory(null, "subdir1");
    subdir.createChildDirectory(null, "subdir2");

    myIndex.checkConsistency();

    subdir.rename(null, "abcd");

    myIndex.checkConsistency();
  }

  public void testRenameRoot() throws Exception {
    myModule1Dir.rename(null, "newName");

    myIndex.checkConsistency();
  }

  public void testMoveRoot() throws Exception {
    myModule1Dir.move(null, myModule3Dir);

    myIndex.checkConsistency();
  }

  public void testAddProjectDir() throws Exception {
    VirtualFile newDir = myModule1Dir.getParent().createChildDirectory(null, "newDir");
    newDir.createChildDirectory(null, "subdir");

    myIndex.checkConsistency();

    ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    rootModel.addContentEntry(newDir);
    rootModel.commit();

    myIndex.checkConsistency();
  }

  public void testChangeIgnoreList() throws Exception {
    myModule1Dir.createChildDirectory(null, "newDir");

    myIndex.checkConsistency();

    final FileTypeManagerEx fileTypeManager = (FileTypeManagerEx)FileTypeManager.getInstance();
    final String list = fileTypeManager.getIgnoredFilesList();
    final String list1 = list + ";" + "newDir";
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fileTypeManager.setIgnoredFilesList(list1);
        }
      });

      myIndex.checkConsistency();
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fileTypeManager.setIgnoredFilesList(list);
        }
      });
    }
  }

  public void testAddModule() throws Exception {
    myIndex.checkConsistency();

    VirtualFile newModuleContent = myRootVFile.createChildDirectory(null, "newModule");
    newModuleContent.createChildDirectory(null, "subDir");
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", StdModuleTypes.JAVA);
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    rootModel.addContentEntry(newModuleContent);
    rootModel.commit();

    myIndex.checkConsistency();
  }

  public void testExplicitExcludeOfInner() throws Exception {
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();

    ContentEntry[] contentEntries = rootModel.getContentEntries();
    assertEquals(1, contentEntries.length);
    ContentEntry contentEntry = contentEntries[0];
    contentEntry.addExcludeFolder(myModule2Dir);

    rootModel.commit();

    myIndex.checkConsistency();

    checkInfo(myModule2Dir, myModule2, false, false, false, false, null, new Module[]{});
    checkInfo(mySrcDir2, myModule2, true, false, false, false, "", new Module[]{myModule2, myModule3});
  }

  public void testResettingProjectOutputPath() throws Exception {
    VirtualFile output1 = myModule1Dir.createChildDirectory(null, "output1");
    VirtualFile output2 = myModule1Dir.createChildDirectory(null, "output2");

    assertNotNull(myIndex.getInfoForDirectory(output1));
    assertNotNull(myIndex.getInfoForDirectory(output2));

    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(output1.getUrl());
    fireRootsChanged();

    assertNull(myIndex.getInfoForDirectory(output1));
    assertNotNull(myIndex.getInfoForDirectory(output2));

    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(output2.getUrl());
    fireRootsChanged();

    assertNotNull(myIndex.getInfoForDirectory(output1));
    assertNull(myIndex.getInfoForDirectory(output2));
  }

  private void fireRootsChanged() {
    ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(EmptyRunnable.getInstance(), false, true);
  }

  public void testExcludedDirShouldBeExcludedRightAfterItsCreation() throws Exception {
    VirtualFile excluded = myModule1Dir.createChildDirectory(null, "excluded");
    VirtualFile projectOutput = myModule1Dir.createChildDirectory(null, "projectOutput");
    VirtualFile module2Output = myModule1Dir.createChildDirectory(null, "module2Output");
    VirtualFile module2TestOutput = myModule2Dir.createChildDirectory(null, "module2TestOutput");

    assertNotNull(myIndex.getInfoForDirectory(excluded));
    assertNotNull(myIndex.getInfoForDirectory(projectOutput));
    assertNotNull(myIndex.getInfoForDirectory(module2Output));
    assertNotNull(myIndex.getInfoForDirectory(module2TestOutput));

    CompilerProjectExtension.getInstance(myProject).setCompilerOutputUrl(projectOutput.getUrl());

    ModifiableRootModel m = ModuleRootManager.getInstance(myModule).getModifiableModel();
    ContentEntry[] ee = m.getContentEntries();
    assertEquals(1, ee.length);
    ee[0].addExcludeFolder(excluded);
    m.commit();
    
    m = ModuleRootManager.getInstance(myModule2).getModifiableModel();
    final CompilerModuleExtension compilerModuleExtension = m.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setCompilerOutputPath(module2Output);
    compilerModuleExtension.setCompilerOutputPathForTests(module2TestOutput);
    compilerModuleExtension.setExcludeOutput(true);
    compilerModuleExtension.inheritCompilerOutputPath(false);
    m.commit();

    assertNull(myIndex.getInfoForDirectory(excluded));
    assertNull(myIndex.getInfoForDirectory(projectOutput));
    assertNull(myIndex.getInfoForDirectory(module2Output));
    assertNull(myIndex.getInfoForDirectory(module2TestOutput));

    excluded.delete(null);
    projectOutput.delete(null);
    module2Output.delete(null);
    module2TestOutput.delete(null);

    final List<Boolean> isExcluded = new ArrayList<Boolean>();
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        isExcluded.add(myIndex.getInfoForDirectory(e.getFile()) == null);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      excluded = myModule1Dir.createChildDirectory(null, excluded.getName());
      projectOutput = myModule1Dir.createChildDirectory(null, projectOutput.getName());
      module2Output = myModule1Dir.createChildDirectory(null, module2Output.getName());
      module2TestOutput = myModule2Dir.createChildDirectory(null, module2TestOutput.getName());
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }

    assertNull(myIndex.getInfoForDirectory(excluded));
    assertNull(myIndex.getInfoForDirectory(projectOutput));
    assertNull(myIndex.getInfoForDirectory(module2Output));
    assertNull(myIndex.getInfoForDirectory(module2TestOutput));

    assertEquals(4, isExcluded.size());
    assertTrue(isExcluded.get(0));
    assertTrue(isExcluded.get(1));
    assertTrue(isExcluded.get(2));
    assertTrue(isExcluded.get(3));
  }

  public void testExcludesShouldBeRecognizedRightOnRefresh() throws Exception {
    VirtualFile dir = myModule1Dir.createChildDirectory(null, "dir");
    VirtualFile excluded = dir.createChildDirectory(null, "excluded");

    ModifiableRootModel m = ModuleRootManager.getInstance(myModule).getModifiableModel();
    ContentEntry[] ee = m.getContentEntries();
    assertEquals(1, ee.length);
    ee[0].addExcludeFolder(excluded);
    m.commit();

    dir.delete(null);

    new File(myModule1Dir.getPath(), "dir/excluded/foo").mkdirs();

    final List<Boolean> isExcluded = new ArrayList<Boolean>();
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        assertEquals("dir", e.getFileName());

        isExcluded.add(myIndex.getInfoForDirectory(e.getFile()) == null);
        isExcluded.add(myIndex.getInfoForDirectory(e.getFile().findFileByRelativePath("excluded")) == null);
        isExcluded.add(myIndex.getInfoForDirectory(e.getFile().findFileByRelativePath("excluded/foo")) == null);
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      VirtualFileManager.getInstance().refresh(false);
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }

    assertEquals(Arrays.asList(false, true, true), isExcluded);
  }

  public void testProcessingNestedContentRootsOfExcludedDirsOnCreation() {
    String rootPath = myModule1Dir.getPath();
    File f = new File(rootPath, "excludedDir/dir/anotherContentRoot");

    ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    rootModel.getContentEntries()[0].addExcludeFolder(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(f.getParentFile().getParent())));
    rootModel.commit();

    rootModel = ModuleRootManager.getInstance(myModule2).getModifiableModel();
    rootModel.addContentEntry(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(f.getPath())));
    rootModel.commit();

    f.mkdirs();
    LocalFileSystem.getInstance().refresh(false);

    assertNull(myIndex.getInfoForDirectory(LocalFileSystem.getInstance().findFileByIoFile(f.getParentFile().getParentFile())));
    assertNotNull(myIndex.getInfoForDirectory(LocalFileSystem.getInstance().findFileByIoFile(f)));
  }

  public void testLibraryDirInContent() throws Exception {
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    Library.ModifiableModel libraryModel = rootModel.getModuleLibraryTable().createLibrary().getModifiableModel();
    libraryModel.addRoot(myModule1Dir, OrderRootType.CLASSES);
    libraryModel.commit();
    rootModel.commit();

    myIndex.checkConsistency();

    checkInfo(myModule1Dir, myModule, false, false, true, false, "", new Module[]{myModule});
    checkInfo(mySrcDir1, myModule, true, false, true, false, "", new Module[]{myModule});
  }


  public void testExcludeCompilerOutputOutsideOfContentRoot() throws Exception {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    assertTrue(fileIndex.isIgnored(myOutputDir));
    assertTrue(fileIndex.isIgnored(myModule1OutputDir));
    assertFalse(fileIndex.isIgnored(myOutputDir.getParent()));
  }

  private void checkInfo(VirtualFile dir,
                         Module module,
                         boolean isInModuleSource,
                         boolean isTestSource,
                         boolean isInLibrary,
                         boolean isInLibrarySource,
                         String packageName,
                         Module[] modulesOfOrderEntries) {
    DirectoryInfo info = myIndex.getInfoForDirectory(dir);
    assertNotNull(info);
    assertEquals(module, info.module);
    assertEquals(isInModuleSource, info.isInModuleSource);
    assertEquals(isTestSource, info.isTestSource);
    assertEquals(isInLibrary, info.libraryClassRoot != null);
    assertEquals(isInLibrarySource, info.isInLibrarySource);

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    assertEquals(packageName, fileIndex.getPackageNameByDirectory(dir));

    assertEquals(modulesOfOrderEntries.length, info.getOrderEntries().size());
    for (Module aModule : modulesOfOrderEntries) {
      boolean found = false;
      for (OrderEntry orderEntry : info.getOrderEntries()) {
        if (orderEntry.getOwnerModule() == aModule) {
          found = true;
          break;
        }
      }
      assertTrue(found);
    }
  }

  private void checkInfoNull(VirtualFile dir) {
    DirectoryInfo info = myIndex.getInfoForDirectory(dir);
    assertNull(info);
  }

  private void checkPackage(VirtualFile[] expectedDirs, String packageName) {
    VirtualFile[] actualDirs = myIndex.getDirectoriesByPackageName(packageName, true).toArray(VirtualFile.EMPTY_ARRAY);
    assertNotNull(actualDirs);
    HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
    ContainerUtil.addAll(set1, expectedDirs);
    HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
    ContainerUtil.addAll(set2, actualDirs);
    assertEquals(set1, set2);
  }
}
