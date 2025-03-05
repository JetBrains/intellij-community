// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.*;
import com.intellij.tracing.Tracer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.task.impl.ProjectTaskManagerStatisticsCollector.*;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.groupingBy;

/**
 * @author Vladislav.Soroka
 */
public final class ProjectTaskManagerImpl extends ProjectTaskManager {
  private static final Logger LOG = Logger.getInstance(ProjectTaskManager.class);
  private static final Key<Class<?>> BUILD_ORIGINATOR_KEY = Key.create("project task build originator");

  private final ProjectTaskRunner myDummyTaskRunner = new DummyTaskRunner();
  private final ProjectTaskListener myEventPublisher;
  private final List<ProjectTaskManagerListener> myListeners = new CopyOnWriteArrayList<>();

  public ProjectTaskManagerImpl(@NotNull Project project) {
    super(project);
    myEventPublisher = project.getMessageBus().syncPublisher(ProjectTaskListener.TOPIC);
  }

  @Override
  public Promise<Result> build(Module @NotNull [] modules) {
    return run(createModulesBuildTask(modules, true, true, false));
  }

  @Override
  public Promise<Result> rebuild(Module @NotNull [] modules) {
    return run(createModulesBuildTask(modules, false, false, false));
  }

  @ApiStatus.Internal
  public ProjectTask createModulesFilesTask(VirtualFile @NotNull [] files) {
    Map<Module, List<Pair<VirtualFile, Module>>> modulesMap = stream(files)
      .map(file -> new Pair<>(file, ProjectFileIndex.getInstance(myProject).getModuleForFile(file, false)))
      .filter(pair -> pair.second != null)
      .collect(groupingBy(pair -> pair.second));

    var tasks = map(
      modulesMap.entrySet(),
      entry -> new ModuleFilesBuildTaskImpl(entry.getKey(), false, map(entry.getValue(), pair -> pair.first))
    );

    return new ProjectTaskList(tasks);
  }

  @Override
  public Promise<Result> compile(VirtualFile @NotNull [] files) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return run(createModulesFilesTask(files));
    }

    ProjectTask task = ReadAction.nonBlocking(() -> this.createModulesFilesTask(files)).executeSynchronously();
    return run(task);
  }

  @Override
  public Promise<Result> build(ProjectModelBuildableElement @NotNull [] buildableElements) {
    return doBuild(buildableElements, true);
  }

  @Override
  public Promise<Result> rebuild(ProjectModelBuildableElement @NotNull [] buildableElements) {
    return doBuild(buildableElements, false);
  }

  @Override
  public Promise<Result> buildAllModules() {
    return run(createAllModulesBuildTask(true, myProject));
  }

  @Override
  public Promise<Result> rebuildAllModules() {
    return run(createAllModulesBuildTask(false, myProject));
  }

  @Override
  public ProjectTask createAllModulesBuildTask(boolean isIncrementalBuild, Project project) {
    return createModulesBuildTask(ModuleManager.getInstance(project).getModules(), isIncrementalBuild, false, false);
  }

  @Override
  public ProjectTask createModulesBuildTask(Module[] modules, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies, boolean includeTests) {
    if (modules.length == 1) {
      return new ModuleBuildTaskImpl(modules[0], isIncrementalBuild, includeDependentModules, includeRuntimeDependencies, includeTests);
    }
    return new ProjectTaskList(
      map(Arrays.asList(modules), module -> new ModuleBuildTaskImpl(module, isIncrementalBuild, includeDependentModules, includeRuntimeDependencies, includeTests))
    );
  }

  @Override
  public ProjectTask createBuildTask(boolean isIncrementalBuild, ProjectModelBuildableElement... buildableElements) {
    return buildableElements.length == 1
           ? new ProjectModelBuildTaskImpl<>(buildableElements[0], isIncrementalBuild)
           : new ProjectTaskList(map(Arrays.asList(buildableElements),
                                     buildableElement -> new ProjectModelBuildTaskImpl<>(buildableElement, isIncrementalBuild)));
  }

  @ApiStatus.Experimental
  @Override
  public @Nullable ExecutionEnvironment createProjectTaskExecutionEnvironment(@NotNull ProjectTask projectTask) {
    List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> toRun = groupByRunner(projectTask);
    if (toRun.isEmpty()) return null;
    Map<ProjectTaskRunner, List<ProjectTask>> tasksMap = new LinkedHashMap<>();
    for (Pair<ProjectTaskRunner, Collection<? extends ProjectTask>> pair : toRun) {
      tasksMap.computeIfAbsent(pair.first, runner -> new ArrayList<>()).addAll(pair.second);
    }
    if (tasksMap.size() != 1) {
      LOG.debug("Can not create single execution environment for tasks of different runners: '" + tasksMap + "'");
      return null;
    }
    Map.Entry<ProjectTaskRunner, List<ProjectTask>> entry = tasksMap.entrySet().iterator().next();
    ProjectTask[] tasks = entry.getValue().toArray(EMPTY_TASKS_ARRAY);
    ProjectTaskRunner taskRunner = entry.getKey();
    return taskRunner.createExecutionEnvironment(myProject, tasks);
  }

  @Override
  public Promise<Result> run(@NotNull ProjectTask projectTask) {
    return run(new ProjectTaskContext(), projectTask);
  }

  @Override
  public Promise<Result> run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask) {
    Tracer.Span buildSpan = Tracer.start("build");
    AsyncPromise<Result> promiseResult = new AsyncPromise<>();
    List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> toRun = groupByRunner(projectTask, context);

    buildSpan.complete();
    context.putUserData(ProjectTaskScope.KEY, new ProjectTaskScope() {
      @Override
      public @NotNull <T extends ProjectTask> List<T> getRequestedTasks(@NotNull Class<T> instanceOf) {
        List<T> tasks = new ArrayList<>();
        //noinspection unchecked
        toRun.forEach(pair -> pair.second.stream().filter(instanceOf::isInstance).map(task -> (T)task).forEach(tasks::add));
        return tasks;
      }
    });


    Pair<StructuredIdeActivity, List<EventPair<?>>> activity = reportBuildStart(projectTask, toRun);
    myEventPublisher.started(context);

    Runnable runnable = () -> {
      for (ProjectTaskManagerListener listener : myListeners) {
        try {
          listener.beforeRun(context);
        }
        catch (ExecutionException e) {
          sendAbortedEmptyResult(context, new ResultConsumer(promiseResult));
          activity.first.finished(() -> activity.second);
          return;
        }
      }

      for (ProjectTaskManagerListenerExtensionPoint listener : ProjectTaskManagerListenerExtensionPoint.EP_NAME.getExtensionList()) {
        try {
          listener.beforeRun(myProject, context);
        }
        catch (ExecutionException e) {
          sendAbortedEmptyResult(context, new ResultConsumer(promiseResult));
          activity.first.finished(() -> activity.second);
          return;
        }
      }

      if (toRun.isEmpty()) {
        sendSuccessEmptyResult(context, new ResultConsumer(promiseResult));
        activity.first.finished(() -> activity.second);
        return;
      }

      ProjectTaskResultsAggregator resultsCollector =
        new ProjectTaskResultsAggregator(context, new ResultConsumer(promiseResult), toRun.size(), activity);
      for (Pair<ProjectTaskRunner, Collection<? extends ProjectTask>> pair : toRun) {
        Consumer<ProjectTaskRunner.Result> runnerResultConsumer = result -> resultsCollector.add(result, pair.second);
        if (pair.second.isEmpty()) {
          runnerResultConsumer.accept(TaskRunnerResults.SUCCESS);
        }
        else {
          ProjectTaskRunner runner = pair.first;
          if (context.isCollectionOfGeneratedFilesEnabled() && !runner.isFileGeneratedEventsSupported()) {
            pair.second.stream()
              .filter(ModuleBuildTask.class::isInstance)
              .map(task -> ((ModuleBuildTask)task).getModule())
              .forEach(module -> context.addDirtyOutputPathsProvider(moduleOutputPathsProvider(module)));
          }
          runner
            .run(myProject, context, pair.second.toArray(EMPTY_TASKS_ARRAY))
            .onSuccess(runnerResultConsumer)
            .onError(throwable -> runnerResultConsumer.accept(TaskRunnerResults.ABORTED));
        }
      }
    };
    // do not run before tasks on EDT
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
    else {
      runnable.run();
    }
    promiseResult.onProcessed(result -> {
      buildSpan.complete();
    });
    return promiseResult;
  }

  private Pair<StructuredIdeActivity, List<EventPair<?>>> reportBuildStart(@NotNull ProjectTask projectTask,
                                                                           List<? extends Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> toRun) {
    Ref<Boolean> incremental = new Ref<>(null);
    AtomicInteger modules = new AtomicInteger(0);
    visitTask(projectTask, tasks -> {
      for (ProjectTask task : tasks) {
        if (task instanceof BuildTask) {
          boolean taskIncremental = ((BuildTask) task).isIncrementalBuild();
          // if any of the tasks is full rebuild, assume everything is full rebuild
          if (!taskIncremental) {
            incremental.set(false);
          }
          else if (incremental.get() == null) {
            incremental.set(true);
          }
        }

        if (task instanceof ModuleBuildTask) {
          modules.incrementAndGet();
        }
      }
    });

    List<EventPair<?>> fields = new SmartList<>();
    fields.add(TASK_RUNNER.with(map(toRun, it -> it.first.getClass())));
    if (incremental.get() != null) {
      fields.add(INCREMENTAL.with(incremental.get()));
    }
    if (modules.get() > 0) {
      fields.add(MODULES.with(modules.get()));
    }
    Class<?> buildOriginator = BUILD_ORIGINATOR_KEY.get(myProject);
    if (buildOriginator != null) {
      myProject.putUserData(BUILD_ORIGINATOR_KEY, null);
      fields.add(BUILD_ORIGINATOR.with(buildOriginator));
    }
    return Pair.create(BUILD_ACTIVITY.started(myProject, () -> fields), fields);
  }

  private List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> groupByRunner(@NotNull ProjectTask projectTask) {
    return groupByRunner(projectTask, null);
  }

  private List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> groupByRunner(@NotNull ProjectTask projectTask,
                                                                                         @Nullable ProjectTaskContext context) {
    List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> toRun = new SmartList<>();
    Consumer<Collection<? extends ProjectTask>> taskClassifier = tasks -> {
      Map<ProjectTaskRunner, ? extends List<? extends ProjectTask>> toBuild = tasks.stream().collect(
        groupingBy(aTask -> stream(ProjectTaskRunner.EP_NAME.getExtensions())
          .filter(runner -> {
            try {
              return runner.canRun(myProject, aTask, context);
            }
            catch (ProcessCanceledException e) {
              throw e;
            }
            catch (Throwable e) {
              LOG.error("Broken project task runner: " + runner.getClass().getName(), e);
            }
            return false;
          })
          .findFirst()
          .orElse(myDummyTaskRunner))
      );
      for (Map.Entry<ProjectTaskRunner, ? extends List<? extends ProjectTask>> entry : toBuild.entrySet()) {
        toRun.add(Pair.create(entry.getKey(), entry.getValue()));
      }
    };
    visitTask(projectTask, taskClassifier);
    return toRun;
  }

  private static void visitTask(@NotNull ProjectTask projectTask, Consumer<? super Collection<? extends ProjectTask>> taskClassifier) {
    visitTasks(projectTask instanceof ProjectTaskList ? (ProjectTaskList)projectTask : Collections.singleton(projectTask), taskClassifier);
  }

  public static void putBuildOriginator(@Nullable Project project, @NotNull Class<?> clazz) {
    if (BUILD_ORIGINATOR_KEY.get(project) == null) {
      BUILD_ORIGINATOR_KEY.set(project, clazz);
    }
  }

  @ApiStatus.Experimental
  public static @Nullable <T> T waitForPromise(@NotNull Promise<T> promise) {
    while (true) {
      try {
        return promise.blockingGet(10, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (java.util.concurrent.ExecutionException e) {
        ExceptionUtil.rethrow(e);
      }
      ProgressManager.checkCanceled();
    }
  }

  private static @NotNull Supplier<List<String>> moduleOutputPathsProvider(@NotNull Module module) {
    return () -> ReadAction.compute(() -> {
      return JBIterable.of(OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().getClassesRoots())
        .filterMap(file -> file.isDirectory() && !file.getFileSystem().isReadOnly() ? file.getPath() : null)
        .toList();
    });
  }

  public void addListener(@NotNull ProjectTaskManagerListener listener) {
    myListeners.add(listener);
  }

  private static void sendSuccessEmptyResult(@NotNull ProjectTaskContext context, @NotNull Consumer<? super Result> resultConsumer) {
    resultConsumer.accept(new MyResult(context, Collections.emptyMap(), false, false));
  }

  private static void sendAbortedEmptyResult(@NotNull ProjectTaskContext context, @NotNull Consumer<? super Result> resultConsumer) {
    resultConsumer.accept(new MyResult(context, Collections.emptyMap(), true, false));
  }

  private static void visitTasks(@NotNull Collection<? extends ProjectTask> tasks,
                                 @NotNull Consumer<? super Collection<? extends ProjectTask>> consumer) {
    if (tasks.isEmpty()) return;

    for (ProjectTask child : tasks) {
      Collection<? extends ProjectTask> taskDependencies;
      if (child instanceof AbstractProjectTask) {
        taskDependencies = ((AbstractProjectTask)child).getDependsOn();
      }
      else if (child instanceof ProjectTaskList) {
        taskDependencies = (ProjectTaskList)child;
      }
      else {
        taskDependencies = emptyList();
      }

      visitTasks(taskDependencies, consumer);
    }
    consumer.accept(tasks);
  }

  private Promise<Result> doBuild(ProjectModelBuildableElement @NotNull [] buildableElements, boolean isIncrementalBuild) {
    return run(createBuildTask(isIncrementalBuild, buildableElements));
  }

  private static final class DummyTaskRunner extends ProjectTaskRunner {
    @Override
    public Promise<Result> run(@NotNull Project project,
                               @NotNull ProjectTaskContext context,
                               ProjectTask @NotNull ... tasks) {
      return Promises.resolvedPromise(TaskRunnerResults.SUCCESS);
    }

    @Override
    public boolean canRun(@NotNull ProjectTask projectTask) {
      return true;
    }
  }

  private final class ResultConsumer implements Consumer<Result> {
    private final @NotNull AsyncPromise<Result> myPromise;

    private ResultConsumer(@NotNull AsyncPromise<Result> promise) {
      myPromise = promise;
    }

    @Override
    public void accept(@NotNull Result result) {
      if (!result.isAborted() && !result.hasErrors()) {
        // do not run after tasks on EDT
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            for (ProjectTaskManagerListener listener : myListeners) {
              listener.afterRun(result);
            }
            for (ProjectTaskManagerListenerExtensionPoint listener : ProjectTaskManagerListenerExtensionPoint.EP_NAME.getExtensionList()) {
              listener.afterRun(myProject, result);
            }
            notify(result);
          }
          catch (ExecutionException e) {
            LOG.debug(e);
            notify(new ResultWrapper(result) {
              @Override
              public boolean hasErrors() {
                return true;
              }
            });
          }
        });
      }
      else {
        notify(result);
      }
    }

    private void notify(@NotNull Result result) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
        if (!myProject.isDisposed()) {
          myEventPublisher.finished(result);
        }
        myPromise.setResult(result);
      });
    }
  }

  private static final class ProjectTaskResultsAggregator {
    private final ProjectTaskContext myContext;
    private final ResultConsumer myResultConsumer;
    private final AtomicInteger myProgressCounter;
    private final Pair<StructuredIdeActivity, List<EventPair<?>>> myActivity;
    private final AtomicBoolean myErrorsFlag;
    private final AtomicBoolean myAbortedFlag;
    private final Map<ProjectTask, ProjectTaskState> myTasksState = new ConcurrentHashMap<>();

    private ProjectTaskResultsAggregator(@NotNull ProjectTaskContext context,
                                         @NotNull ResultConsumer resultConsumer,
                                         int expectedResults,
                                         Pair<StructuredIdeActivity, List<EventPair<?>>> activity) {
      myContext = context;
      myResultConsumer = resultConsumer;
      myProgressCounter = new AtomicInteger(expectedResults);
      myActivity = activity;
      myErrorsFlag = new AtomicBoolean(false);
      myAbortedFlag = new AtomicBoolean(false);
    }

    public void add(@NotNull ProjectTaskRunner.Result result, @NotNull Collection<? extends ProjectTask> tasks) {
      int inProgress = myProgressCounter.decrementAndGet();
      ProjectTaskState state = new ProjectTaskState() {
        @Override
        public boolean isSkipped() {
          return result.isAborted();
        }

        @Override
        public boolean isFailed() {
          return result.hasErrors();
        }
      };
      for (ProjectTask task : tasks) {
        myTasksState.put(task, state);
      }

      if (result.isAborted()) {
        myAbortedFlag.set(true);
      }
      if (result.hasErrors()) {
        myErrorsFlag.set(true);
      }
      if (inProgress <= 0) {
        try {
          myResultConsumer.accept(new MyResult(myContext, myTasksState, myAbortedFlag.get(), myErrorsFlag.get()));
        }
        finally {
          List<EventPair<?>> events = new ArrayList<>(myActivity.second);
          events.add(HAS_ERRORS.with(myErrorsFlag.get()));
          myActivity.first.finished(() -> events);
        }
      }
    }
  }

  private static final class MyResult implements Result {
    private final ProjectTaskContext myContext;
    private final boolean myAborted;
    private final boolean myErrors;
    private final Map<ProjectTask, ProjectTaskState> myTasksState;

    private MyResult(@NotNull ProjectTaskContext context,
                     @NotNull Map<ProjectTask, ProjectTaskState> tasksState,
                     boolean isAborted,
                     boolean hasErrors) {
      myContext = context;
      myTasksState = tasksState;
      myAborted = isAborted;
      myErrors = hasErrors;
    }

    @Override
    public @NotNull ProjectTaskContext getContext() {
      return myContext;
    }

    @Override
    public boolean isAborted() {
      return myAborted;
    }

    @Override
    public boolean hasErrors() {
      return myErrors;
    }

    @Override
    public boolean anyTaskMatches(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate) {
      return myTasksState.entrySet().stream().anyMatch(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }
  }

  private static class ResultWrapper implements Result {
    private final Result myResult;

    private ResultWrapper(Result result) {myResult = result;}

    @Override
    public @NotNull ProjectTaskContext getContext() {
      return myResult.getContext();
    }

    @Override
    public boolean isAborted() {
      return myResult.isAborted();
    }

    @Override
    public boolean hasErrors() {
      return myResult.hasErrors();
    }

    @Override
    public boolean anyTaskMatches(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate) {
      return myResult.anyTaskMatches(predicate);
    }
  }
}
