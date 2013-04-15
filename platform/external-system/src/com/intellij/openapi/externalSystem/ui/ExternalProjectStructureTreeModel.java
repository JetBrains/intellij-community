package com.intellij.openapi.externalSystem.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.change.*;
import com.intellij.openapi.externalSystem.model.project.id.*;
import com.intellij.openapi.externalSystem.service.project.*;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectStructureChangeListener;
import com.intellij.openapi.externalSystem.service.project.change.ProjectStructureChangesModel;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import com.intellij.openapi.externalSystem.model.project.change.ContentRootPresenceChange;
import com.intellij.openapi.externalSystem.model.project.change.DependencyExportedChange;
import com.intellij.openapi.externalSystem.model.project.change.DependencyScopeChange;
import com.intellij.openapi.externalSystem.model.project.change.LibraryDependencyPresenceChange;
import com.intellij.openapi.externalSystem.model.project.change.ModuleDependencyPresenceChange;
import com.intellij.openapi.externalSystem.model.project.change.OutdatedLibraryVersionChange;
import com.intellij.openapi.externalSystem.model.project.change.JarPresenceChange;
import com.intellij.openapi.externalSystem.model.project.change.ModulePresenceChange;
import com.intellij.openapi.externalSystem.model.project.change.LanguageLevelChange;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Model for the target project structure tree used by the gradle integration.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/30/12 4:20 PM
 */
public class ExternalProjectStructureTreeModel extends DefaultTreeModel {

  private static final Function<ProjectEntityId, ProjectEntityId> SELF_MAPPER = new Function.Self<ProjectEntityId, ProjectEntityId>();

  /**
   * <pre>
   *     ...
   *      |_module     &lt;- module's name is a key
   *          |_...
   *          |_dependencies   &lt;- dependencies holder node is a value
   *                  |_dependency1
   *                  |_dependency2
   * </pre>
   */
  private final Map<String, ProjectStructureNode<GradleSyntheticId>> myModuleDependencies
    = new HashMap<String, ProjectStructureNode<GradleSyntheticId>>();
  private final Map<String, ProjectStructureNode<ModuleId>>          myModules
    = new HashMap<String, ProjectStructureNode<ModuleId>>();

  private final Set<ExternalProjectStructureNodeFilter> myFilters                   = new HashSet<ExternalProjectStructureNodeFilter>();
  private final TreeNode[]                              myNodeHolder                = new TreeNode[1];
  private final int[]                                   myIndexHolder               = new int[1];
  private final NodeListener                            myNodeListener              = new NodeListener();
  private final ObsoleteChangesDispatcher               myObsoleteChangesDispatcher = new ObsoleteChangesDispatcher();
  private final NewChangesDispatcher                    myNewChangesDispatcher      = new NewChangesDispatcher();

  @NotNull private final Project                             myProject;
  @NotNull private final ProjectSystemId                     myExternalSystemId;
  @NotNull private final PlatformFacade                      myPlatformFacade;
  @NotNull private final ProjectStructureHelper              myProjectStructureHelper;
  @NotNull private final Comparator<ProjectStructureNode<?>> myNodeComparator;
  @NotNull private final ProjectStructureChangesModel        myChangesModel;

  @Nullable private Comparator<ExternalProjectStructureChange> myChangesComparator;

  private boolean myProcessChangesAtTheSameThread;

  @SuppressWarnings("UnusedDeclaration") // Used implicitly by IoC
  public ExternalProjectStructureTreeModel(@NotNull ProjectSystemId externalSystemId,
                                           @NotNull Project project,
                                           @NotNull ProjectStructureServices context)
  {
    this(externalSystemId, project, context, true);
  }

  ExternalProjectStructureTreeModel(@NotNull ProjectSystemId externalSystemId,
                                    @NotNull Project project,
                                    @NotNull ProjectStructureServices context,
                                    boolean rebuild)
  {
    super(null);
    myExternalSystemId = externalSystemId;
    myProject = project;
    myPlatformFacade = context.getPlatformFacade();
    myProjectStructureHelper = context.getProjectStructureHelper();
    myChangesModel = context.getChangesModel();
    myNodeComparator = new ExternalProjectStructureNodeComparator(context, project);

    context.getChangesModel().addListener(new ExternalProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull Project ideProject,
                            @NotNull ProjectSystemId externalSystemId,
                            @NotNull Collection<ExternalProjectStructureChange> oldChanges,
                            @NotNull Collection<ExternalProjectStructureChange> currentChanges) {
        if (externalSystemId.equals(myExternalSystemId)) {
          processChanges(oldChanges, currentChanges);
        }
      }
    });

    if (rebuild) {
      rebuild();
    }
  }

  private void processChanges(@NotNull final Collection<ExternalProjectStructureChange> oldChanges,
                              @NotNull final Collection<ExternalProjectStructureChange> currentChanges)
  {
    final Runnable task = new Runnable() {
      @Override
      public void run() {
        Collection<ExternalProjectStructureChange> obsoleteChangesToUse = ContainerUtil.subtract(oldChanges, currentChanges);
        Collection<ExternalProjectStructureChange> currentChangesToUse = ContainerUtil.subtract(currentChanges, oldChanges);
        if (myChangesComparator != null) {
          obsoleteChangesToUse = sort(obsoleteChangesToUse, myChangesComparator);
          currentChangesToUse = sort(currentChangesToUse, myChangesComparator);
        }
        processObsoleteChanges(obsoleteChangesToUse);
        processCurrentChanges(currentChangesToUse);
      }
    };
    if (myProcessChangesAtTheSameThread) {
      task.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(task);
    }
  }

  @NotNull
  private static Collection<ExternalProjectStructureChange> sort(@NotNull Collection<ExternalProjectStructureChange> changes,
                                                                 @NotNull Comparator<ExternalProjectStructureChange> myChangesComparator)
  {
    List<ExternalProjectStructureChange> toSort = ContainerUtilRt.newArrayList(changes);
    Collections.sort(toSort, myChangesComparator);
    return toSort;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ProjectStructureNode<ProjectId> getRoot() {
    return (ProjectStructureNode<ProjectId>)super.getRoot();
  }

  public void rebuild() {
    rebuild(false);
  }

  public void rebuild(boolean onIdeProjectStructureChange) {
    myModuleDependencies.clear();
    myModules.clear();

    ProjectId projectId = EntityIdMapper.mapEntityToId(getProject());
    ProjectStructureNode<ProjectId> root = buildNode(projectId, getProject().getName());
    setRoot(root);
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
    final List<ProjectStructureNode<?>> dependencies = ContainerUtilRt.newArrayList();
    final List<Pair<ProjectStructureNode<LibraryDependencyId>, Library>> libraryDependencies = ContainerUtilRt.newArrayList();
    RootPolicy<Object> visitor = new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        ModuleDependencyId id = EntityIdMapper.mapEntityToId(moduleOrderEntry);
        dependencies.add(buildNode(id, moduleOrderEntry.getModuleName()));
        return value;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        if (libraryOrderEntry.getLibraryName() == null) {
          return value;
        }
        LibraryDependencyId id = EntityIdMapper.mapEntityToId(libraryOrderEntry);
        ProjectStructureNode<LibraryDependencyId> dependencyNode = buildNode(id, id.getDependencyName());
        Library library = libraryOrderEntry.getLibrary();
        if (library != null) {
          libraryDependencies.add(Pair.create(dependencyNode, library));
          dependencies.add(dependencyNode);
        }
        return value;
      }
    };
    for (Module module : modules) {
      dependencies.clear();
      libraryDependencies.clear();
      final ModuleId moduleId = EntityIdMapper.mapEntityToId(module);
      final ProjectStructureNode<ModuleId> moduleNode = buildNode(moduleId, moduleId.getModuleName());
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      root.add(moduleNode);
      
      // Content roots
      final Collection<ModuleAwareContentRoot> contentRoots = myPlatformFacade.getContentRoots(module);
      for (ContentEntry entry : contentRoots) {
        ContentRootId contentRootId = EntityIdMapper.mapEntityToId(entry);
        moduleNode.add(buildContentRootNode(contentRootId, contentRoots.size() <= 1));
      }
      
      // Dependencies
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        orderEntry.accept(visitor, null);
      }
      if (dependencies.isEmpty()) {
        continue;
      }
      ProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(moduleId);
      for (ProjectStructureNode<?> dependency : dependencies) {
        dependenciesNode.add(dependency);
      }
      
      // The general idea is to add jar nodes when all tree nodes above have already been initialized.
      if (!libraryDependencies.isEmpty()) {
        for (Pair<ProjectStructureNode<LibraryDependencyId>, Library> p : libraryDependencies) {
          populateLibraryDependencyNode(p.first, p.second);
        }
      }
    }

    // TODO den implement
//    ProjectData externalProject = myChangesModel.getExternalProject(myExternalSystemId, myProject);
//    if (externalProject != null) {
//      ExternalProjectChangesCalculationContext context
//        = myChangesModel.getCurrentChangesContext(externalProject, myProject, onIdeProjectStructureChange);
//      processChanges(Collections.<ExternalProjectStructureChange>emptyList(),
//                     ContainerUtil.union(context.getKnownChanges(), context.getCurrentChanges()));
//      filterNodes(root);
//    }
  }

  private void populateLibraryDependencyNode(@NotNull ProjectStructureNode<LibraryDependencyId> node,
                                             @Nullable Library library)
  {
    if (library == null) {
      return;
    }

    LibraryId libraryId = node.getDescriptor().getElement().getLibraryId();
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      JarId jarId = new JarId(ExternalSystemUtil.getLocalFileSystemPath(file), LibraryPathType.BINARY, libraryId);
      ProjectStructureNode<JarId> jarNode = buildNode(jarId, ExternalSystemUtil.extractNameFromPath(jarId.getPath()));
      jarNode.getDescriptor().setToolTip(jarId.getPath());
      node.add(jarNode);
    }
  }

  @TestOnly
  public void setProcessChangesAtTheSameThread(boolean processChangesAtTheSameThread) {
    myProcessChangesAtTheSameThread = processChangesAtTheSameThread;
  }

  @TestOnly
  public void setChangesComparator(@Nullable Comparator<ExternalProjectStructureChange> changesComparator) {
    myChangesComparator = changesComparator;
  }

  private void filterNodes(@NotNull ProjectStructureNode<?> root) {
    if (myFilters.isEmpty()) {
      return;
    }
    Deque<ProjectStructureNode<?>> toRemove = new ArrayDeque<ProjectStructureNode<?>>();
    Stack<ProjectStructureNode<?>> toProcess = new Stack<ProjectStructureNode<?>>();
    toProcess.push(root);
    while (!toProcess.isEmpty()) {
      final ProjectStructureNode<?> current = toProcess.pop();
      toRemove.add(current);
      if (passFilters(current)) {
        toRemove.remove(current);
        // Keep all nodes up to the hierarchy.
        for (ProjectStructureNode<?> parent = current.getParent(); parent != null; parent = parent.getParent()) {
          if (!toRemove.remove(parent)) {
            break;
          }
        }
      }
      for (ProjectStructureNode<?> child : current) {
        toProcess.push(child);
      }
    }
    for (ProjectStructureNode<?> node = toRemove.pollLast(); node != null; node = toRemove.pollLast()) {
      final ProjectStructureNode<?> parent = node.getParent();
      if (parent == null) {
        continue;
      }
      parent.remove(node);
      
      // Clear caches.
      final ProjectEntityId id = node.getDescriptor().getElement();
      if (id instanceof ModuleId) {
        String moduleName = ((ModuleId)id).getModuleName();
        myModules.remove(moduleName);
        myModuleDependencies.remove(moduleName);
      }
    }
  }

  public void addFilter(@NotNull ExternalProjectStructureNodeFilter filter) {
    myFilters.add(filter);
    rebuild();
  }

  public boolean hasFilter(@NotNull ExternalProjectStructureNodeFilter filter) {
    return myFilters.contains(filter);
  }

  public boolean hasAnyFilter() {
    return !myFilters.isEmpty();
  }
  
  public void removeFilter(@NotNull ExternalProjectStructureNodeFilter filter) {
    myFilters.remove(filter);
    rebuild();
  }

  public void removeAllFilters() {
    myFilters.clear();
    rebuild();
  }

  /**
   * @param node    node to check
   * @return        <code>true</code> if active filters allow to show given node; <code>false</code> otherwise
   */
  private boolean passFilters(@NotNull ProjectStructureNode<?> node) {
    if (myFilters.isEmpty()) {
      return true;
    }
    for (ExternalProjectStructureNodeFilter filter : myFilters) {
      if (filter.isVisible(node)) {
        return true;
      }
    }
    return false;
  }
  
  @NotNull
  private static String getContentRootNodeName(@NotNull ContentRootId id, boolean singleRoot) {
    final String name = ExternalSystemBundle.message("entity.type.content.root");
    if (singleRoot) {
      return name;
    }
    final String path = id.getRootPath();
    final int i = path.lastIndexOf('/');
    if (i < 0) {
      return name;
    }
    return name + ":" + path.substring(i + 1);
  }
  
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  private ProjectStructureNode<ContentRootId> buildContentRootNode(@NotNull ContentRootId id) {
    return null;
    // TODO den implement
//    final boolean singleRoot;
//    if (myExternalSystemId.equals(id.getOwner())) {
//      final ModuleData module = myProjectStructureHelper.findExternalModule(id.getModuleName(), myExternalSystemId, myProject);
//      singleRoot = module == null || module.getContentRoots().size() <= 1;
//    }
//    else {
//      final Module module = myProjectStructureHelper.findIdeModule(id.getModuleName(), myProject);
//      singleRoot = module == null || myPlatformFacade.getContentRoots(module).size() <= 1;
//    }
//    return buildContentRootNode(id, singleRoot);
  }
  
  @NotNull
  private ProjectStructureNode<ContentRootId> buildContentRootNode(@NotNull ContentRootId id, boolean singleRoot) {
    ProjectStructureNode<ContentRootId> result = buildNode(id, getContentRootNodeName(id, singleRoot));
    result.getDescriptor().setToolTip(id.getRootPath());
    return result;
  }

  @NotNull
  private <T extends ProjectEntityId> ProjectStructureNode<T> buildNode(@NotNull T id, @NotNull String name) {
    final ProjectStructureNode<T> result = new ProjectStructureNode<T>(ExternalSystemUiUtil.buildDescriptor(id, name), myNodeComparator);
    result.addListener(myNodeListener);
    return result;
  }

  @NotNull
  private ProjectStructureNode<GradleSyntheticId> getDependenciesNode(@NotNull ModuleId id) {
    final ProjectStructureNode<GradleSyntheticId> cached = myModuleDependencies.get(id.getModuleName());
    if (cached != null) {
      return cached;
    }
    ProjectStructureNode<ModuleId> moduleNode = getModuleNode(id);
    ProjectStructureNode<GradleSyntheticId> result
      = new ProjectStructureNode<GradleSyntheticId>(ExternalSystemUiUtil.DEPENDENCIES_NODE_DESCRIPTOR, myNodeComparator);
    result.addListener(myNodeListener);
    moduleNode.add(result);
    myModuleDependencies.put(id.getModuleName(), result);
    
    return result;
  }
  
  @NotNull
  private ProjectStructureNode<ModuleId> getModuleNode(@NotNull ModuleId id) {
    ProjectStructureNode<ModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      moduleNode = buildNode(id, id.getModuleName());
      myModules.put(id.getModuleName(), moduleNode);
      ((ProjectStructureNode<?>)root).add(moduleNode);
    }
    return moduleNode;
  }

  /**
   * Notifies current model that particular module roots change just has happened.
   * <p/>
   * The model is expected to update itself if necessary.
   */
  public void onModuleRootsChange() {
    for (ProjectStructureNode<GradleSyntheticId> node : myModuleDependencies.values()) {
      node.sortChildren();
    }
  }

  private void removeModuleNodeIfEmpty(@NotNull ProjectStructureNode<ModuleId> node) {
    if (node.getChildCount() != 0) {
      return;
    }
    node.removeFromParent();
    myModules.remove(node.getDescriptor().getElement().getModuleName());
  }

  private void removeModuleDependencyNodeIfEmpty(@NotNull ProjectStructureNode<GradleSyntheticId> node,
                                                 @NotNull ModuleId moduleId)
  {
    if (node.getChildCount() != 0) {
      return;
    }
    node.removeFromParent();
    myModuleDependencies.remove(moduleId.getModuleName());
  }
  
  /**
   * Asks current model to update its state in accordance with the given changes.
   * 
   * @param changes  collections that contains all changes between the current gradle and intellij project structures
   */
  public void processCurrentChanges(@NotNull Collection<ExternalProjectStructureChange> changes) {
    for (ExternalProjectStructureChange change : changes) {
      change.invite(myNewChangesDispatcher);
    }
  }

  /**
   * Asks current model to process given changes assuming that they are obsolete.
   * <p/>
   * Example:
   * <pre>
   * <ol>
   *   <li>There is a particular intellij-local library (change from the gradle project structure);</li>
   *   <li>Corresponding node is shown at the current UI;</li>
   *   <li>The library is removed, i.e. corresponding change has become obsolete;</li>
   *   <li>This method is notified within the obsolete change and is expected to remove the corresponding node;</li>
   * </ol>
   * </pre>
   */
  public void processObsoleteChanges(Collection<ExternalProjectStructureChange> changes) {
    for (ExternalProjectStructureChange change : changes) {
      change.invite(myObsoleteChangesDispatcher);
    }
  }

  private void processNewProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    getRoot().addConflictChange(change);
  }

  private void processNewLanguageLevelChange(@NotNull LanguageLevelChange change) {
    getRoot().addConflictChange(change);
  }

  private void processNewDependencyScopeChange(@NotNull DependencyScopeChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, false);
  }

  private void processNewDependencyExportedChange(@NotNull DependencyExportedChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, false);
  }
  
  private void processNewLibraryDependencyPresenceChange(@NotNull LibraryDependencyPresenceChange change) {
    ProjectStructureNode<LibraryDependencyId> dependencyNode = processNewDependencyPresenceChange(change);
    LibraryDependencyId id = change.getExternalEntity();
    if (dependencyNode == null || id == null) {
      return;
    }
    LibraryData gradleLibrary = null;
    // TODO den implement
//    LibraryData gradleLibrary = myProjectStructureHelper.findExternalLibrary(id.getLibraryId(), myExternalSystemId, myProject);
    if (gradleLibrary == null) {
      return;
    }

    Map<JarId, ProjectStructureNode<JarId>> existingJarNodes = ContainerUtilRt.newHashMap();
    for (ProjectStructureNode<JarId> jarNode : dependencyNode.getChildren(JarId.class)) {
      existingJarNodes.put(jarNode.getDescriptor().getElement(), jarNode);
    }

    Map<JarId, ProjectStructureNode<JarId>> gradleJarIds = ContainerUtilRt.newHashMap();
    LibraryId libraryId = dependencyNode.getDescriptor().getElement().getLibraryId();
    for (String path : gradleLibrary.getPaths(LibraryPathType.BINARY)) {
      JarId jarId = new JarId(path, LibraryPathType.BINARY, libraryId);
      ProjectStructureNode<JarId> jarNode = existingJarNodes.get(jarId);
      if (jarNode == null) {
        jarNode = buildNode(jarId, ExternalSystemUtil.extractNameFromPath(jarId.getPath()));
        jarNode.setAttributes(ExternalSystemTextAttributes.NO_CHANGE);
        jarNode.getDescriptor().setToolTip(jarId.getPath());
        dependencyNode.add(jarNode);
      }
      gradleJarIds.put(jarId, jarNode);
    }

    Library intellijLibrary = myProjectStructureHelper.findIdeLibrary(gradleLibrary, myProject);
    if (intellijLibrary == null) {
      for (ProjectStructureNode<?> jarNode : dependencyNode) {
        jarNode.setAttributes(ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE);
      }
    }
    else {
      Set<JarId> intellijJarIds = ContainerUtilRt.newHashSet();
      for (VirtualFile jarFile : intellijLibrary.getFiles(OrderRootType.CLASSES)) {
        JarId jarId = new JarId(ExternalSystemUtil.getLocalFileSystemPath(jarFile), LibraryPathType.BINARY, libraryId);
        if (gradleJarIds.remove(jarId) == null) {
          intellijJarIds.add(jarId);
        }
      }
      for (ProjectStructureNode<JarId> jarNode : gradleJarIds.values()) {
        jarNode.setAttributes(ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE);
      }
      for (JarId jarId : intellijJarIds) {
        ProjectStructureNode<JarId> jarNode = buildNode(jarId, ExternalSystemUtil.extractNameFromPath(jarId.getPath()));
        jarNode.setAttributes(ExternalSystemTextAttributes.IDE_LOCAL_CHANGE);
        jarNode.getDescriptor().setToolTip(jarId.getPath());
        dependencyNode.add(jarNode);
      }
    }
  }

  private void processJarPresenceChange(@NotNull JarPresenceChange change, boolean obsolete) {
    JarId jarId = change.getExternalEntity();
    TextAttributesKey attributes = ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE;
    if (jarId == null) {
      jarId = change.getIdeEntity();
      attributes = ExternalSystemTextAttributes.IDE_LOCAL_CHANGE;
      assert jarId != null;
    }
    if (jarId.getLibraryPathType() != LibraryPathType.BINARY) {
      // Ignore non-binary paths (docs, sources) as they are not shown at the JetGradle tool window's project structure view.
      return;
    }
    if (obsolete) {
      attributes = ExternalSystemTextAttributes.NO_CHANGE;
    }

    for (Map.Entry<String, ProjectStructureNode<GradleSyntheticId>> entry : myModuleDependencies.entrySet()) {
      Collection<ProjectStructureNode<LibraryDependencyId>> libraryDependencies
        = entry.getValue().getChildren(LibraryDependencyId.class);

      insideModule:
      for (ProjectStructureNode<LibraryDependencyId> libraryDependencyNode : libraryDependencies) {
        if (!libraryDependencyNode.getDescriptor().getElement().getLibraryId().equals(jarId.getLibraryId())) {
          continue;
        }

        for (ProjectStructureNode<JarId> jarNode : libraryDependencyNode.getChildren(JarId.class)) {
          if (jarNode.getDescriptor().getElement().equals(jarId)) {
            if (obsolete && myProjectStructureHelper.findIdeJar(jarId, myProject) == null) {
              // It was a gradle-local change which is now obsolete. Remove the jar node then.
              jarNode.removeFromParent();
            }
            else {
              jarNode.setAttributes(attributes);
            }
            break insideModule;
          }
        }

        if (obsolete) {
          continue;
        }

        // There is a possible case that both gradle and intellij have a library with the same name but different jar sets.
        // We don't want to show intellij-local jars for the gradle-local module which uses that library then.
        if (jarId.getOwner() ==  ProjectSystemId.IDE
            && myModules.get(entry.getKey()).getDescriptor().getAttributes() == ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE)
        {
          continue;
        }

          // When control flow reaches this place that means that this is a new jar attached to a library. Hence, we need to add a node.
        ProjectStructureNode<JarId> newNode = buildNode(jarId, ExternalSystemUtil.extractNameFromPath(jarId.getPath()));
        newNode.setAttributes(attributes);
        newNode.getDescriptor().setToolTip(jarId.getPath());
        if (passFilters(newNode)) {
          libraryDependencyNode.add(newNode);
        }
      }
    }
  }
  
  private void processNewChangedLibraryVersionChange(@NotNull OutdatedLibraryVersionChange change) {
    for (Module module : myPlatformFacade.getModules(myProject)) {
      String moduleName = module.getName();
      LibraryOrderEntry dependency =
        myProjectStructureHelper.findIdeLibraryDependency(moduleName, change.getIdeLibraryId().getLibraryName(), myProject);
      if (dependency == null) {
        continue;
      }
      ProjectStructureNode<GradleSyntheticId> dependenciesNode =
        getDependenciesNode(new ModuleId(ProjectSystemId.IDE, moduleName));

      // Remove local dependency nodes (if any).
      Collection<ProjectStructureNode<LibraryDependencyId>> dependencyNodes =
        dependenciesNode.getChildren(LibraryDependencyId.class);
      for (ProjectStructureNode<LibraryDependencyId> dependencyNode : dependencyNodes) {
        LibraryDependencyId id = dependencyNode.getDescriptor().getElement();
        if (id.getLibraryId().equals(change.getLibraryId()) || id.getLibraryId().equals(change.getIdeLibraryId())) {
          dependenciesNode.remove(dependencyNode);
        }
      }

      // Add 'changed version' nodes.
      CompositeLibraryDependencyId libraryDependencyId = new CompositeLibraryDependencyId(
        new LibraryDependencyId(myExternalSystemId, moduleName, change.getLibraryId().getLibraryName()),
        new LibraryDependencyId(ProjectSystemId.IDE, moduleName, change.getIdeLibraryId().getLibraryName())
      );
      String libraryDependencyNodeName = ExternalSystemUtil.getOutdatedEntityName(
        change.getBaseLibraryName(),
        change.getExternalLibraryVersion(),
        change.getIdeLibraryVersion()
      );
      ProjectStructureNode<CompositeLibraryDependencyId> libraryDependencyNode =
        buildNode(libraryDependencyId, libraryDependencyNodeName);
      libraryDependencyNode.setAttributes(ExternalSystemTextAttributes.OUTDATED_ENTITY);
      if (passFilters(libraryDependencyNode)) {
        dependenciesNode.add(libraryDependencyNode);
      }
    }
  }
  
  private void processNewModuleDependencyPresenceChange(@NotNull ModuleDependencyPresenceChange change) {
    processNewDependencyPresenceChange(change);
  }
  
  @SuppressWarnings("unchecked")
  @Nullable
  private <I extends AbstractExternalDependencyId> ProjectStructureNode<I> processNewDependencyPresenceChange(
    @NotNull AbstractProjectEntityPresenceChange<I> change)
  {
    I id = change.getExternalEntity();
    TextAttributesKey attributes = ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIdeEntity();
      attributes = ExternalSystemTextAttributes.IDE_LOCAL_CHANGE;
    }
    assert id != null;
    final ProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(id.getOwnerModuleId());
    Class<I> clazz = (Class<I>)id.getClass();
    for (ProjectStructureNode<I> node : dependenciesNode.getChildren(clazz)) {
      if (id.equals(node.getDescriptor().getElement())) {
        node.setAttributes(attributes);
        return node;
      }
    }
    ProjectStructureNode<I> newNode = buildNode(id, id.getDependencyName());
    dependenciesNode.add(newNode);
    newNode.setAttributes(attributes);

    if (passFilters(newNode)) {
      return newNode;
    }

    newNode.removeFromParent();
    removeModuleDependencyNodeIfEmpty(dependenciesNode, id.getOwnerModuleId());
    removeModuleNodeIfEmpty(getModuleNode(id.getOwnerModuleId()));
    return null;
  }

  private void processNewModulePresenceChange(@NotNull ModulePresenceChange change) {
    final ModuleId id;
    final TextAttributesKey key;
    if (change.getExternalEntity() == null) {
      id = change.getIdeEntity();
      key = ExternalSystemTextAttributes.IDE_LOCAL_CHANGE;
    }
    else {
      id = change.getExternalEntity();
      key = ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE;
    }
    assert id != null;
    final ProjectStructureNode<ModuleId> moduleNode = getModuleNode(id);
    moduleNode.setAttributes(key);
    
    if (!passFilters(moduleNode)) {
      removeModuleNodeIfEmpty(moduleNode);
    }
  }

  private void processNewContentRootPresenceChange(@NotNull ContentRootPresenceChange change) {
    ContentRootId id = change.getExternalEntity();
    TextAttributesKey key = ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIdeEntity();
      key = ExternalSystemTextAttributes.IDE_LOCAL_CHANGE;
    }
    assert id != null;
    final ProjectStructureNode<ModuleId> moduleNode = getModuleNode(id.getModuleId());
    for (ProjectStructureNode<ContentRootId> contentRoot : moduleNode.getChildren(ContentRootId.class)) {
      if (id.equals(contentRoot.getDescriptor().getElement())) {
        contentRoot.setAttributes(key);
        return;
      }
    }
    ProjectStructureNode<ContentRootId> contentRootNode = buildContentRootNode(id);
    moduleNode.add(contentRootNode);
    contentRootNode.setAttributes(key);

    if (!passFilters(contentRootNode)) {
      contentRootNode.removeFromParent();
      removeModuleNodeIfEmpty(moduleNode);
    }
  }
  
  private void processObsoleteProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    getRoot().removeConflictChange(change);
  }
  
  private void processObsoleteLanguageLevelChange(@NotNull LanguageLevelChange change) {
    getRoot().removeConflictChange(change);
  }
  
  private void processObsoleteDependencyScopeChange(@NotNull DependencyScopeChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, true);
  }

  private void processObsoleteDependencyExportedChange(@NotNull DependencyExportedChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, true);
  }
  
  private void processObsoleteLibraryDependencyPresenceChange(@NotNull LibraryDependencyPresenceChange change) {
    // We need to remove the corresponding node then.
    LibraryDependencyId id = change.getExternalEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIdeEntity();
      assert id != null;
      removeNode = !myProjectStructureHelper.isIdeLibraryDependencyExist(id, myProject);
    }
    else {
      removeNode = !myProjectStructureHelper.isExternalLibraryDependencyExist(id, myExternalSystemId, myProject);
    }
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteChangedLibraryVersionChange(@NotNull OutdatedLibraryVersionChange change) {
    Library library = myProjectStructureHelper.findIdeLibraryByBaseName(change.getBaseLibraryName(), myProject);
    if (library == null) {
      return;
    }
    for (Map.Entry<String, ProjectStructureNode<GradleSyntheticId>> entry : myModuleDependencies.entrySet()) {
      String moduleName = entry.getKey();
      CompositeLibraryDependencyId outdatedEntityId = new CompositeLibraryDependencyId(
        new LibraryDependencyId(myExternalSystemId, moduleName, change.getLibraryId().getLibraryName()),
        new LibraryDependencyId(ProjectSystemId.IDE, moduleName, change.getIdeLibraryId().getLibraryName())
      );
      ProjectStructureNode<GradleSyntheticId> dependenciesNode = entry.getValue();
      Collection<ProjectStructureNode<CompositeLibraryDependencyId>> dependencyNodes =
        dependenciesNode.getChildren(CompositeLibraryDependencyId.class);
      for (ProjectStructureNode<CompositeLibraryDependencyId> oldDependencyNode : dependencyNodes) {
        if (!outdatedEntityId.equals(oldDependencyNode.getDescriptor().getElement())) {
          continue;
        }
        dependenciesNode.remove(oldDependencyNode);
        LibraryOrderEntry libraryDependency =
          myProjectStructureHelper.findIdeLibraryDependency(moduleName, ExternalSystemUtil.getLibraryName(library), myProject);
        if (libraryDependency != null) {
          LibraryDependencyId newDependencyId = EntityIdMapper.mapEntityToId(libraryDependency);
          ProjectStructureNode<LibraryDependencyId> newDependencyNode =
            buildNode(newDependencyId, newDependencyId.getDependencyName());
          populateLibraryDependencyNode(newDependencyNode, libraryDependency.getLibrary());
          dependenciesNode.add(newDependencyNode);
        }
      }
    }
  }
  
  private void processObsoleteModuleDependencyPresenceChange(@NotNull ModuleDependencyPresenceChange change) {
    ModuleDependencyId id = change.getExternalEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIdeEntity();
      assert id != null;
      removeNode = !myProjectStructureHelper.isIdeModuleDependencyExist(id, myProject);
    }
    else {
      removeNode = !myProjectStructureHelper.isExternalModuleDependencyExist(id, myProject);
    }
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteDependencyPresenceChange(@NotNull AbstractExternalDependencyId id, boolean removeNode) {
    final ProjectStructureNode<GradleSyntheticId> holder = myModuleDependencies.get(id.getOwnerModuleName());
    if (holder == null) {
      return;
    }

    // There are two possible cases why 'local library dependency' change is obsolete:
    //   1. Corresponding dependency has been added at the counterparty;
    //   2. The 'local dependency' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.

    for (ProjectStructureNode<? extends AbstractExternalDependencyId> node : holder.getChildren(id.getClass())) {
      ProjectStructureNodeDescriptor<? extends AbstractExternalDependencyId> descriptor = node.getDescriptor();
      if (!id.equals(descriptor.getElement())) {
        continue;
      }
      if (removeNode) {
        holder.remove(node);
      }
      else {
        descriptor.setAttributes(ExternalSystemTextAttributes.NO_CHANGE);
        holder.correctChildPositionIfNecessary(node);
        if (!passFilters(node)) {
          node.removeFromParent();
          removeModuleDependencyNodeIfEmpty(holder, id.getOwnerModuleId());
          removeModuleNodeIfEmpty(myModules.get(id.getOwnerModuleName()));
        }
      }
      return;
    }
  }

  private void processObsoleteModulePresenceChange(@NotNull ModulePresenceChange change) {
    ModuleId id = change.getExternalEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIdeEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIdeModule(id.getModuleName(), myProject) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findExternalModule(id.getModuleName(), myExternalSystemId, myProject) == null;
    }
    

    // There are two possible cases why 'module presence' change is obsolete:
    //   1. Corresponding module has been added at the counterparty;
    //   2. The 'local module' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.
    
    final ProjectStructureNode<ModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    if (removeNode) {
      moduleNode.removeFromParent();
    }
    else {
      moduleNode.setAttributes(ExternalSystemTextAttributes.NO_CHANGE);
      if (!passFilters(moduleNode)) {
        removeModuleNodeIfEmpty(moduleNode);
      }
    }
  }

  private void processObsoleteContentRootPresenceChange(@NotNull ContentRootPresenceChange change) {
    ContentRootId id = change.getExternalEntity();
    final boolean removeNode;
    if (id == null) {
      id = change.getIdeEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIdeContentRoot(id, myProject) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findExternalContentRoot(id, myExternalSystemId, myProject) == null;
    }
    final ProjectStructureNode<ModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    for (ProjectStructureNode<ContentRootId> contentRootNode : moduleNode.getChildren(ContentRootId.class)) {
      if (!id.equals(contentRootNode.getDescriptor().getElement())) {
        continue;
      }
      if (removeNode) {
        contentRootNode.removeFromParent();
      }
      else {
        contentRootNode.setAttributes(ExternalSystemTextAttributes.NO_CHANGE);
        if (!passFilters(contentRootNode)) {
          contentRootNode.removeFromParent();
          removeModuleNodeIfEmpty(moduleNode);
        }
      }
      return;
    }
  }

  private void processDependencyConflictChange(@NotNull AbstractConflictingPropertyChange<?> change,
                                               @NotNull Function<ProjectEntityId, ProjectEntityId> nodeIdMapper,
                                               boolean obsolete)
  {
    for (Map.Entry<String, ProjectStructureNode<GradleSyntheticId>> entry : myModuleDependencies.entrySet()) {
      for (ProjectStructureNode<?> dependencyNode : entry.getValue()) {
        if (!change.getEntityId().equals(nodeIdMapper.fun(dependencyNode.getDescriptor().getElement()))) {
          continue;
        }
        if (obsolete) {
          dependencyNode.removeConflictChange(change);
        }
        else {
          dependencyNode.addConflictChange(change);
        }
        if (!passFilters(dependencyNode)) {
          dependencyNode.removeFromParent();
          final ProjectStructureNode<ModuleId> moduleNode = myModules.get(entry.getKey());
          final ModuleId moduleId = moduleNode.getDescriptor().getElement();
          removeModuleDependencyNodeIfEmpty(entry.getValue(), moduleId);
          removeModuleNodeIfEmpty(moduleNode);
        }
        break;
      }
    }
  }

  private class NodeListener implements ProjectStructureNode.Listener {
    
    @Override
    public void onNodeAdded(@NotNull ProjectStructureNode<?> node, int index) {
      myIndexHolder[0] = index;
      nodesWereInserted(node.getParent(), myIndexHolder);
    }

    @Override
    public void onNodeRemoved(@NotNull ProjectStructureNode<?> parent,
                              @NotNull ProjectStructureNode<?> removedChild,
                              int removedChildIndex)
    {
      myIndexHolder[0] = removedChildIndex;
      myNodeHolder[0] = removedChild;
      nodesWereRemoved(parent, myIndexHolder, myNodeHolder); 
    }

    @Override
    public void onNodeChanged(@NotNull ProjectStructureNode<?> node) {
      nodeChanged(node);
    }

    @Override
    public void onNodeChildrenChanged(@NotNull ProjectStructureNode<?> parent, int[] childIndices) {
      nodesChanged(parent, childIndices);
    }
  }
  
  private class NewChangesDispatcher implements ExternalProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processNewProjectRenameChange(change); }
    @Override public void visit(@NotNull LanguageLevelChange change) { processNewLanguageLevelChange(change); }
    @Override public void visit(@NotNull ModulePresenceChange change) { processNewModulePresenceChange(change); }
    @Override public void visit(@NotNull ContentRootPresenceChange change) { processNewContentRootPresenceChange(change); }
    @Override public void visit(@NotNull LibraryDependencyPresenceChange change) { processNewLibraryDependencyPresenceChange(change); }
    @Override public void visit(@NotNull JarPresenceChange change) { processJarPresenceChange(change, false); }
    @Override public void visit(@NotNull OutdatedLibraryVersionChange change) { processNewChangedLibraryVersionChange(change); }
    @Override public void visit(@NotNull ModuleDependencyPresenceChange change) { processNewModuleDependencyPresenceChange(change); }
    @Override public void visit(@NotNull DependencyScopeChange change) { processNewDependencyScopeChange(change); }
    @Override public void visit(@NotNull DependencyExportedChange change) { processNewDependencyExportedChange(change);
    }
  }
  
  private class ObsoleteChangesDispatcher implements ExternalProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processObsoleteProjectRenameChange(change); }
    @Override public void visit(@NotNull LanguageLevelChange change) { processObsoleteLanguageLevelChange(change); }
    @Override public void visit(@NotNull ModulePresenceChange change) { processObsoleteModulePresenceChange(change); }
    @Override public void visit(@NotNull ContentRootPresenceChange change) { processObsoleteContentRootPresenceChange(change); }
    @Override public void visit(@NotNull LibraryDependencyPresenceChange change) {
      processObsoleteLibraryDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull JarPresenceChange change) { processJarPresenceChange(change, true); }
    @Override public void visit(@NotNull OutdatedLibraryVersionChange change) { processObsoleteChangedLibraryVersionChange(change); }

    @Override public void visit(@NotNull ModuleDependencyPresenceChange change) {
      processObsoleteModuleDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull DependencyScopeChange change) { processObsoleteDependencyScopeChange(change); }
    @Override public void visit(@NotNull DependencyExportedChange change) { processObsoleteDependencyExportedChange(change); }
  }
}
