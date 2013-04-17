package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator;
import com.intellij.openapi.externalSystem.service.DisposableExternalSystemService;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.IntegrationKey;
import com.intellij.openapi.project.Project;
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

  private final Set<ExternalProjectStructureChangeListener> myListeners = new ConcurrentHashSet<ExternalProjectStructureChangeListener>();

  private final ConcurrentMap<IntegrationKey, Set<ExternalProjectStructureChange>> myChanges          = ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<IntegrationKey, DataNode<ProjectData>>               myExternalProjects = ContainerUtil.newConcurrentMap();

  private final Collection<ExternalProjectStructureChangesPreProcessor>  myCommonPreProcessors  = ContainerUtilRt.createEmptyCOWList();
  private final Collection<ExternalProjectStructureChangesPostProcessor> myCommonPostProcessors = ContainerUtilRt.createEmptyCOWList();

  private final ConcurrentMap<ProjectSystemId, Set<ExternalProjectStructureChangesPostProcessor>> mySpecificPreProcessors  =
    ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<ProjectSystemId, Set<ExternalProjectStructureChangesPostProcessor>> mySpecificPostProcessors =
    ContainerUtil.newConcurrentMap();

  // TODO den uncomment
  //@NotNull private final ExternalProjectStructureChangesCalculator<ProjectData, Project> myChangesCalculator;
  @NotNull private final PlatformFacade                                                  myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper                                   myLibraryPathTypeMapper;

  // TODO den uncomment
  public ProjectStructureChangesModel(/*@NotNull ExternalProjectStructureChangesCalculator<ProjectData, Project> changesCalculator,*/
                                      @NotNull PlatformFacade platformFacade,
                                      @NotNull ExternalLibraryPathTypeMapper mapper
                                      /*@NotNull AutoImporter autoImporter,
                                      @NotNull MovedJarsPostProcessor movedJarsPostProcessor,
                                      @NotNull OutdatedLibraryVersionPostProcessor changedLibraryVersionPostProcessor*/)
  {
    // TODO den uncomment
    //myChangesCalculator = changesCalculator;
    myPlatformFacade = platformFacade;
    myLibraryPathTypeMapper = mapper;
    // TODO den uncomment
    //myCommonPostProcessors.add(autoImporter);
    //myCommonPostProcessors.add(movedJarsPostProcessor);
    //myCommonPostProcessors.add(changedLibraryVersionPostProcessor);
  }

  /**
   * Asks the model to update its state according to the given state of the target external project.
   * <p/>
   * I.e. basically the processing looks as following:
   * <ol>
   *  <li>This method is called;</li>
   *  <li>
   *    The model process given project state within the {@link #getChanges(ProjectSystemId, Project) registered changes} and calculates
   *    resulting difference between external system and ide projects;
   *  </li>
   *  <li>
   *    {@link #addListener(ExternalProjectStructureChangeListener) Registered listeners} are notified
   *    if any new change is detected;
   *  </li>
   * </ol>
   * <p/>
   * <b>Note:</b> the listeners are notified <b>after</b> the actual state change, i.e. {@link #getChanges(ProjectSystemId, Project)}
   * during the update returns up-to-date data.
   *
   * @param externalProject              external project to sync with
   * @param ideProject                   target ide project
   * @param onIdeProjectStructureChange  a flag which identifies if current update is triggered by ide project structure
   *                                     change (an alternative is a manual project structure changes refresh implied by a user)
   */
  public void update(@NotNull DataNode<ProjectData> externalProject, @NotNull Project ideProject, boolean onIdeProjectStructureChange) {
    DataNode<ProjectData> externalProjectToUse = externalProject;
    // TODO den uncomment
//    for (ExternalProjectStructureChangesPreProcessor preProcessor : myCommonPreProcessors) {
//      externalProjectToUse = preProcessor.preProcess(externalProjectToUse, ideProject);
//    }
    ProjectSystemId externalSystemId = externalProject.getData().getOwner();
    IntegrationKey key = new IntegrationKey(ideProject, externalSystemId);
    myExternalProjects.putIfAbsent(key, externalProjectToUse);
    final ExternalProjectChangesCalculationContext context = getCurrentChangesContext(
      externalProjectToUse, ideProject, onIdeProjectStructureChange
    );
    if (!context.hasNewChanges()) {
      return;
    }
    myChanges.put(key, context.getCurrentChanges());
    for (ExternalProjectStructureChangeListener listener : myListeners) {
      listener.onChanges(ideProject, externalSystemId, context.getKnownChanges(), context.getCurrentChanges());
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
  public DataNode<ProjectData> getExternalProject(@NotNull ProjectSystemId id, @NotNull Project ideProject) {
    DataNode<ProjectData> result = myExternalProjects.get(new IntegrationKey(ideProject, id));
    if (result == null) {
      return null;
    }
    return result;
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
   * @param listener  listener to register
   * @return          <code>true</code> if given listener was not registered before;
   *                  <code>false</code> otherwise
   */
  public boolean addListener(@NotNull ExternalProjectStructureChangeListener listener) {
    return myListeners.add(listener);
  }

  @NotNull
  public ExternalProjectChangesCalculationContext getCurrentChangesContext(@NotNull DataNode<ProjectData> externalProject,
                                                                           @NotNull Project ideProject,
                                                                           boolean onIdeProjectStructureChange)
  {
    ProjectSystemId owner = externalProject.getData().getOwner();
    IntegrationKey key = new IntegrationKey(ideProject, owner);
    Set<ExternalProjectStructureChange> knownChanges = myChanges.get(key);
    if (knownChanges == null) {
      knownChanges = Collections.emptySet();
    }
    ExternalProjectChangesCalculationContext context
      = new ExternalProjectChangesCalculationContext(knownChanges, myPlatformFacade, myLibraryPathTypeMapper);
    // TODO den uncomment
//    myChangesCalculator.calculate(externalProject, ideProject, context);
    for (ExternalProjectStructureChangesPostProcessor processor : myCommonPostProcessors) {
      processor.processChanges(context.getCurrentChanges(), owner, ideProject, onIdeProjectStructureChange);
    }
    Set<ExternalProjectStructureChangesPostProcessor> postProcessors = mySpecificPostProcessors.get(owner);
    if (postProcessors != null)
      for (ExternalProjectStructureChangesPostProcessor processor : myCommonPostProcessors) {
        processor.processChanges(context.getCurrentChanges(), owner, ideProject, onIdeProjectStructureChange);
      }
    return context;
  }

  /**
   * @param id  target external system id
   * @return collection of project structure changes between the external system with the given id and
   *            given ide project registered within the current model
   */
  @NotNull
  public Set<ExternalProjectStructureChange> getChanges(@NotNull ProjectSystemId id, @NotNull Project ideProject) {
    Set<ExternalProjectStructureChange> result = myChanges.get(new IntegrationKey(ideProject, id));
    return result == null ? Collections.<ExternalProjectStructureChange>emptySet() : result;
  }

  @Override
  public void onExternalSystemUnlinked(@NotNull ProjectSystemId externalSystemId, @NotNull Project ideProject) {
    IntegrationKey key = new IntegrationKey(ideProject, externalSystemId);
    myChanges.remove(key);
    myExternalProjects.remove(key);
  }
}
