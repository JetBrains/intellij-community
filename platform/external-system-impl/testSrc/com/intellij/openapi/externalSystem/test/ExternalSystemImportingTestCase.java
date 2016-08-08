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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Vladislav.Soroka
 * @since 6/30/2014
 */
public abstract class ExternalSystemImportingTestCase extends ExternalSystemTestCase {

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    List<String> actual = new ArrayList<>();
    for (ContentEntry e : getContentRoots(moduleName)) {
      actual.add(e.getUrl());
    }

    for (int i = 0; i < expectedRoots.length; i++) {
      expectedRoots[i] = VfsUtilCore.pathToUrl(expectedRoots[i]);
    }

    assertUnorderedPathsAreEqual(actual, Arrays.asList(expectedRoots));
  }

  protected void assertSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.SOURCE, expectedSources);
  }

  protected void assertGeneratedSources(String moduleName, String... expectedSources) {
    assertGeneratedSources(moduleName, JavaSourceRootType.SOURCE, expectedSources);
  }

  protected void assertGeneratedTestSources(String moduleName, String... expectedSources) {
    assertGeneratedSources(moduleName, JavaSourceRootType.TEST_SOURCE, expectedSources);
  }

  private void assertGeneratedSources(String moduleName, JavaSourceRootType type, String... expectedSources) {
    final ContentEntry[] contentRoots = getContentRoots(moduleName);
    final String rootUrl = contentRoots.length > 1 ? ExternalSystemApiUtil.getExternalProjectPath(getModule(moduleName)) : null;
    List<SourceFolder> folders = doAssertContentFolders(rootUrl, contentRoots, type, expectedSources);
    for (SourceFolder folder : folders) {
      JavaSourceRootProperties properties = folder.getJpsElement().getProperties(type);
      assertNotNull(properties);
      assertTrue("Not a generated folder: " + folder, properties.isForGeneratedSources());
    }
  }

  protected void assertResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.RESOURCE, expectedSources);
  }

  protected void assertTestSources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaSourceRootType.TEST_SOURCE, expectedSources);
  }

  protected void assertTestResources(String moduleName, String... expectedSources) {
    doAssertContentFolders(moduleName, JavaResourceRootType.TEST_RESOURCE, expectedSources);
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, Arrays.asList(contentRoot.getExcludeFolders()), expectedExcludes);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, Arrays.asList(root.getExcludeFolders()), expectedExcudes);
  }

  private void doAssertContentFolders(String moduleName, @NotNull JpsModuleSourceRootType<?> rootType, String... expected) {
    final ContentEntry[] contentRoots = getContentRoots(moduleName);
    final String rootUrl = contentRoots.length > 1 ? ExternalSystemApiUtil.getExternalProjectPath(getModule(moduleName)) : null;
    doAssertContentFolders(rootUrl, contentRoots, rootType, expected);
  }

  private static List<SourceFolder> doAssertContentFolders(@Nullable String rootUrl,
                                                           ContentEntry[] contentRoots,
                                                           @NotNull JpsModuleSourceRootType<?> rootType,
                                                           String... expected) {
    List<SourceFolder> result = new ArrayList<>();
    List<String> actual = new ArrayList<>();
    for (ContentEntry contentRoot : contentRoots) {
      for (SourceFolder f : contentRoot.getSourceFolders(rootType)) {
        rootUrl = rootUrl == null ? VirtualFileManager.extractPath(contentRoot.getUrl()) : VirtualFileManager.extractPath(rootUrl);
        String folderUrl = VirtualFileManager.extractPath(f.getUrl());
        if (folderUrl.startsWith(rootUrl)) {
          int length = rootUrl.length() + 1;
          folderUrl = folderUrl.substring(Math.min(length, folderUrl.length()));
        }

        actual.add(folderUrl);
        result.add(f);
      }
    }

    assertOrderedElementsAreEqual(actual, Arrays.asList(expected));
    return result;
  }

  private static void doAssertContentFolders(ContentEntry e, final List<? extends ContentFolder> folders, String... expected) {
    List<String> actual = new ArrayList<>();
    for (ContentFolder f : folders) {
      String rootUrl = e.getUrl();
      String folderUrl = f.getUrl();

      if (folderUrl.startsWith(rootUrl)) {
        int length = rootUrl.length() + 1;
        folderUrl = folderUrl.substring(Math.min(length, folderUrl.length()));
      }

      actual.add(folderUrl);
    }

    assertOrderedElementsAreEqual(actual, Arrays.asList(expected));
  }

  protected void assertModuleOutputs(String moduleName, String... outputs) {
    String[] outputPaths = ContainerUtil.map2Array(CompilerPathsEx.getOutputPaths(new Module[]{getModule(moduleName)}), String.class,
                                                   s -> getAbsolutePath(s));
    assertUnorderedElementsAreEqual(outputPaths, outputs);
  }

  protected void assertModuleOutput(String moduleName, String output, String testOutput) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);

    assertFalse(e.isCompilerOutputPathInherited());
    assertEquals(output, getAbsolutePath(e.getCompilerOutputUrl()));
    assertEquals(testOutput, getAbsolutePath(e.getCompilerOutputUrlForTests()));
  }

  protected void assertModuleInheritedOutput(String moduleName) {
    CompilerModuleExtension e = getCompilerExtension(moduleName);
    assertTrue(e.isCompilerOutputPathInherited());
  }

  private static String getAbsolutePath(String path) {
    path = VfsUtil.urlToPath(path);
    path = PathUtil.getCanonicalPath(path);
    return FileUtil.toSystemIndependentName(path);
  }

  protected void assertProjectOutput(String module) {
    assertTrue(getCompilerExtension(module).isCompilerOutputPathInherited());
  }

  protected CompilerModuleExtension getCompilerExtension(String module) {
    return CompilerModuleExtension.getInstance(getModule(module));
  }

  protected void assertModuleLibDep(String moduleName, String depName) {
    assertModuleLibDep(moduleName, depName, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath) {
    assertModuleLibDep(moduleName, depName, classesPath, null, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath, String sourcePath, String javadocPath) {
    LibraryOrderEntry lib = ContainerUtil.getFirstItem(getModuleLibDeps(moduleName, depName));

    assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPath == null ? null : Collections.singletonList(classesPath));
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePath == null ? null : Collections.singletonList(sourcePath));
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPath == null ? null : Collections.singletonList(javadocPath));
  }

  protected void assertModuleLibDep(String moduleName,
                                    String depName,
                                    List<String> classesPaths,
                                    List<String> sourcePaths,
                                    List<String> javadocPaths) {
    LibraryOrderEntry lib = ContainerUtil.getFirstItem(getModuleLibDeps(moduleName, depName));

    assertModuleLibDepPath(lib, OrderRootType.CLASSES, classesPaths);
    assertModuleLibDepPath(lib, OrderRootType.SOURCES, sourcePaths);
    assertModuleLibDepPath(lib, JavadocOrderRootType.getInstance(), javadocPaths);
  }

  private static void assertModuleLibDepPath(LibraryOrderEntry lib, OrderRootType type, List<String> paths) {
    assertNotNull(lib);
    if (paths == null) return;
    assertUnorderedPathsAreEqual(Arrays.asList(lib.getRootUrls(type)), paths);
    // also check the library because it may contain slight different set of urls (e.g. with duplicates)
    final Library library = lib.getLibrary();
    assertNotNull(library);
    assertUnorderedPathsAreEqual(Arrays.asList(library.getUrls(type)), paths);
  }

  protected void assertModuleLibDepScope(String moduleName, String depName, DependencyScope... scopes) {
    List<LibraryOrderEntry> deps = getModuleLibDeps(moduleName, depName);
    assertUnorderedElementsAreEqual(ContainerUtil.map2Array(deps, entry -> entry.getScope()), scopes);
  }

  protected List<LibraryOrderEntry> getModuleLibDeps(String moduleName, String depName) {
    return getModuleDep(moduleName, depName, LibraryOrderEntry.class);
  }

  protected void assertModuleLibDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, LibraryOrderEntry.class, expectedDeps);
  }

  protected void assertExportedDeps(String moduleName, String... expectedDeps) {
    final List<String> actual = new ArrayList<>();

    getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process(new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry e, Object value) {
        actual.add(e.getModuleName());
        return null;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry e, Object value) {
        actual.add(e.getLibraryName());
        return null;
      }
    }, null);

    assertOrderedElementsAreEqual(actual, expectedDeps);
  }

  protected void assertModuleModuleDeps(String moduleName, String... expectedDeps) {
    assertModuleDeps(moduleName, ModuleOrderEntry.class, expectedDeps);
  }

  private void assertModuleDeps(String moduleName, Class clazz, String... expectedDeps) {
    assertOrderedElementsAreEqual(collectModuleDepsNames(moduleName, clazz), expectedDeps);
  }

  protected void assertModuleModuleDepScope(String moduleName, String depName, DependencyScope... scopes) {
    List<ModuleOrderEntry> deps = getModuleModuleDeps(moduleName, depName);
    assertUnorderedElementsAreEqual(ContainerUtil.map2Array(deps, entry -> entry.getScope()), scopes);
  }

  @NotNull
  private List<ModuleOrderEntry> getModuleModuleDeps(@NotNull String moduleName, @NotNull String depName) {
    return getModuleDep(moduleName, depName, ModuleOrderEntry.class);
  }

  private List<String> collectModuleDepsNames(String moduleName, Class clazz) {
    List<String> actual = new ArrayList<>();

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e)) {
        actual.add(e.getPresentableName());
      }
    }
    return actual;
  }

  @NotNull
  private <T> List<T> getModuleDep(@NotNull String moduleName, @NotNull String depName, @NotNull Class<T> clazz) {
    List<T> deps = ContainerUtil.newArrayList();

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (clazz.isInstance(e) && e.getPresentableName().equals(depName)) {
        deps.add((T)e);
      }
    }
    assertNotNull("Dependency not found: " + depName + "\namong: " + collectModuleDepsNames(moduleName, clazz), deps);
    return deps;
  }

  public void assertProjectLibraries(String... expectedNames) {
    List<String> actualNames = new ArrayList<>();
    for (Library each : ProjectLibraryTable.getInstance(myProject).getLibraries()) {
      String name = each.getName();
      actualNames.add(name == null ? "<unnamed>" : name);
    }
    assertUnorderedElementsAreEqual(actualNames, expectedNames);
  }

  protected void assertModuleGroupPath(String moduleName, String... expected) {
    String[] path = ModuleManager.getInstance(myProject).getModuleGroupPath(getModule(moduleName));

    if (expected.length == 0) {
      assertNull(path);
    }
    else {
      assertNotNull(path);
      assertOrderedElementsAreEqual(Arrays.asList(path), expected);
    }
  }

  protected void assertArtifacts(String... expectedNames) {
    final List<String> actualNames = ContainerUtil.map(
      ArtifactManager.getInstance(myProject).getAllArtifactsIncludingInvalid(), new Function<Artifact, String>() {
        @Override
        public String fun(Artifact artifact) {
          return artifact.getName();
        }
      });

    assertUnorderedElementsAreEqual(actualNames, expectedNames);
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

  private ContentEntry getContentRoot(String moduleName) {
    ContentEntry[] ee = getContentRoots(moduleName);
    List<String> roots = new ArrayList<>();
    for (ContentEntry e : ee) {
      roots.add(e.getUrl());
    }

    String message = "Several content roots found: [" + StringUtil.join(roots, ", ") + "]";
    assertEquals(message, 1, ee.length);

    return ee[0];
  }

  private ContentEntry getContentRoot(String moduleName, String path) {
    for (ContentEntry e : getContentRoots(moduleName)) {
      if (e.getUrl().equals(VfsUtilCore.pathToUrl(path))) return e;
    }
    throw new AssertionError("content root not found");
  }

  public ContentEntry[] getContentRoots(String moduleName) {
    return getRootManager(moduleName).getContentEntries();
  }

  private ModuleRootManager getRootManager(String module) {
    return ModuleRootManager.getInstance(getModule(module));
  }

  protected void ignoreData(BooleanFunction<DataNode<?>> booleanFunction, final boolean ignored) {
    final ExternalProjectInfo externalProjectInfo = ProjectDataManager.getInstance().getExternalProjectData(
      myProject, getExternalSystemId(), getCurrentExternalProjectSettings().getExternalProjectPath());
    assertNotNull(externalProjectInfo);

    final DataNode<ProjectData> projectDataNode = externalProjectInfo.getExternalProjectStructure();
    assertNotNull(projectDataNode);

    final Collection<DataNode<?>> nodes = ExternalSystemApiUtil.findAllRecursively(projectDataNode, booleanFunction);
    for (DataNode<?> node : nodes) {
      ExternalSystemApiUtil.visit(node, dataNode -> dataNode.setIgnored(ignored));
    }
    ServiceManager.getService(ProjectDataManager.class).importData(projectDataNode, myProject, true);
  }

  protected void importProject(@NonNls String config) throws IOException {
    createProjectConfig(config);
    importProject();
  }

  protected void importProject() {
    doImportProject();
  }

  private void doImportProject() {
    AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(myProject, getExternalSystemId());
    final ExternalProjectSettings projectSettings = getCurrentExternalProjectSettings();
    projectSettings.setExternalProjectPath(getProjectPath());
    Set<ExternalProjectSettings> projects = ContainerUtilRt.newHashSet(systemSettings.getLinkedProjectsSettings());
    projects.remove(projectSettings);
    projects.add(projectSettings);
    systemSettings.setLinkedProjectsSettings(projects);

    final Ref<Couple<String>> error = Ref.create();
    ExternalSystemUtil.refreshProjects(
      new ImportSpecBuilder(myProject, getExternalSystemId())
        .use(ProgressExecutionMode.MODAL_SYNC)
        .callback(new ExternalProjectRefreshCallback() {
          @Override
          public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
            if (externalProject == null) {
              System.err.println("Got null External project after import");
              return;
            }
            ServiceManager.getService(ProjectDataManager.class).importData(externalProject, myProject, true);
            System.out.println("External project was successfully imported");
          }

          @Override
          public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
            error.set(Couple.of(errorMessage, errorDetails));
          }
        })
        .forceWhenUptodate()
    );

    if (!error.isNull()) {
      String failureMsg = "Import failed: " + error.get().first;
      if (StringUtil.isNotEmpty(error.get().second)) {
        failureMsg += "\nError details: \n" + error.get().second;
      }
      fail(failureMsg);
    }
  }

  protected abstract ExternalProjectSettings getCurrentExternalProjectSettings();

  protected abstract ProjectSystemId getExternalSystemId();

  protected void setupJdkForModules(String... moduleNames) {
    for (String each : moduleNames) {
      setupJdkForModule(each);
    }
  }

  protected Sdk setupJdkForModule(final String moduleName) {
    final Sdk sdk = true ? JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk() : createJdk("Java 1.5");
    ModuleRootModificationUtil.setModuleSdk(getModule(moduleName), sdk);
    return sdk;
  }

  protected static Sdk createJdk(String versionName) {
    return IdeaTestUtil.getMockJdk17(versionName);
  }

  protected static AtomicInteger configConfirmationForYesAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    Messages.setTestDialog(new TestDialog() {
      @Override
      public int show(String message) {
        counter.set(counter.get() + 1);
        return 0;
      }
    });
    return counter;
  }

  protected static AtomicInteger configConfirmationForNoAnswer() {
    final AtomicInteger counter = new AtomicInteger();
    Messages.setTestDialog(new TestDialog() {
      @Override
      public int show(String message) {
        counter.set(counter.get() + 1);
        return 1;
      }
    });
    return counter;
  }

  //protected void assertProblems(String... expectedProblems) {
  //  final List<String> actualProblems = new ArrayList<String>();
  //  UIUtil.invokeAndWaitIfNeeded(new Runnable() {
  //    @Override
  //    public void run() {
  //      final NewErrorTreeViewPanel messagesView = ExternalSystemNotificationManager.getInstance(myProject)
  //        .prepareMessagesView(getExternalSystemId(), NotificationSource.PROJECT_SYNC, false);
  //      final ErrorViewStructure treeStructure = messagesView.getErrorViewStructure();
  //
  //      ErrorTreeElement[] elements = treeStructure.getChildElements(treeStructure.getRootElement());
  //      for (ErrorTreeElement element : elements) {
  //        if (element.getKind() == ErrorTreeElementKind.ERROR ||
  //            element.getKind() == ErrorTreeElementKind.WARNING) {
  //          actualProblems.add(StringUtil.join(element.getText(), "\n"));
  //        }
  //      }
  //    }
  //  });
  //
  //  assertOrderedElementsAreEqual(actualProblems, expectedProblems);
  //}
}
