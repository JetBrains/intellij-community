package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalProject;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.service.DisposableExternalSystemService;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages information about the changes between the gradle and intellij project structures. 
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 7:01 PM
 */
public class ProjectStructureChangesModel implements DisposableExternalSystemService {

  private final ConcurrentMap<ProjectSystemId, Set<ExternalProjectStructureChangeListener>> myListeners =
    ContainerUtil.newConcurrentMap();

  private final ConcurrentMap<Pair<ProjectSystemId, String /* ide project id */>, Set<ExternalProjectStructureChange>> myChanges =
    ContainerUtil.newConcurrentMap();

  private final ConcurrentMap<Pair<ProjectSystemId, String /* ide project id */>, ExternalProject> myExternalProjects =
    ContainerUtil.newConcurrentMap();

  private final Collection<ExternalProjectStructureChangesPreProcessor>  myCommonPreProcessors  = ContainerUtilRt.createEmptyCOWList();
  private final Collection<ExternalProjectStructureChangesPostProcessor> myCommonPostProcessors = ContainerUtilRt.createEmptyCOWList();

  private final ConcurrentMap<ProjectSystemId, Set<ExternalProjectStructureChangesPostProcessor>> mySpecificPreProcessors  =
    ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<ProjectSystemId, Set<ExternalProjectStructureChangesPostProcessor>> mySpecificPostProcessors =
    ContainerUtil.newConcurrentMap();

  @NotNull private final ExternalProjectStructureChangesCalculator<ExternalProject, Project> myChangesCalculator;
  @NotNull private final PlatformFacade                                                      myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper                                       myLibraryPathTypeMapper;

  public ProjectStructureChangesModel(@NotNull ExternalProjectStructureChangesCalculator<ExternalProject, Project> changesCalculator,
                                      @NotNull PlatformFacade platformFacade,
                                      @NotNull ExternalLibraryPathTypeMapper mapper,
                                      @NotNull AutoImporter autoImporter,
                                      @NotNull MovedJarsPostProcessor movedJarsPostProcessor,
                                      @NotNull OutdatedLibraryVersionPostProcessor changedLibraryVersionPostProcessor)
  {
    myChangesCalculator = changesCalculator;
    myPlatformFacade = platformFacade;
    myLibraryPathTypeMapper = mapper;
    myCommonPostProcessors.add(autoImporter);
    myCommonPostProcessors.add(movedJarsPostProcessor);
    myCommonPostProcessors.add(changedLibraryVersionPostProcessor);
  }

  /**
   * Asks the model to update its state according to the given state of the target external project.
   * <p/>
   * I.e. basically the processing looks as following:
   * <ol>
   *  <li>This method is called;</li>
   *  <li>
   *    The model process given project state within the {@link #getChanges() registered changes} and calculates resulting difference
   *    between external system and ide projects;
   *  </li>
   *  <li>
   *    {@link #addListener(ProjectSystemId, ExternalProjectStructureChangeListener)}  Registered listeners} are notified
   *    if any new change is detected;
   *  </li>
   * </ol>
   * <p/>
   * <b>Note:</b> the listeners are notified <b>after</b> the actual state change, i.e. {@link #getChanges()} during the update
   * returns up-to-date data.
   *
   * @param externalProject              external project to sync with
   * @param ideProject                   target ide project
   * @param onIdeProjectStructureChange  a flag which identifies if current update is triggered by ide project structure
   *                                     change (an alternative is a manual project structure changes refresh implied by a user)
   */
  public void update(@NotNull ExternalProject externalProject, @NotNull Project ideProject, boolean onIdeProjectStructureChange) {
    ExternalProject externalProjectToUse = externalProject;
    for (ExternalProjectStructureChangesPreProcessor preProcessor : myCommonPreProcessors) {
      externalProjectToUse = preProcessor.preProcess(externalProjectToUse, ideProject);
    }
    ProjectSystemId externalSystemId = externalProject.getSystemId();
    Pair<ProjectSystemId, String> key = key(externalSystemId, ideProject);
    myExternalProjects.putIfAbsent(key, externalProjectToUse);
    final ExternalProjectChangesCalculationContext context = getCurrentChangesContext(
      externalProjectToUse, ideProject, onIdeProjectStructureChange
    );
    if (!context.hasNewChanges()) {
      return;
    }
    myChanges.put(key, context.getCurrentChanges());
    Set<ExternalProjectStructureChangeListener> listeners = myListeners.get(externalSystemId);
    if (listeners != null) {
      for (ExternalProjectStructureChangeListener listener : listeners) {
        listener.onChanges(ideProject, externalSystemId, context.getKnownChanges(), context.getCurrentChanges());
      }
    }
  }

  public void addPreProcessor(@NotNull ProjectSystemId externalSystemId,
                              @NotNull ExternalProjectStructureChangesPostProcessor postProcessor)
  {
    add(externalSystemId, postProcessor, mySpecificPreProcessors);
  }
  
  public void addPostProcessor(@NotNull ProjectSystemId externalSystemId,
                               @NotNull ExternalProjectStructureChangesPostProcessor postProcessor)
  {
    add(externalSystemId, postProcessor, mySpecificPostProcessors);
  }

  private static <T> void add(@NotNull ProjectSystemId externalSystemId,
                              @NotNull T data,
                              @NotNull ConcurrentMap<ProjectSystemId, Set<T>> holder)
  {
    Set<T> set = holder.get(externalSystemId);
    while (set == null) {
      holder.putIfAbsent(externalSystemId, new ConcurrentHashSet<T>());
      set = holder.get(externalSystemId);
    }
    set.add(data);
  }
  
  /**
   * @param id          target external system id
   * @param ideProject  target ide project which might be linked to an external project of the given type
   * @return            last known external project state (if any) of the given type linked to the given ide project 
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends ExternalProject> T getExternalProject(@NotNull ProjectSystemId id, @NotNull Project ideProject) {
    ExternalProject result = myExternalProjects.get(key(id, ideProject));
    if (result == null) {
      return null;
    }
    return (T)result;
  }

  @NotNull
  @TestOnly
  public Collection<ExternalProjectStructureChangesPreProcessor> getCommonPreProcessors() {
    return myCommonPreProcessors;
  }

  @NotNull
  @TestOnly
  public Collection<ExternalProjectStructureChangesPostProcessor> getCommonPostProcessors() {
    return myCommonPostProcessors;
  }

  /**
   * Registers given listener within the current model.
   * 
   * @param id        id of the target external system which project changes should be delivered to the given listener
   * @param listener  listener to register
   * @return          <code>true</code> if given listener was not registered before;
   *                  <code>false</code> otherwise
   */
  public boolean addListener(@NotNull ProjectSystemId id, @NotNull ExternalProjectStructureChangeListener listener) {
    Set<ExternalProjectStructureChangeListener> listeners = myListeners.get(id);
    while (listeners == null) {
      myListeners.putIfAbsent(id, new ConcurrentHashSet<ExternalProjectStructureChangeListener>());
      listeners = myListeners.get(id);
    }
    return listeners.add(listener);
  }

  @NotNull
  public ExternalProjectChangesCalculationContext getCurrentChangesContext(@NotNull ExternalProject externalProject,
                                                                           @NotNull Project ideProject,
                                                                           boolean onIdeProjectStructureChange)
  {
    Pair<ProjectSystemId, String> key = key(externalProject.getSystemId(), ideProject);
    ExternalProjectChangesCalculationContext context
      = new ExternalProjectChangesCalculationContext(myChanges.get(key), myPlatformFacade, myLibraryPathTypeMapper);
    myChangesCalculator.calculate(externalProject, ideProject, context);
    for (ExternalProjectStructureChangesPostProcessor processor : myCommonPostProcessors) {
      processor.processChanges(context.getCurrentChanges(), ideProject, onIdeProjectStructureChange);
    }
    Set<ExternalProjectStructureChangesPostProcessor> postProcessors = mySpecificPostProcessors.get(externalProject.getSystemId());
    if (postProcessors != null)
    for (ExternalProjectStructureChangesPostProcessor processor : myCommonPostProcessors) {
      processor.processChanges(context.getCurrentChanges(), ideProject, onIdeProjectStructureChange);
    }
    return context;
  }
  
  /**
   * @param id  target external system id
   * @return    collection of project structure changes between the external system with the given id and
   *            given ide project registered within the current model
   */
  @NotNull
  public Set<ExternalProjectStructureChange> getChanges(@NotNull ProjectSystemId id, @NotNull Project ideProject) {
    Set<ExternalProjectStructureChange> result = myChanges.get(key(id, ideProject));
    return result == null ? Collections.<ExternalProjectStructureChange>emptySet() : result;
  }

  @Override
  public void onExternalSystemUnlinked(@NotNull ProjectSystemId externalSystemId, @NotNull Project ideProject) {
    Pair<ProjectSystemId, String> key = key(externalSystemId, ideProject);
    myListeners.remove(externalSystemId);
    myChanges.remove(key);
    myExternalProjects.remove(key);
  }

  @NotNull
  private static Pair<ProjectSystemId, String> key(@NotNull ProjectSystemId externalSystemId, @NotNull Project ideProject) {
    return Pair.create(externalSystemId, ideProject.getName() + ideProject.getLocationHash());
  }
}
