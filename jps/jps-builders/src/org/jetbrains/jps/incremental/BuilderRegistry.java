package org.jetbrains.jps.incremental;

import org.jetbrains.jps.idea.OwnServiceLoader;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class BuilderRegistry {
  private static class Holder {
    static final BuilderRegistry ourInstance = new BuilderRegistry();
  }
  private final Map<BuilderCategory, List<ModuleLevelBuilder>> myModuleLevelBuilders = new HashMap<BuilderCategory, List<ModuleLevelBuilder>>();
  private final List<ProjectLevelBuilder> myProjectLevelBuilders = new ArrayList<ProjectLevelBuilder>();
  private ExecutorService myTasksExecutor;

  public static BuilderRegistry getInstance() {
    return Holder.ourInstance;
  }

  private BuilderRegistry() {
    for (BuilderCategory category : BuilderCategory.values()) {
      myModuleLevelBuilders.put(category, new ArrayList<ModuleLevelBuilder>());
    }
    final Runtime runtime = Runtime.getRuntime();
    myTasksExecutor = Executors.newFixedThreadPool(runtime.availableProcessors());
    runtime.addShutdownHook(new Thread() {
      public void run() {
        myTasksExecutor.shutdown();
      }
    });

    final OwnServiceLoader<BuilderService> loader = OwnServiceLoader.load(BuilderService.class);

    for (BuilderService service : loader) {
      myProjectLevelBuilders.addAll(service.createProjectLevelBuilders());
      final List<? extends ModuleLevelBuilder> moduleLevelBuilders = service.createModuleLevelBuilders(myTasksExecutor);
      for (ModuleLevelBuilder builder : moduleLevelBuilders) {
        myModuleLevelBuilders.get(builder.getCategory()).add(builder);
      }
    }
  }

  public int getModuleLevelBuilderCount() {
    int count = 0;
    for (BuilderCategory category : BuilderCategory.values()) {
      count += getBuilders(category).size();
    }
    return count;
  }

  public List<BuildTask> getBeforeTasks(){
    return Collections.emptyList(); // todo
  }

  public List<BuildTask> getAfterTasks(){
    return Collections.emptyList(); // todo
  }

  public List<ModuleLevelBuilder> getBuilders(BuilderCategory category){
    return Collections.unmodifiableList(myModuleLevelBuilders.get(category)); // todo
  }

  public List<ProjectLevelBuilder> getProjectLevelBuilders() {
    return myProjectLevelBuilders;
  }

  public void shutdown() {
    myTasksExecutor.shutdown();
  }

}
