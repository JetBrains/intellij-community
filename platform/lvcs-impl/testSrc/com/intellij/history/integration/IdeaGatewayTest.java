// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.ide.scratch.ScratchFileActions;
import com.intellij.ide.scratch.ScratchFileCreationHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.OpenProjectTaskBuilder;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateExcludedFolders;

public class IdeaGatewayTest extends IntegrationTestCase {
  public void testFindingFile() {
    assertEquals(myRoot, myGateway.findVirtualFile(myRoot.getPath()));
    assertNull(myGateway.findVirtualFile(myRoot.getPath() + "/nonexistent"));
  }

  public void testGettingDirectory() throws Exception {
    assertEquals(myRoot, findOrCreateFileSafely(myRoot.getPath()));
  }

  @NotNull
  private VirtualFile findOrCreateFileSafely(String path) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(
      (ThrowableComputable<VirtualFile, IOException>)() -> myGateway.findOrCreateFileSafely(path, true))
      ;
  }

  public void testCreatingDirectory() throws Exception {
    String subSubDirPath = myRoot.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    VirtualFile subDir = findOrCreateFileSafely(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testCreatingDirectoryWhenSuchFileExists() throws Exception {
    String subSubDirPath = myRoot.getPath() + "/subDir/subSubDir";

    assertFalse(new File(subSubDirPath).exists());
    createChildData(myRoot, "subDir");

    VirtualFile subDir = findOrCreateFileSafely(subSubDirPath);

    assertNotNull(subDir);
    assertEquals(subSubDirPath, subDir.getPath());

    assertTrue(new File(subSubDirPath).exists());
  }

  public void testSingleFileRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile file = createFile(lowLevelDir + "/path.txt");

    createFile(lowLevelDir + "/path2.txt");
    createFile(lowLevelDir + "/path3.txt");
    createFile(topLevelDir + "/dir3/path.txt");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(file), false);
    assertEquals(myGateway.getPathOrUrl(file), getAllPaths(rootEntry));
  }

  public void testSingleDirectoryRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile directory = createDirectory(lowLevelDir);

    createDirectory(lowLevelDir + "smth");
    createFile(lowLevelDir + "/file.txt");
    createDirectory(topLevelDir + "/dir239");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(directory), false);
    assertEquals(myGateway.getPathOrUrl(directory), getAllPaths(rootEntry));
  }

  public void testSingleDirectoryWithChildrenRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir = topLevelDir + "/dir2";

    VirtualFile directory = createDirectory(lowLevelDir);

    VirtualFile file1 = createFile(lowLevelDir + "/file1.txt");
    VirtualFile file2 = createFile(lowLevelDir + "/file2.txt");

    createDirectory(lowLevelDir + "smth");
    createDirectory(topLevelDir + "/dir239");

    RootEntry rootEntry = myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(directory), true);
    assertEquals(myGateway.getPathOrUrl(file1) + "\n" + myGateway.getPathOrUrl(file2), getAllPaths(rootEntry));
  }

  public void testMultipleFilesRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir2 = topLevelDir + "/dir2";
    String lowLevelDir3 = topLevelDir + "/dir3";

    VirtualFile file1 = createFile(lowLevelDir2 + "/file1.txt");
    VirtualFile file2 = createFile(lowLevelDir2 + "/file2.txt");
    VirtualFile file3 = createFile(lowLevelDir3 + "/file3.txt");

    createDirectory(lowLevelDir2 + "smth");
    createDirectory(topLevelDir + "/dir239");
    createFile(lowLevelDir2 + "/other.file.txt");
    createFile(lowLevelDir3 + "/and.other.file.txt");

    Collection<String> paths = ContainerUtil.map(Arrays.asList(file1, file2, file3), file -> myGateway.getPathOrUrl(file));
    RootEntry rootEntry = myGateway.createTransientRootEntryForPaths(paths, true);
    assertEquals(StringUtil.join(paths, "\n"), getAllPaths(rootEntry));
  }

  public void testMultipleDirectoriesRootEntry() throws IOException {
    String topLevelDir = "dir1";
    String lowLevelDir2 = topLevelDir + "/dir2";
    String lowLevelDir3 = topLevelDir + "/dir3";

    VirtualFile directory2 = createDirectory(lowLevelDir2);
    VirtualFile directory3 = createDirectory(lowLevelDir3);

    VirtualFile file1 = createFile(lowLevelDir2 + "/file1.txt");
    VirtualFile file2 = createFile(lowLevelDir2 + "/file2.txt");
    VirtualFile file3 = createFile(lowLevelDir3 + "/file3.txt");
    VirtualFile directory4 = createDirectory(lowLevelDir3 + "/someDir");

    createDirectory(topLevelDir + "smth");
    createDirectory(topLevelDir + "/dir239");
    createFile(topLevelDir + "/other.file.txt");
    createFile(topLevelDir + "/and.other.file.txt");

    Collection<String> paths = ContainerUtil.map(Arrays.asList(directory2, directory3), directory -> myGateway.getPathOrUrl(directory));
    RootEntry rootEntry = myGateway.createTransientRootEntryForPaths(paths, true);
    assertEquals(StringUtil.join(Arrays.asList(file1, file2, file3, directory4), file -> myGateway.getPathOrUrl(file), "\n"),
                 getAllPaths(rootEntry));
  }

  public void testFileVersionedWhenInContentRoot() throws Exception {
    // GIVEN
    VirtualFile sourceFile = createFile("src/main/java/Main.java");
    VirtualFile sourceDirectory = sourceFile.getParent();

    // WHEN - THEN
    assertTrue(myGateway.isVersioned(sourceDirectory));
    assertTrue(myGateway.isVersioned(sourceFile));
    assertTrue(myGateway.isVersioned(sourceDirectory, true));
    assertTrue(myGateway.isVersioned(sourceFile, true));
  }

  public void testScratchFileVersionedOnlyWhenCallerDontCareAboutContentRoot() {
    // GIVEN there is no module, so the project doesn't have content roots
    ModifiableModuleModel model = ModuleManager.getInstance(getProject()).getModifiableModel();
    model.disposeModule(getModule());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> model.commit());

    // AND scratch file
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    VirtualFile scratchFile = ScratchFileActions.doCreateNewScratch(getProject(), context).getVirtualFile();

    // WHEN - THEN
    assertTrue(myGateway.isVersioned(scratchFile));
    assertFalse(myGateway.isVersioned(scratchFile, true));
  }

  public void testFileNotVersionedWhenHasSpecificName() throws Exception {
    // GIVEN
    List<VirtualFile> ignoredFiles = List.of(
      createFile("workspace.xml"),
      createFile("project.iws"),
      createFile("some.class")
    );

    // WHEN - THEN
    ignoredFiles.forEach(file -> {
      assertFalse(myGateway.isVersioned(file));
      assertFalse(myGateway.isVersioned(file, true));
    });
  }

  public void testFileNotVersionedWhenInExcludedDirectory() throws Exception {
    // GIVEN
    VirtualFile fileInExcludedDirectory = createFile("out/example-file.txt");
    VirtualFile excludedDirectory = fileInExcludedDirectory.getParent();

    updateExcludedFolders(myModule, myRoot, List.of(), List.of(excludedDirectory.getUrl()));

    // WHEN - THEN
    assertFalse(myGateway.isVersioned(excludedDirectory));
    assertFalse(myGateway.isVersioned(fileInExcludedDirectory));
    assertFalse(myGateway.isVersioned(excludedDirectory, true));
    assertFalse(myGateway.isVersioned(fileInExcludedDirectory, true));
  }

  public void testFileNotVersionedWhenGloballyIgnored() throws Exception {
    // GIVEN
    VirtualFile ignoredFile = createFile("src/main/resources/ignored.file");
    VirtualFile resourcesDirectory = ignoredFile.getParent();

    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String originalIgnoredFileList = fileTypeManager.getIgnoredFilesList();
    try {
      WriteAction.runAndWait(() -> fileTypeManager.setIgnoredFilesList(originalIgnoredFileList + ";ignored.file"));

      // WHEN - THEN
      assertTrue(myGateway.isVersioned(resourcesDirectory));
      assertFalse(myGateway.isVersioned(ignoredFile));
      assertTrue(myGateway.isVersioned(resourcesDirectory, true));
      assertFalse(myGateway.isVersioned(ignoredFile, true));
    } finally {
      WriteAction.runAndWait(() -> fileTypeManager.setIgnoredFilesList(originalIgnoredFileList));
    }
  }

  public void testFileNotVersionedWhenIgnoredByOneOfTheProjects() throws Exception {
    // GIVEN a second opened project
    VirtualFile anotherProjectRootDir = getTempDir().createVirtualDir();
    Project anotherProject = ProjectManagerEx.getInstanceEx().openProject(anotherProjectRootDir.toNioPath(), new OpenProjectTaskBuilder().build());
    Disposer.register(getTestRootDisposable(), () -> PlatformTestUtil.forceCloseProjectWithoutSaving(anotherProject));

    // AND a module in the second project
    Module module = doCreateRealModuleIn(anotherProject.getName(), anotherProject, getModuleType());
    ModuleRootModificationUtil.addContentRoot(module, anotherProjectRootDir.getPath());

    // AND an ignored directory
    VirtualFile ignoredDirectory = WriteAction.compute(() -> anotherProjectRootDir.createChildDirectory(this, "out"));
    updateExcludedFolders(module, anotherProjectRootDir, List.of(), List.of(ignoredDirectory.getUrl()));

    // AND a file inside the ignored directory
    VirtualFile fileInIgnoredDirectory = WriteAction.compute(() -> ignoredDirectory.createChildData(this, "fileInAnotherProject.txt"));

    // WHEN - THEN
    assertSize(2, ProjectManagerEx.getInstanceEx().getOpenProjects());
    assertFalse(myGateway.isVersioned(fileInIgnoredDirectory));
    assertFalse(myGateway.isVersioned(fileInIgnoredDirectory, true));
  }

  public void testFileVersionedInOptimizedPath() throws Exception {
    // GIVEN there are no opened projects
    PlatformTestUtil.forceCloseProjectWithoutSaving(myProject);

    // AND a file outside the content root (because the project isn't opened)
    VirtualFile fileOutsideContentRoot = createFile("file/outside/content/root/Main.java");

    // WHEN - THEN
    assertEmpty(ProjectManagerEx.getInstanceEx().getOpenProjects());
    assertTrue(myGateway.isVersioned(fileOutsideContentRoot));
    assertFalse(myGateway.isVersioned(fileOutsideContentRoot, true));
  }

  public static @NotNull String getAllPaths(@NotNull RootEntry rootEntry) {
    List<String> result = new ArrayList<>();
    printAllPaths(rootEntry, result);
    result.sort(Comparator.naturalOrder());
    return StringUtil.join(result, "\n");
  }

  private static void printAllPaths(@NotNull Entry parentEntry, @NotNull List<String> result) {
    for (Entry entry : parentEntry.getChildren()) {
      if (entry.getChildren().isEmpty()) {
        result.add(entry.getPath());
        continue;
      }
      printAllPaths(entry, result);
    }
  }
}
