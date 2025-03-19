// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.externalSystem.testFramework.utils.module.ExternalSystemSourceRootAssertion;
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions;
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions;
import com.intellij.platform.testFramework.assertion.moduleAssertion.SourceRootAssertions;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.platform.externalSystem.testFramework.utils.module.ExternalSystemSourceRootAssertions.getExType;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndGet;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;

/**
 * @author Vladislav.Soroka
 */
public abstract class ExternalSystemImportingTestCase extends ExternalSystemTestCase {

  private @Nullable Disposable myTestDisposable = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    installExecutionOutputPrinter(getTestDisposable());
  }

  @Override
  public void tearDown() throws Exception {
    new RunAll(
      () -> Disposer.dispose(getTestDisposable()),
      () -> super.tearDown()
    ).run();
  }

  private @NotNull Disposable getTestDisposable() {
    if (myTestDisposable == null) {
      myTestDisposable = Disposer.newDisposable();
    }
    return myTestDisposable;
  }

  public static void installExecutionOutputPrinter(@NotNull Disposable parentDisposable) {
    var notificationManager = ExternalSystemProgressNotificationManager.getInstance();
    var notificationListener = new ExternalSystemTaskNotificationListener() {

      @Override
      public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
        System.out.print(id + "\\n");
      }

      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (stdOut) {
          System.out.print(text.replace("\n", "\\n").replace("\r", "\\r"));
        }
        else {
          System.err.print(text);
        }
      }

      @Override
      public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
        System.out.println();
      }
    };
    notificationManager.addNotificationListener(notificationListener, parentDisposable);
  }

  protected void assertModulesContains(String... expectedNames) {
    ModuleAssertions.assertModulesContains(myProject, expectedNames);
  }

  protected void assertModules(String... expectedNames) {
    ModuleAssertions.assertModules(myProject, expectedNames);
  }

  protected void assertModules(List<String> expectedNames) {
    ModuleAssertions.assertModules(myProject, expectedNames);
  }

  protected void assertContentRoots(String moduleName, String... expectedRoots) {
    var expectedRootPaths = ContainerUtil.map(expectedRoots, it -> Path.of(it));
    ContentRootAssertions.assertContentRoots(myProject, moduleName, expectedRootPaths);
  }

  protected void assertNoSourceRoots(String moduleName) {
    assertSourceRoots(moduleName, it -> {});
  }

  protected void assertSourceRoots(String moduleName, Consumer<ExternalSystemSourceRootAssertion<String>> applyAssertion) {
    ExternalSystemSourceRootAssertion.assertSourceRoots(applyAssertion, (type, expectedRoots) -> {
      assertSourceRoots(moduleName, type, expectedRoots);
    });
  }

  protected void assertSourceRoots(String moduleName, ExternalSystemSourceType type, List<String> expectedRoots) {
    var expectedRootPaths = ContainerUtil.map(expectedRoots, it -> Path.of(it));
    SourceRootAssertions.assertSourceRoots(myProject, moduleName, it -> type.equals(getExType(it)), expectedRootPaths, () ->
      "%s source root of type %s".formatted(moduleName, type)
    );
  }

  protected void assertExcludes(String moduleName, String... expectedExcludes) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    doAssertContentFolders(contentRoot, Arrays.asList(contentRoot.getExcludeFolders()), expectedExcludes);
  }

  protected void assertExcludePatterns(String moduleName, String... expectedPatterns) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    assertUnorderedElementsAreEqual(contentRoot.getExcludePatterns(), Arrays.asList(expectedPatterns));
  }

  protected void assertNoExcludePatterns(String moduleName, String... nonExpectedPatterns) {
    ContentEntry contentRoot = getContentRoot(moduleName);
    assertDoesntContain(contentRoot.getExcludePatterns(), nonExpectedPatterns);
  }

  protected void assertContentRootExcludes(String moduleName, String contentRoot, String... expectedExcudes) {
    ContentEntry root = getContentRoot(moduleName, contentRoot);
    doAssertContentFolders(root, Arrays.asList(root.getExcludeFolders()), expectedExcudes);
  }

  private void assertSourceFolders(
    @NotNull String moduleName,
    @NotNull JpsModuleSourceRootType<?> rootType,
    @NotNull List<String> expected
  ) {
    assertSourceFolders(moduleName, rootType, __ -> true, expected);
  }

  private void assertGeneratedSourceFolders(
    @NotNull String moduleName,
    @NotNull JavaSourceRootType rootType,
    @NotNull List<String> expectedSources
  ) {
    assertSourceFolders(moduleName, rootType, it -> isGeneratedSource(it, rootType), expectedSources);
  }

  private void assertGeneratedResourceFolders(
    @NotNull String moduleName,
    @NotNull JavaResourceRootType rootType,
    @NotNull List<String> expectedSources
  ) {
    assertSourceFolders(moduleName, rootType, it -> isGeneratedResource(it, rootType), expectedSources);
  }

  private static boolean isGeneratedSource(
    @NotNull SourceFolder sourceFolder,
    @NotNull JavaSourceRootType rootType
  ) {
    var element = sourceFolder.getJpsElement();
    var properties = element.getProperties(rootType);
    return properties != null && properties.isForGeneratedSources();
  }

  private static boolean isGeneratedResource(
    @NotNull SourceFolder sourceFolder,
    @NotNull JavaResourceRootType rootType
  ) {
    var element = sourceFolder.getJpsElement();
    var properties = element.getProperties(rootType);
    return properties != null && properties.isForGeneratedSources();
  }

  private void assertSourceFolders(
    @NotNull String moduleName,
    @NotNull JpsModuleSourceRootType<?> rootType,
    @NotNull Predicate<SourceFolder> sourceFolderFilter,
    @NotNull List<String> expectedSources
  ) {
    var actualSources = new ArrayList<String>();
    for (var contentRoot : getContentRoots(moduleName)) {
      for (var sourceFolder : contentRoot.getSourceFolders(rootType)) {
        if (sourceFolderFilter.test(sourceFolder)) {
          actualSources.add(VirtualFileManager.extractPath(sourceFolder.getUrl()));
        }
      }
    }
    assertUnorderedElementsAreEqual(actualSources, expectedSources);
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

  protected static String getAbsolutePath(String path) {
    path = VfsUtilCore.urlToPath(path);
    path = FileUtil.toCanonicalPath(path);
    return FileUtil.toSystemIndependentName(path);
  }

  protected void assertModuleLibDep(String moduleName, String depName) {
    assertModuleLibDep(moduleName, depName, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath) {
    assertModuleLibDep(moduleName, depName, classesPath, null, null);
  }

  protected void assertModuleLibDep(String moduleName, String depName, String classesPath, String sourcePath, String javadocPath) {
    LibraryOrderEntry lib = ContainerUtil.getFirstItem(getModuleLibDeps(moduleName, depName));
    final String errorMessage = "Failed to find dependency with name [" + depName + "] in module [" + moduleName + "]\n" +
                                "Available dependencies: " + collectModuleDepsNames(moduleName, LibraryOrderEntry.class);
    assertNotNull(errorMessage, lib);
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

  protected void assertModuleLibDeps(BiPredicate<? super String, ? super String> predicate, String moduleName, String... expectedDeps) {
    assertModuleDeps(predicate, moduleName, LibraryOrderEntry.class, expectedDeps);
  }

  protected void assertExportedDeps(String moduleName, String... expectedDeps) {
    final List<String> actual = new ArrayList<>();

    getRootManager(moduleName).orderEntries().withoutSdk().withoutModuleSourceEntries().exportedOnly().process(new RootPolicy<>() {
      @Override
      public Object visitModuleOrderEntry(@NotNull ModuleOrderEntry e, Object value) {
        actual.add(e.getModuleName());
        return null;
      }

      @Override
      public Object visitLibraryOrderEntry(@NotNull LibraryOrderEntry e, Object value) {
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
    assertModuleDeps(equalsPredicate(), moduleName, clazz, expectedDeps);
  }

  private void assertModuleDeps(BiPredicate<? super String, ? super String> predicate, String moduleName, Class clazz, String... expectedDeps) {
    assertOrderedElementsAreEqual(predicate, collectModuleDepsNames(moduleName, clazz), expectedDeps);
  }

  protected void assertProductionOnTestDependencies(String moduleName, String... expectedDeps) {
    assertOrderedElementsAreEqual(collectModuleDepsNames(
      moduleName, entry -> entry instanceof ModuleOrderEntry && ((ModuleOrderEntry)entry).isProductionOnTestDependency()
    ), expectedDeps);
  }

  protected void assertModuleModuleDepScope(String moduleName, String depName, DependencyScope... scopes) {
    List<ModuleOrderEntry> deps = getModuleModuleDeps(moduleName, depName);
    assertUnorderedElementsAreEqual(ContainerUtil.map2Array(deps, entry -> entry.getScope()), scopes);
  }

  private @NotNull List<ModuleOrderEntry> getModuleModuleDeps(@NotNull String moduleName, @NotNull String depName) {
    return getModuleDep(moduleName, depName, ModuleOrderEntry.class);
  }

  private List<String> collectModuleDepsNames(String moduleName, Predicate<? super OrderEntry> predicate) {
    List<String> actual = new ArrayList<>();

    for (OrderEntry e : getRootManager(moduleName).getOrderEntries()) {
      if (predicate.test(e)) {
        actual.add(e.getPresentableName());
      }
    }
    return actual;
  }

  private List<String> collectModuleDepsNames(String moduleName, Class clazz) {
    return collectModuleDepsNames(moduleName, entry -> clazz.isInstance(entry));
  }

  private @NotNull <T> List<T> getModuleDep(@NotNull String moduleName, @NotNull String depName, @NotNull Class<T> clazz) {
    List<T> deps = new ArrayList<>();

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
    for (Library each : LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraries()) {
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

  protected ModuleRootManager getRootManager(String module) {
    return ModuleRootManager.getInstance(getModule(module));
  }

  protected void ignoreData(Predicate<? super DataNode<?>> booleanFunction, final boolean ignored) {
    final ExternalProjectInfo externalProjectInfo = ProjectDataManagerImpl.getInstance().getExternalProjectData(
      myProject, getExternalSystemId(), getCurrentExternalProjectSettings().getExternalProjectPath());
    assertNotNull(externalProjectInfo);

    final DataNode<ProjectData> projectDataNode = externalProjectInfo.getExternalProjectStructure();
    assertNotNull(projectDataNode);

    final Collection<DataNode<?>> nodes = ExternalSystemApiUtil.findAllRecursively(projectDataNode, booleanFunction);
    for (DataNode<?> node : nodes) {
      node.visit(dataNode -> dataNode.setIgnored(ignored));
    }

    ExternalSystemTestUtilKt.importData(projectDataNode, myProject);
  }

  protected void importProject(@NotNull String config, @Nullable Boolean skipIndexing) throws IOException {
    createProjectConfig(config);
    importProject(skipIndexing);
  }

  protected void importProject(@Nullable Boolean skipIndexing) {
    if (skipIndexing != null) {
      PlatformTestUtil.withSystemProperty("idea.skip.indices.initialization", skipIndexing.toString(), () -> importProject());
    }
    else {
      importProject();
    }
  }

  protected void importProject() {
    AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(myProject, getExternalSystemId());
    final ExternalProjectSettings projectSettings = getCurrentExternalProjectSettings();
    projectSettings.setExternalProjectPath(getProjectPath());
    //noinspection unchecked
    Set<ExternalProjectSettings> projects = new HashSet<>(systemSettings.getLinkedProjectsSettings());
    projects.remove(projectSettings);
    projects.add(projectSettings);
    //noinspection unchecked
    systemSettings.setLinkedProjectsSettings(projects);

    final Ref<Couple<String>> error = Ref.create();
    ImportSpec importSpec = createImportSpec();
    ExternalProjectRefreshCallback callback = importSpec.getCallback();
    if (callback == null || callback instanceof ImportSpecBuilder.DefaultProjectRefreshCallback) {
      importSpec = new ImportSpecBuilder(importSpec).callback(new ExternalProjectRefreshCallback() {
        @Override
        public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
          if (externalProject == null) {
            System.err.println("Got null External project after import");
            return;
          }
          try {
            ProjectDataManager.getInstance().importData(externalProject, myProject);
          } catch (Throwable ex) {
            ex.printStackTrace(System.err);
            error.set(Couple.of("Exception occurred in `ProjectDataManager.importData` (see output for the details)", null));
          }
        }

        @Override
        public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
          error.set(Couple.of(errorMessage, errorDetails));
        }
      }).build();
    }

    ExternalSystemUtil.refreshProjects(importSpec);

    if (!error.isNull()) {
      handleImportFailure(error.get().first, error.get().second);
    }

    // allow all the invokeLater to pass through the queue, before waiting for indexes to be ready
    // (specifically, all the invokeLater that schedule indexing after language level change performed by import)
    runInEdtAndWait(() -> PlatformTestUtil.dispatchAllEventsInIdeEventQueue());
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
  }

  protected void handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    String failureMsg = "Import failed: " + errorMessage;
    if (StringUtil.isNotEmpty(errorDetails)) {
      failureMsg += "\nError details: \n" + errorDetails;
    }
    fail(failureMsg);
  }

  protected ImportSpec createImportSpec() {
    ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(myProject, getExternalSystemId())
      .use(ProgressExecutionMode.MODAL_SYNC);
    return importSpecBuilder.build();
  }

  protected abstract ExternalProjectSettings getCurrentExternalProjectSettings();

  protected abstract ProjectSystemId getExternalSystemId();

  protected static Collection<UsageInfo> findUsages(@NotNull PsiElement element) throws Exception {
    return ProgressManager.getInstance().run(new Task.WithResult<Collection<UsageInfo>, Exception>(null, "", false) {
      @Override
      protected Collection<UsageInfo> compute(@NotNull ProgressIndicator indicator) {
        return runInEdtAndGet(() -> {
          FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
          FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
          assertNotNull(handler);
          final FindUsagesOptions options = handler.getFindUsagesOptions();
          final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
          for (PsiElement element : handler.getPrimaryElements()) {
            handler.processElementUsages(element, processor, options);
          }
          for (PsiElement element : handler.getSecondaryElements()) {
            handler.processElementUsages(element, processor, options);
          }
          return processor.getResults();
        });
      }
    });
  }

  protected @Nullable SourceFolder findSource(@NotNull String moduleName, @NotNull String sourcePath) {
    return findSource(getRootManager(moduleName), sourcePath);
  }

  protected @Nullable SourceFolder findSource(@NotNull ModuleRootModel moduleRootManager, @NotNull String sourcePath) {
    ContentEntry[] contentRoots = moduleRootManager.getContentEntries();
    Module module = moduleRootManager.getModule();
    String rootUrl = getAbsolutePath(ExternalSystemApiUtil.getExternalProjectPath(module));
    for (ContentEntry contentRoot : contentRoots) {
      for (SourceFolder f : contentRoot.getSourceFolders()) {
        String folderPath = getAbsolutePath(f.getUrl());
        String rootPath = getAbsolutePath(rootUrl + "/" + sourcePath);
        if (folderPath.equals(rootPath)) return f;
      }
    }
    return null;
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
