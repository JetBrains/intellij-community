package org.jetbrains.jps.incremental;

import org.jetbrains.jps.idea.OwnServiceLoader;
import org.jetbrains.jps.incremental.groovy.GroovyBuilder;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;

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
        myTasksExecutor.shutdownNow();
      }
    });

    final OwnServiceLoader<ProjectLevelBuilderService> loader = OwnServiceLoader.load(ProjectLevelBuilderService.class);

    for (ProjectLevelBuilderService service : loader) {
      myProjectLevelBuilders.add(service.createBuilder());
    }
    // todo: some builder registration mechanism for plugins needed

    myModuleLevelBuilders.get(BuilderCategory.TRANSLATOR).add(new GroovyBuilder(true));
    myModuleLevelBuilders.get(BuilderCategory.TRANSLATOR).add(new JavaBuilder(myTasksExecutor));
    myModuleLevelBuilders.get(BuilderCategory.TRANSLATOR).add(new ResourcesBuilder());
    myModuleLevelBuilders.get(BuilderCategory.TRANSLATOR).add(new GroovyBuilder(false));

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
    myTasksExecutor.shutdownNow();
  }

}
