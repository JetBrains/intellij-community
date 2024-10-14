// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.impl.CompileScopeUtil;
import com.intellij.compiler.impl.CompositeScope;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.packaging.impl.compiler.ArtifactsCompiler;
import com.intellij.packaging.impl.compiler.ArtifactsWorkspaceSettings;
import com.intellij.task.*;
import com.intellij.tracing.Tracer;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vladislav.Soroka
 */
public final class JpsProjectTaskRunner extends ProjectTaskRunner {
  private static final Logger LOG = Logger.getInstance(JpsProjectTaskRunner.class);
  @ApiStatus.Internal
  public static final Key<JpsBuildData> JPS_BUILD_DATA_KEY = KeyWithDefaultValue.create("jps_build_data", () -> new MyJpsBuildData());
  @ApiStatus.Internal
  public static final Key<Object> EXECUTION_SESSION_ID_KEY = ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY;

  @Override
  public Promise<Result> run(@NotNull Project project, @NotNull ProjectTaskContext context, ProjectTask @NotNull ... tasks) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    Tracer.Span jpsRunnerStart = Tracer.start("jps runner");
    context.putUserData(JPS_BUILD_DATA_KEY, new MyJpsBuildData());
    if (context.isCollectionOfGeneratedFilesEnabled()) {
      SimpleMessageBusConnection fileGeneratedTopicConnection = project.getMessageBus().simpleConnect();
      fileGeneratedTopicConnection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
        @Override
        public void fileGenerated(@NotNull String outputRoot, @NotNull String relativePath) {
          context.fileGenerated(outputRoot, relativePath);
        }
      });
      promise.onProcessed(result -> {
        fileGeneratedTopicConnection.disconnect();
      });
    }
    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = groupBy(Arrays.asList(tasks));
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), project.getDisposed(), () -> {
      try (MyNotificationCollector notificationCollector = new MyNotificationCollector(context, promise)) {
        runModulesResourcesBuildTasks(project, context, notificationCollector, taskMap);
        runModulesBuildTasks(project, context, notificationCollector, taskMap);
        runFilesBuildTasks(project, notificationCollector, taskMap);
        runEmptyBuildTask(project, context, notificationCollector, taskMap);
        runArtifactsBuildTasks(project, context, notificationCollector, taskMap);
      }
    });
    jpsRunnerStart.complete();
    return promise;
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    return projectTask instanceof ModuleBuildTask || projectTask instanceof EmptyCompileScopeBuildTask ||
           (projectTask instanceof ProjectModelBuildTask && ((ProjectModelBuildTask<?>)projectTask).getBuildableElement() instanceof Artifact);
  }

  @Override
  public boolean isFileGeneratedEventsSupported() {
    return true;
  }

  public static Map<Class<? extends ProjectTask>, List<ProjectTask>> groupBy(@NotNull Collection<? extends ProjectTask> tasks) {
    return tasks.stream().collect(Collectors.groupingBy(o -> {
      if (o instanceof ModuleFilesBuildTask) return ModuleFilesBuildTask.class;
      if (o instanceof ModuleResourcesBuildTask) return ModuleResourcesBuildTask.class;
      if (o instanceof ModuleBuildTask) return ModuleBuildTask.class;
      if (o instanceof ProjectModelBuildTask) return ProjectModelBuildTask.class;
      if (o instanceof EmptyCompileScopeBuildTask) return EmptyCompileScopeBuildTask.class;
      return o.getClass();
    }));
  }

  private static void runModulesBuildTasks(@NotNull Project project,
                                           @NotNull ProjectTaskContext context,
                                           @NotNull MyNotificationCollector notificationCollector,
                                           @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> buildTasks = tasksMap.get(ModuleBuildTask.class);
    if (ContainerUtil.isEmpty(buildTasks)) {
      return;
    }

    ModulesBuildSettings buildSettings = assembleModulesBuildSettings(buildTasks);
    CompilerManager compilerManager = CompilerManager.getInstance(project);

    if (buildSettings.isRebuild()) {
      compilerManager.rebuildClean(new MyCompileStatusNotification(notificationCollector));
    }
    else {
      CompileScope scope = createScope(
        compilerManager, context, buildSettings.modules, buildSettings.includeDependentModules, buildSettings.includeRuntimeDependencies, buildSettings.includeTests
      );
      if (buildSettings.isIncrementalBuild) {
        compilerManager.make(scope, new MyCompileStatusNotification(notificationCollector));
      }
      else {
        compilerManager.compile(scope, new MyCompileStatusNotification(notificationCollector));
      }
    }
  }
  private static void runEmptyBuildTask(@NotNull Project project,
                                        @NotNull ProjectTaskContext context,
                                        @NotNull MyNotificationCollector notificationCollector,
                                        @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> buildTasks = tasksMap.get(EmptyCompileScopeBuildTask.class);
    if (ContainerUtil.isEmpty(buildTasks)) {
      return;
    }

    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = createScope(compilerManager, context, Collections.emptySet(), false, false, true);
    // this will effectively run all before- and after- compilation tasks registered within CompilerManager
    EmptyCompileScopeBuildTask task = (EmptyCompileScopeBuildTask)buildTasks.iterator().next();
    if (task.isIncrementalBuild()) {
      compilerManager.make(scope, new MyCompileStatusNotification(notificationCollector));
    }
    else {
      compilerManager.compile(scope, new MyCompileStatusNotification(notificationCollector));
    }
  }

  private static void runModulesResourcesBuildTasks(@NotNull Project project,
                                                    @NotNull ProjectTaskContext context,
                                                    @NotNull MyNotificationCollector notificationCollector,
                                                    @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> buildTasks = tasksMap.get(ModuleResourcesBuildTask.class);
    if (ContainerUtil.isEmpty(buildTasks)) return;

    CompilerManager compilerManager = CompilerManager.getInstance(project);

    ModulesBuildSettings buildSettings = assembleModulesBuildSettings(buildTasks);
    CompileScope scope = createScope(
      compilerManager, context, buildSettings.modules, buildSettings.includeDependentModules, buildSettings.includeRuntimeDependencies, buildSettings.includeTests
    );
    List<String> moduleNames = ContainerUtil.map(buildSettings.modules, Module::getName);
    CompileScopeUtil.setResourcesScopeForExternalBuild(scope, moduleNames);

    if (buildSettings.isIncrementalBuild) {
      compilerManager.make(scope, new MyCompileStatusNotification(notificationCollector));
    }
    else {
      compilerManager.compile(scope, new MyCompileStatusNotification(notificationCollector));
    }
  }

  private record ModulesBuildSettings(boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies,
                                      boolean includeTests, Collection<? extends Module> modules) {
    boolean isRebuild() {
      if (!isIncrementalBuild && !modules.isEmpty()) {
        final Module someModule = modules.iterator().next();
        final Module[] projectModules = ModuleManager.getInstance(someModule.getProject()).getModules();
        return projectModules.length == modules.size();
      }
      return false;
    }
  }

  private static ModulesBuildSettings assembleModulesBuildSettings(Collection<? extends ProjectTask> buildTasks) {
    Collection<Module> modules = new SmartList<>();
    Collection<ModuleBuildTask> incrementalTasks = new SmartList<>();
    Collection<ModuleBuildTask> excludeDependentTasks = new SmartList<>();
    Collection<ModuleBuildTask> excludeRuntimeTasks = new SmartList<>();
    Collection<ModuleBuildTask> excludeTests = new SmartList<>();

    for (ProjectTask buildProjectTask : buildTasks) {
      ModuleBuildTask moduleBuildTask = (ModuleBuildTask)buildProjectTask;
      modules.add(moduleBuildTask.getModule());

      if (moduleBuildTask.isIncrementalBuild()) {
        incrementalTasks.add(moduleBuildTask);
      }
      if (!moduleBuildTask.isIncludeDependentModules()) {
        excludeDependentTasks.add(moduleBuildTask);
      }
      if (!moduleBuildTask.isIncludeRuntimeDependencies()) {
        excludeRuntimeTasks.add(moduleBuildTask);
      }
      if (!moduleBuildTask.isIncludeTests()) {
        excludeTests.add(moduleBuildTask);
      }
    }

    boolean isIncrementalBuild = incrementalTasks.size() == buildTasks.size();
    boolean includeDependentModules = excludeDependentTasks.size() != buildTasks.size();
    boolean includeRuntimeDependencies = excludeRuntimeTasks.size() != buildTasks.size();
    boolean includeTests = excludeTests.size() != buildTasks.size();

    if (!isIncrementalBuild && !incrementalTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(incrementalTasks, "will be built ignoring incremental build setting");
    }
    if (includeDependentModules && !excludeDependentTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(excludeDependentTasks, "will be built along with dependent modules");
    }
    if (includeRuntimeDependencies && !excludeRuntimeTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(excludeRuntimeTasks, "will be built along with runtime dependencies");
    }
    if (includeTests && !excludeTests.isEmpty()) {
      assertModuleBuildSettingsConsistent(excludeTests, "will be built along with test classes");
    }
    return new ModulesBuildSettings(isIncrementalBuild, includeDependentModules, includeRuntimeDependencies, includeTests, modules);
  }

  private static void assertModuleBuildSettingsConsistent(Collection<? extends ModuleBuildTask> moduleBuildTasks, String warnMsg) {
    String moduleNames = StringUtil.join(moduleBuildTasks, task -> task.getModule().getName(), ", ");
    LOG.warn("Module" + (moduleBuildTasks.size() > 1 ? "s": "") + " : '" + moduleNames + "' " + warnMsg);
  }

  private static CompileScope createScope(CompilerManager compilerManager,
                                          ProjectTaskContext context,
                                          Collection<? extends Module> modules,
                                          boolean includeDependentModules,
                                          boolean includeRuntimeDependencies,
                                          boolean includeTests) {
    CompileScope scope = !modules.isEmpty()?
      compilerManager.createModulesCompileScope(modules.toArray(Module.EMPTY_ARRAY), includeDependentModules, includeRuntimeDependencies, includeTests):
      new CompositeScope(CompileScope.EMPTY_ARRAY);

    if (context.isAutoRun()) {
      CompileDriver.setCompilationStartedAutomatically(scope);
    }

    RunConfiguration configuration = context.getRunConfiguration();
    if (configuration != null) {
      scope.putUserData(CompilerManager.RUN_CONFIGURATION_KEY, configuration);
      scope.putUserData(CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY, configuration.getType().getId());
    }
    ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, context.getSessionId());
    return scope;
  }

  private static void runFilesBuildTasks(@NotNull Project project,
                                         @NotNull MyNotificationCollector notificationCollector,
                                         @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> filesTargets = tasksMap.get(ModuleFilesBuildTask.class);
    if (!ContainerUtil.isEmpty(filesTargets)) {
      VirtualFile[] files = filesTargets.stream()
        .flatMap(target -> Stream.of(((ModuleFilesBuildTask)target).getFiles()))
        .toArray(VirtualFile[]::new);
      CompilerManager.getInstance(project).compile(files, new MyCompileStatusNotification(notificationCollector));
    }
  }

  private static void runArtifactsBuildTasks(@NotNull Project project,
                                             @NotNull ProjectTaskContext context,
                                             @NotNull MyNotificationCollector notificationCollector,
                                             @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {

    Collection<? extends ProjectTask> buildTasks = tasksMap.get(ProjectModelBuildTask.class);
    if (!ContainerUtil.isEmpty(buildTasks)) {
      List<Artifact> toMake = new SmartList<>();
      List<Artifact> toCompile = new SmartList<>();
      for (ProjectTask buildProjectTask : buildTasks) {
        ProjectModelBuildTask buildTask = (ProjectModelBuildTask)buildProjectTask;
        ProjectModelBuildableElement buildableElement = buildTask.getBuildableElement();
        if (buildableElement instanceof Artifact) {
          if (buildTask.isIncrementalBuild()) {
            toMake.add((Artifact)buildableElement);
          }
          else {
            toCompile.add((Artifact)buildableElement);
          }
        }
      }

      buildArtifacts(project, toMake, context.getSessionId(), notificationCollector, false);
      buildArtifacts(project, toCompile, context.getSessionId(), notificationCollector, true);
    }
  }

  private static void buildArtifacts(@NotNull Project project,
                                     @NotNull List<? extends Artifact> artifacts,
                                     @Nullable Object sessionId,
                                     @NotNull MyNotificationCollector notificationCollector,
                                     boolean forceArtifactBuild) {
    if (!artifacts.isEmpty()) {
      final CompileScope scope = ArtifactCompileScope.createArtifactsScope(project, artifacts, forceArtifactBuild);
      ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(artifacts);
      ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, sessionId);
      //in external build we can set 'rebuild' flag per target type
      CompilerManager.getInstance(project).make(scope, new MyCompileStatusNotification(notificationCollector));
    }
  }

  private static final class MyNotificationCollector implements AutoCloseable {
    private static final Result FAILED_AND_ABORTED = new Result() {
      @Override
      public boolean isAborted() {
        return true;
      }

      @Override
      public boolean hasErrors() {
        return true;
      }
    };
    @NotNull private final ProjectTaskContext myContext;
    private final AsyncPromise<Result> myPromise;
    private boolean myCollectingStopped;

    private final Set<MyCompileStatusNotification> myNotifications = new ReferenceOpenHashSet<>();
    private int myErrors;
    private boolean myAborted;

    private MyNotificationCollector(@NotNull ProjectTaskContext context, @NotNull AsyncPromise<Result> promise) {
      myContext = context;
      myPromise = promise;
    }

    @Override
    synchronized public void close() {
      if (!myCollectingStopped) {
        myCollectingStopped = true;
        notifyFinished();
      }
    }

    private void notifyFinished() {
      if (myCollectingStopped && myNotifications.isEmpty()) {
        myPromise.setResult(myAborted && myErrors > 0 ? FAILED_AND_ABORTED :
                            myAborted ? TaskRunnerResults.ABORTED : 
                            myErrors > 0 ? TaskRunnerResults.FAILURE : 
                            TaskRunnerResults.SUCCESS);
      }
    }

    synchronized private void appendJpsBuildResult(boolean aborted, int errors,
                                                   @NotNull CompileContext compileContext,
                                                   @NotNull MyCompileStatusNotification notification) {
      final boolean notificationRemoved = myNotifications.remove(notification);
      if (!notificationRemoved) {
        LOG.error("Multiple invocation of the same callback");
      }
      myErrors += errors;
      if (aborted) myAborted = true;
      MyJpsBuildData jpsBuildData = (MyJpsBuildData)JPS_BUILD_DATA_KEY.get(myContext);
      jpsBuildData.add(compileContext);

      if (notificationRemoved) {
        notifyFinished();
      }
    }

    synchronized private void add(@NotNull MyCompileStatusNotification notification) {
      assert !myCollectingStopped;
      if (!myNotifications.add(notification)) {
        LOG.error("Do not use the same callback for different JPS invocations");
      }
    }
  }

  private static final class MyCompileStatusNotification implements CompileStatusNotification {

    private final MyNotificationCollector myCollector;
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Tracer.Span mySpan = Tracer.start("jps task"); // which?

    private MyCompileStatusNotification(@NotNull MyNotificationCollector collector) {
      myCollector = collector;
      myCollector.add(this);
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
      if (finished.compareAndSet(false, true)) {
        myCollector.appendJpsBuildResult(aborted, errors, compileContext, this);
        mySpan.complete();
      } else {
        // can be invoked by CompileDriver for rerun action
        LOG.debug("Multiple invocation of the same CompileStatusNotification.");
      }
    }
  }

  private static class MyJpsBuildData implements JpsBuildData {
    private final List<CompileContext> myContexts = new ArrayList<>();

    @NotNull
    @Override
    public Set<String> getArtifactsWrittenPaths() {
      return myContexts.stream()
        .map(ctx -> ArtifactsCompiler.getWrittenPaths(ctx))
        .filter(Objects::nonNull)
        .flatMap(set -> set.stream())
        .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public List<CompileContext> getFinishedBuildsContexts() {
      return Collections.unmodifiableList(myContexts);
    }

    private void add(@NotNull CompileContext context) {
      myContexts.add(context);
    }
  }
}
