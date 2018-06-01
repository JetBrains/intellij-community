// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.problems.ProblemListener;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.search.scope.ProblemsScope;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.stripe.ErrorStripe;
import com.intellij.ui.tree.AbstractTreeWalker;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.tree.ProjectFileNode;
import com.intellij.ui.tree.ProjectFileTreeModel;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.intellij.ide.projectView.impl.ProjectRootsUtil.findSourceFolder;
import static com.intellij.openapi.roots.ui.configuration.SourceRootPresentation.getSourceRootIcon;
import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.openapi.vfs.VfsUtilCore.VFS_SEPARATOR_CHAR;
import static com.intellij.openapi.vfs.VfsUtilCore.getRelativePath;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static java.util.Collections.emptyList;

public final class ScopeViewTreeModel extends BaseTreeModel<AbstractTreeNode> implements InvokerSupplier {
  private static final Logger LOG = Logger.getInstance(ScopeViewTreeModel.class);
  private volatile Comparator<NodeDescriptor> comparator;
  private final ProjectFileTreeModel model;
  private final ProjectNode root;

  public ScopeViewTreeModel(@NotNull Project project, @NotNull ViewSettings settings) {
    model = new ProjectFileTreeModel(project);
    model.addTreeModelListener(new TreeModelAdapter() {
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        if (type == EventType.StructureChanged) {
          TreePath path = event.getTreePath();
          if (path == null || null == path.getParentPath()) {
            invalidate(null);
          }
          else {
            Object component = path.getLastPathComponent();
            if (component instanceof ProjectFileNode) {
              ProjectFileNode node = (ProjectFileNode)component;
              notifyStructureChanged(node.getVirtualFile());
            }
          }
        }
      }
    });
    Disposer.register(this, model);
    root = new ProjectNode(project, settings);
    project.getMessageBus().connect(this).subscribe(ProblemListener.TOPIC, new ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        problemsDisappeared(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        NamedScopeFilter filter = getFilter();
        if (filter != null && filter.getScope() instanceof ProblemsScope) {
          model.setFilter(filter); // update a problem scope from root
        }
        else {
          notifyPresentationChanged(file);
        }
      }
    });
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusChanged(@NotNull VirtualFile file) {
        notifyPresentationChanged(file);
      }

      @Override
      public void fileStatusesChanged() {
        invalidate(null); // TODO: visit all loaded nodes
      }
    }, this);
  }

  public void setStructureProvider(TreeStructureProvider provider) {
    model.onValidThread(() -> {
      if (root.provider == null && provider == null) return;
      root.provider = provider;
      treeStructureChanged(null, null, null);
    });
  }

  public void setNodeDecorator(ProjectViewNodeDecorator decorator) {
    model.onValidThread(() -> {
      if (root.decorator == null && decorator == null) return;
      root.decorator = decorator;
      treeStructureChanged(null, null, null);
    });
  }

  public void setComparator(Comparator<NodeDescriptor> comparator) {
    model.onValidThread(() -> {
      if (this.comparator == null && comparator == null) return;
      this.comparator = comparator;
      treeStructureChanged(null, null, null);
    });
  }

  public void setFilter(@Nullable NamedScopeFilter filter) {
    root.filter = filter;
    LOG.debug("set filter", filter);
    model.setFilter(filter != null && filter.getScope() instanceof ProjectFilesScope ? null : filter);
  }

  public NamedScopeFilter getFilter() {
    return root.filter;
  }

  @Nullable
  public PsiElement getPsiElement(Object object) {
    if (object instanceof GroupNode) {
      GroupNode node = (GroupNode)object;
      object = node.getSingleRoot();
    }
    if (object instanceof FileNode) {
      FileNode node = (FileNode)object;
      return node.getPsiElement();
    }
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      Object value = node.getValue();
      if (value instanceof PsiElement) return (PsiElement)value;
    }
    return null;
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return model.getInvoker();
  }

  public void invalidate(@Nullable Runnable onDone) {
    model.onValidThread(() -> {
      root.childrenValid = false;
      LOG.debug("whole structure changed");
      treeStructureChanged(null, null, null);
      if (onDone != null) onDone.run();
    });
  }

  private void update(@NotNull AbstractTreeNode node, boolean structure) {
    model.onValidThread(() -> {
      boolean updated = node.update();
      boolean changed = structure || !(node instanceof Node);
      if (!updated && !changed) return;
      TreePath path = TreePathUtil.pathToCustomNode(node, AbstractTreeNode::getParent);
      if (path == null || root != path.getPathComponent(0)) return;
      if (changed) {
        LOG.debug("structure changed: ", node);
        treeStructureChanged(path, null, null);
      }
      else {
        LOG.debug("node updated: ", node);
        treeNodesChanged(path, null, null);
      }
    });
  }

  private void notifyStructureChanged(@NotNull VirtualFile file) {
    boolean flattenPackages = root.getSettings().isFlattenPackages();
    boolean resolveCompactedFolder = !flattenPackages && file.isDirectory() && root.getSettings().isHideEmptyMiddlePackages();
    if (flattenPackages) {
      ProjectFileIndex index = getProjectFileIndex(root.getProject());
      VirtualFile ancestor = index == null ? null : index.getSourceRootForFile(file);
      if (ancestor != null && isAncestor(ancestor, file, true)) {
        // TODO: check that file is located under a source root with packages
        file = ancestor;
      }
    }
    find(file, null, found -> {
      if (found instanceof Node) {
        Node node = (Node)found;
        if (resolveCompactedFolder) {
          AbstractTreeNode parent = node.getParent();
          if (parent instanceof Node) node = (Node)parent;
        }
        if (node.childrenValid) {
          node.childrenValid = false;
          update(node, true);
        }
      }
      else if (found instanceof AbstractTreeNode) {
        update((AbstractTreeNode)found, true);
      }
    });
  }

  private void notifyPresentationChanged(@NotNull VirtualFile file) {
    List<Node> list = new SmartList<>();
    find(file, list, found -> {
      list.forEach(node -> update(node, false));
      if (found instanceof AbstractTreeNode) {
        update((AbstractTreeNode)found, false);
      }
    });
  }

  private void find(@NotNull VirtualFile file, @Nullable List<Node> list, @NotNull Consumer<Object> consumer) {
    model.onValidThread(() -> {
      Module module = getModule(file, root.getProject());
      if (module != null) {
        TreeVisitor visitor = new TreeVisitor.ByComponent<VirtualFile, AbstractTreeNode>(file, AbstractTreeNode.class) {
          @Override
          protected boolean matches(@NotNull AbstractTreeNode pathComponent, @NotNull VirtualFile thisComponent) {
            if (pathComponent.canRepresent(thisComponent)) return true;
            if (pathComponent instanceof Node) return false;
            ProjectViewNode node = pathComponent instanceof ProjectViewNode ? (ProjectViewNode)pathComponent : null;
            return node != null && node.contains(thisComponent);
          }

          @Override
          protected boolean contains(@NotNull AbstractTreeNode pathComponent, @NotNull VirtualFile thisComponent) {
            Node node = pathComponent instanceof Node ? (Node)pathComponent : null;
            if (node == null || !node.contains(thisComponent, module)) return false;
            if (list != null) list.add(node);
            return true;
          }
        };
        AbstractTreeWalker<AbstractTreeNode> walker = new AbstractTreeWalker<AbstractTreeNode>(visitor) {
          @Override
          protected Collection<AbstractTreeNode> getChildren(@NotNull AbstractTreeNode pathComponent) {
            Node node = pathComponent instanceof Node ? (Node)pathComponent : null;
            return node != null && node.childrenValid ? node.children : emptyList();
          }
        };
        walker.start(root);
        walker.promise().onProcessed(path -> consumer.consume(path == null ? null : path.getLastPathComponent()));
      }
    });
  }

  @Override
  public Object getRoot() {
    if (!model.isValidThread()) return null;
    root.update();
    return root;
  }

  @Override
  public boolean isLeaf(Object object) {
    return root != object && super.isLeaf(object);
  }

  @Override
  public int getChildCount(Object object) {
    if (object instanceof AbstractTreeNode && model.isValidThread()) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      return node.getChildren().size();
    }
    return 0;
  }

  @NotNull
  @Override
  public List<AbstractTreeNode> getChildren(Object object) {
    if (object instanceof AbstractTreeNode && model.isValidThread()) {
      AbstractTreeNode parent = (AbstractTreeNode)object;
      Collection<?> children = parent.getChildren();
      if (!children.isEmpty()) {
        List<AbstractTreeNode> result = new SmartList<>();
        children.forEach(child -> {
          if (child instanceof AbstractTreeNode) {
            AbstractTreeNode node = (AbstractTreeNode)child;
            node.setParent(parent);
            node.update();
            result.add(node);
          }
        });
        Comparator<NodeDescriptor> comparator = this.comparator;
        if (comparator != null) result.sort(comparator);
        return result;
      }
    }
    return emptyList();
  }

  @Nullable
  ErrorStripe getStripe(Object object, boolean expanded) {
    if (expanded && object instanceof Node) return null;
    if (object instanceof PresentableNodeDescriptor) {
      PresentableNodeDescriptor node = (PresentableNodeDescriptor)object;
      PresentationData presentation = node.getPresentation();
      TextAttributesKey key = presentation.getTextAttributesKey();
      if (key != null) {
        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
        if (attributes != null && EffectType.WAVE_UNDERSCORE == attributes.getEffectType()) {
          return ErrorStripe.create(attributes.getEffectColor(), 1);
        }
      }
    }
    return null;
  }


  private static abstract class Node extends ProjectViewNode<Object> {
    volatile NamedScopeFilter filter;
    volatile Collection<AbstractTreeNode> children = emptyList();
    volatile boolean childrenValid;

    Node(@NotNull Project project, @NotNull Object value, @NotNull ViewSettings settings) {
      super(project, value, settings);
    }

    Node(@NotNull Node parent, @NotNull Object value) {
      super(parent.getProject(), value, parent.getSettings());
      setParent(parent);
    }

    @Override
    public int getWeight() {
      return 0;
    }

    @Override
    public final boolean canRepresent(Object element) {
      // may be called from unexpected thread
      if (element instanceof PsiFileSystemItem) {
        PsiFileSystemItem item = (PsiFileSystemItem)element;
        element = item.getVirtualFile();
      }
      return element instanceof VirtualFile && canRepresent((VirtualFile)element);
    }

    boolean canRepresent(@NotNull VirtualFile file) {
      // may be called from unexpected thread
      return file.equals(getVirtualFile());
    }

    @Override
    public final boolean contains(@NotNull VirtualFile file) {
      // may be called from unexpected thread
      Module module = getModule(file, getProject());
      return module != null && contains(file, module);
    }

    @Override
    protected boolean hasProblemFileBeneath() {
      WolfTheProblemSolver solver = getWolfTheProblemSolver(getProject());
      return solver == null || solver.hasProblemFilesBeneath(this::contains);
    }

    // may be called from unexpected thread
    abstract boolean contains(@NotNull VirtualFile file, @NotNull Module module);

    @Override
    public Color getFileStatusColor(@NotNull FileStatus status) {
      return status.getColor();
    }

    @NotNull
    abstract Collection<AbstractTreeNode> createChildren(@NotNull Collection<AbstractTreeNode> old);

    @NotNull
    @Override
    public final Collection<AbstractTreeNode> getChildren() {
      if (childrenValid) return children;
      Collection<AbstractTreeNode> oldChildren = children;
      Collection<AbstractTreeNode> newChildren = createChildren(oldChildren);
      oldChildren.forEach(node -> node.setParent(null));
      newChildren.forEach(node -> node.setParent(this));
      children = newChildren;
      childrenValid = true;
      return newChildren;
    }

    @Nullable
    String getLocation() {
      return null;
    }

    final void decorate(@NotNull PresentationData presentation) {
      String location = getLocation();
      if (location != null) {
        if (getSettings().isShowURL()) {
          presentation.setLocationString(location);
        }
        else {
          presentation.setTooltip(location);
        }
      }
      ProjectNode parent = findParent(ProjectNode.class);
      ProjectViewNodeDecorator decorator = parent == null ? null : parent.decorator;
      if (decorator != null) decorator.decorate(this, presentation);
    }

    @SuppressWarnings("SameParameterValue")
    final <N> N findParent(Class<N> type) {
      for (AbstractTreeNode node = this; node != null; node = node.getParent()) {
        if (type.isInstance(node)) return type.cast(node);
      }
      return null;
    }
  }


  private final class ProjectNode extends Node {
    private volatile HashMap<Object, RootNode> roots = new HashMap<>();
    volatile TreeStructureProvider provider;
    volatile ProjectViewNodeDecorator decorator;

    ProjectNode(@NotNull Project project, @NotNull ViewSettings settings) {
      super(project, project, settings);
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(AllIcons.Nodes.Project);
      presentation.setPresentableText(toString());
      decorate(presentation);
    }

    @Nullable
    @Override
    String getLocation() {
      Project project = getProject();
      return project == null || project.isDisposed() ? null : getLocationRelativeToUserHome(project.getPresentableUrl());
    }

    @NotNull
    @Override
    Collection<AbstractTreeNode> createChildren(@NotNull Collection<AbstractTreeNode> old) {
      HashMap<Object, RootNode> oldRoots = roots;
      HashMap<Object, RootNode> newRoots = new HashMap<>();
      Mapper<RootNode, ProjectFileNode> mapper = new Mapper<>(RootNode::new, oldRoots);
      model.getChildren(model.getRoot()).forEach(child -> newRoots.put(child, mapper.apply(this, child)));
      roots = newRoots;
      if (newRoots.isEmpty()) return emptyList();
      ModuleManager manager = getModuleManager(getProject());
      if (manager == null) return emptyList();
      boolean hidden = !getSettings().isShowModules();
      if (!hidden && manager.getModules().length <= 1) hidden = true;
      boolean flatten = hidden || getSettings().isFlattenModules();
      if (!flatten && !manager.hasModuleGroups() && !Registry.is("project.qualified.module.names")) flatten = true;
      return new Group(newRoots.values(), hidden, flatten).createChildren(this, old);
    }

    @NotNull
    Collection<AbstractTreeNode> createChildren(@NotNull Node parent, @NotNull Collection<AbstractTreeNode> old) {
      boolean flattenPackages = getSettings().isFlattenPackages();
      boolean hideEmptyMiddlePackages = getSettings().isHideEmptyMiddlePackages();
      Mapper<FileNode, ProjectFileNode> mapper = new Mapper<>(FileNode::new, FileNode.class, old);
      List<AbstractTreeNode> children = new SmartList<>();
      List<PsiFile> files = new SmartList<>();
      TreeStructureProvider provider = this.provider;
      model.getChildren(parent.getValue()).forEach(child -> {
        PsiElement element = child.getPsiElement();
        if (element instanceof PsiDirectory) {
          Icon icon = getFolderIcon(child, element);
          if (icon != AllIcons.Nodes.Package || !flattenPackages) {
            ProjectFileNode childNext = !hideEmptyMiddlePackages ? null : getSingleDirectory(child);
            while (childNext != null) {
              Icon iconNext = getFolderIcon(childNext, null);
              if (icon == iconNext) {
                child = childNext;
                childNext = getSingleDirectory(child);
              }
              else if (icon == AllIcons.Nodes.Folder && iconNext != AllIcons.Nodes.Package) {
                icon = iconNext;
                child = childNext;
                childNext = null;
              }
              else {
                childNext = null;
              }
            }
            children.add(mapper.apply(parent, child, icon));
          }
          else if (AllIcons.Nodes.Package != parent.getIcon()) {
            visitPackages(child, hideEmptyMiddlePackages,
                          childNext -> children.add(mapper.apply(parent, childNext, AllIcons.Nodes.Package)));
          }
        }
        else if (element instanceof PsiFile) {
          if (provider == null) {
            children.add(mapper.apply(parent, child));
          }
          else {
            files.add((PsiFile)element);
          }
        }
      });
      if (provider == null) return children;
      children.addAll(provider.modify(parent, files.stream()
                                                   .map(file -> new PsiFileNode(getProject(), file, getSettings()))
                                                   .collect(Collectors.toList()), getSettings()));
      return children;
    }

    private void visitPackages(@NotNull ProjectFileNode parent, boolean hideEmptyPackages, @NotNull Consumer<ProjectFileNode> consumer) {
      AtomicBoolean empty = new AtomicBoolean(hideEmptyPackages);
      model.getChildren(parent).forEach(child -> {
        PsiElement element = child.getPsiElement();
        if (element instanceof PsiDirectory) {
          Icon icon = getFolderIcon(child, element);
          if (icon == AllIcons.Nodes.Package) {
            visitPackages(child, hideEmptyPackages, consumer);
          }
          else {
            if (hideEmptyPackages) empty.set(false);
          }
        }
        else if (element instanceof PsiFile) {
          if (hideEmptyPackages) empty.set(false);
        }
      });
      if (!empty.get()) consumer.consume(parent);
    }

    @Nullable
    private ProjectFileNode getSingleDirectory(ProjectFileNode parent) {
      List<ProjectFileNode> children = model.getChildren(parent);
      ProjectFileNode child = children.size() != 1 ? null : children.get(0);
      return child != null && child.getVirtualFile().isDirectory() ? child : null;
    }

    @Override
    boolean contains(@NotNull VirtualFile file, @NotNull Module module) {
      // may be called from unexpected thread
      return roots.values().stream().anyMatch(root -> root.canRepresentOrContain(file, module));
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
      return 1;
    }

    @NotNull
    @Override
    public String toString() {
      Project project = getProject();
      return project == null || project.isDisposed() ? "DISPOSED PROJECT" : project.getName();
    }
  }


  private static class FileNode extends Node implements ProjectFileNode {
    final List<VirtualFile> compacted;
    final ProjectFileNode node;
    volatile String packageName;
    volatile String nodeName;

    FileNode(@NotNull Node parent, @NotNull ProjectFileNode node) {
      super(parent, node);
      this.node = node;
      compacted = getCompactedFolders(parent.getVirtualFile(), node.getVirtualFile());
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
      super.setIcon(icon);
      packageName = icon != AllIcons.Nodes.Package ? null : getPackageName(getPsiElement());
      nodeName = getNodeName(packageName);
    }

    @Override
    protected void update(PresentationData presentation) {
      String title = getTitle();
      presentation.setPresentableText(title != null ? title : toString());
      Icon icon = getIcon();
      if (icon == null && node.isValid()) {
        VirtualFile file = node.getVirtualFile();
        if (file.isDirectory()) {
          icon = getFolderIcon(node, null);
        }
        else {
          icon = file.getFileType().getIcon();
        }
      }
      presentation.setIcon(icon);
      decorate(presentation);
    }

    @NotNull
    @Override
    Collection<AbstractTreeNode> createChildren(@NotNull Collection<AbstractTreeNode> old) {
      ProjectNode parent = findParent(ProjectNode.class);
      if (parent == null) return emptyList();
      return parent.createChildren(this, old);
    }

    @Override
    boolean canRepresent(@NotNull VirtualFile file) {
      // may be called from unexpected thread
      return super.canRepresent(file) || compacted != null && compacted.stream().anyMatch(file::equals);
    }

    @Override
    boolean contains(@NotNull VirtualFile file, @NotNull Module module) {
      // may be called from unexpected thread
      return getModule() == module && isAncestor(getVirtualFile(), file, true);
    }

    @Override
    public FileStatus getFileStatus() {
      FileStatusManager manager = getFileStatusManager(getProject());
      return manager == null ? FileStatus.NOT_CHANGED : manager.getRecursiveStatus(getVirtualFile());
    }

    @NotNull
    @Override
    public Module getModule() {
      return node.getModule();
    }

    @NotNull
    @Override
    public VirtualFile getVirtualFile() {
      return node.getVirtualFile();
    }

    @Override
    public int getWeight() {
      if (getVirtualFile().isDirectory()) {
        ViewSettings settings = getSettings();
        if (settings == null || settings.isFoldersAlwaysOnTop()) return 0;
      }
      return 20;
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
      return getVirtualFile().isDirectory() ? 3 : 5;
    }

    @NotNull
    @Override
    public String toString() {
      return nodeName;
    }

    @NotNull
    private String getNodeName(@Nullable String name) {
      if (name != null) {
        AbstractTreeNode parent = getParent();
        FileNode node = parent instanceof FileNode ? (FileNode)parent : null;
        String prefix = node == null ? null : node.packageName;
        if (prefix == null) return name;
        int length = prefix.length();
        if (length > 0 && name.startsWith(prefix)) {
          if (length < name.length() && '.' == name.charAt(length)) length++;
          if (length < name.length()) return name.substring(length);
        }
        LOG.info("unexpected prefix: " + prefix + " for package: " + name);
      }
      if (compacted != null) {
        StringBuilder sb = new StringBuilder();
        char separator = AllIcons.Nodes.Package == getIcon() ? '.' : VFS_SEPARATOR_CHAR;
        compacted.forEach(file -> sb.append(file.getName()).append(separator));
        return sb.append(getVirtualFile().getName()).toString();
      }
      return getVirtualFile().getName();
    }
  }


  private static final class RootNode extends FileNode implements NavigatableWithText {
    RootNode(@NotNull Node parent, @NotNull ProjectFileNode node) {
      super(parent, node);
    }

    boolean canRepresentOrContain(@NotNull VirtualFile file, @NotNull Module module) {
      // may be called from unexpected thread
      return getModule() == module && isAncestor(getVirtualFile(), file, false);
    }

    @NotNull
    @Override
    public String getTitle() {
      return getLocation(false);
    }

    @NotNull
    @Override
    public String toString() {
      return getLocation(true);
    }

    @NotNull
    private String getLocation(boolean allowEmpty) {
      Project project = getProject();
      VirtualFile dir = project == null || project.isDisposed() ? null : project.getBaseDir();
      String location = dir == null ? null : getRelativePath(getVirtualFile(), dir);
      if (location != null && (allowEmpty || !location.isEmpty())) return location;
      return getLocationRelativeToUserHome(getVirtualFile().getPresentableUrl());
    }

    @Override
    public boolean canNavigate() {
      ProjectSettingsService service = getProjectSettingsService(getModule());
      return service != null && service.canOpenModuleSettings();
    }

    @Override
    public void navigate(boolean requestFocus) {
      ProjectSettingsService service = getProjectSettingsService(getModule());
      if (service != null) service.openModuleSettings(getModule());
    }

    @Override
    public String getNavigateActionText(boolean focusEditor) {
      return ActionsBundle.message("action.ModuleSettings.navigate");
    }
  }


  private static final class GroupNode extends Node implements NavigatableWithText {
    private final String prefix;
    private final String name;
    private Group group;

    GroupNode(@NotNull Node parent, @NotNull Object value) {
      super(parent, value);
      if (value instanceof Module) {
        List<String> list = getModuleNameAsList((Module)value, false);
        int index = list.size() - 1;
        if (index > 0) {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < index; i++) sb.append(list.get(i)).append(VFS_SEPARATOR_CHAR);
          prefix = sb.toString();
          name = list.get(index);
        }
        else {
          prefix = null;
          name = index < 0 ? "UNEXPECTED" : list.get(index);
        }
      }
      else {
        prefix = null;
        name = value.toString();
      }
    }

    void setGroup(@NotNull Group group) {
      this.group = group;
      childrenValid = false;
      setIcon(group.getIcon());
    }

    @Nullable
    RootNode getSingleRoot() {
      Group group = this.group;
      return group == null ? null : group.getSingleRoot();
    }

    @Nullable
    @Override
    public VirtualFile getVirtualFile() {
      RootNode node = getSingleRoot();
      return node == null ? null : node.getVirtualFile();
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(getIcon());
      if (prefix != null) presentation.addText(prefix, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      presentation.addText(name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      decorate(presentation);
    }

    @Nullable
    @Override
    String getLocation() {
      RootNode node = getSingleRoot();
      return node == null ? null : node.getTitle();
    }

    @NotNull
    @Override
    Collection<AbstractTreeNode> createChildren(@NotNull Collection<AbstractTreeNode> old) {
      Group group = this.group;
      if (group == null) return emptyList();
      RootNode node = group.getSingleRoot();
      if (node == null) return group.createChildren(this, old);
      node.setParent(this);
      return node.getChildren();
    }

    @Override
    boolean contains(@NotNull VirtualFile file, @NotNull Module module) {
      // may be called from unexpected thread
      Group group = this.group;
      return group != null && group.contains(file, module);
    }

    @Override
    public boolean canNavigate() {
      Group group = this.group;
      RootNode node = group == null ? null : group.getFirstRoot();
      return node != null && node.canNavigate();
    }

    @Override
    public void navigate(boolean requestFocus) {
      Group group = this.group;
      RootNode node = group == null ? null : group.getFirstRoot();
      if (node != null) node.navigate(requestFocus);
    }

    @Override
    public String getNavigateActionText(boolean focusEditor) {
      return ActionsBundle.message("action.ModuleSettings.navigate");
    }

    @Override
    public int getTypeSortWeight(boolean sortByType) {
      return 2;
    }

    @Override
    public boolean equals(Object object) {
      return this == object;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @NotNull
    @Override
    public String toString() {
      return prefix != null ? prefix + name : name;
    }
  }


  private static final class Group {
    private final Object id;
    private final HashMap<Object, Group> groups = new HashMap<>();
    private final List<RootNode> roots = new SmartList<>();

    private Group(@NotNull String name) {
      id = name;
    }

    private Group(@NotNull Module module) {
      id = module;
    }

    Group(@NotNull Collection<RootNode> nodes, boolean hidden, boolean flatten) {
      id = null;
      if (!nodes.isEmpty()) {
        HashMap<Module, Group> map = new HashMap<>();
        nodes.forEach(node -> {
          Module module = node.getModule();
          Group group = map.get(module);
          if (group == null) {
            group = new Group(module);
            map.put(module, group);
          }
          group.roots.add(node);
        });
        if (hidden) {
          roots.addAll(nodes);
        }
        else if (flatten) {
          groups.putAll(map);
        }
        else {
          map.forEach((module, group) -> {
            List<String> path = getModuleNameAsList(module, Registry.is("project.qualified.module.names"));
            group.roots.forEach(node -> add(node, path, 0));
          });
        }
      }
    }

    private void add(RootNode node, List<String> path, int index) {
      if (index < path.size()) {
        String name = path.get(index);
        Group group = groups.get(name);
        if (group == null) {
          group = new Group(name);
          groups.put(name, group);
        }
        group.add(node, path, index + 1);
      }
      else {
        roots.add(node);
      }
    }

    @NotNull
    Icon getIcon() {
      if (!groups.isEmpty() || roots.isEmpty()) return AllIcons.Nodes.ModuleGroup;
      Module module = roots.get(0).getModule();
      return roots.stream().anyMatch(node -> module != node.getModule())
             ? AllIcons.Nodes.ModuleGroup
             : module.isDisposed()
               ? AllIcons.Nodes.Module
               : ModuleType.get(module).getIcon();
    }

    @Nullable
    RootNode getFirstRoot() {
      if (!roots.isEmpty()) return roots.get(0);
      for (Group group : groups.values()) {
        RootNode root = group.getFirstRoot();
        if (root != null) return root;
      }
      return null;
    }

    @Nullable
    RootNode getSingleRoot() {
      if (!groups.isEmpty() || roots.size() != 1) return null;
      RootNode node = roots.get(0);
      ModuleRootManager manager = getModuleRootManager(node.getModule());
      if (manager == null) return null;
      // ensure that a content root is not a source root or test root
      for (VirtualFile file : manager.getSourceRoots()) {
        if (!isAncestor(node.getVirtualFile(), file, true)) return null;
      }
      return node;
    }

    @Nullable
    private Group getSingleGroup() {
      if (!roots.isEmpty() || groups.size() != 1) return null;
      return groups.values().stream().findFirst().orElse(null);
    }

    @NotNull
    Collection<AbstractTreeNode> createChildren(@NotNull Node parent, @NotNull Collection<AbstractTreeNode> old) {
      Mapper<GroupNode, Object> mapper = new Mapper<>(GroupNode::new, GroupNode.class, old);
      ModuleManager manager = getModuleManager(parent.getProject());
      char separator = manager != null && manager.hasModuleGroups() ? VFS_SEPARATOR_CHAR : '.';
      boolean hideEmptyMiddlePackages = parent.getSettings().isHideEmptyMiddlePackages();
      List<AbstractTreeNode> children = new SmartList<>();
      for (Group group : groups.values()) {
        Object id = group.id;
        Group single = !hideEmptyMiddlePackages ? null : group.getSingleGroup();
        if (single != null) {
          StringBuilder sb = new StringBuilder(id.toString());
          do {
            group = single;
            sb.append(separator).append(group.id);
            single = single.getSingleGroup();
          }
          while (single != null);
          id = sb.toString();
        }
        GroupNode node = mapper.apply(parent, id);
        node.setGroup(group);
        children.add(node);
      }
      children.addAll(roots);
      return children;
    }

    boolean contains(@NotNull VirtualFile file, @NotNull Module module) {
      // may be called from unexpected thread
      return roots.stream().anyMatch(root -> root.canRepresentOrContain(file, module)) ||
             groups.values().stream().anyMatch(group -> group.contains(file, module));
    }
  }


  private static final class Mapper<N extends Node, V> {
    private final HashMap<Object, N> map;
    private final BiFunction<Node, V, N> creator;

    Mapper(@NotNull BiFunction<Node, V, N> creator, @NotNull HashMap<Object, N> map) {
      this.creator = creator;
      this.map = map;
    }

    Mapper(@NotNull BiFunction<Node, V, N> creator, @NotNull Class<N> type, @NotNull Collection<? extends AbstractTreeNode> list) {
      this(creator, new HashMap<>());
      list.forEach(node -> {
        Object id = node.getValue();
        if (id != null && type.isInstance(node)) map.put(id, type.cast(node));
      });
    }

    @NotNull
    N apply(@NotNull Node parent, @NotNull V value) {
      return apply(parent, value, null);
    }

    @NotNull
    N apply(@NotNull Node parent, @NotNull V value, @Nullable Icon icon) {
      N node = map.isEmpty() ? null : map.get(value);
      if (node == null) node = creator.apply(parent, value);
      node.setIcon(icon);
      node.childrenValid = false;
      return node;
    }
  }


  @Nullable
  private static WolfTheProblemSolver getWolfTheProblemSolver(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : WolfTheProblemSolver.getInstance(project);
  }

  @Nullable
  private static FileStatusManager getFileStatusManager(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : FileStatusManager.getInstance(project);
  }

  @Nullable
  private static ModuleManager getModuleManager(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : ModuleManager.getInstance(project);
  }

  @Nullable
  private static ProjectRootManager getProjectRootManager(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : ProjectRootManager.getInstance(project);
  }

  @Nullable
  private static ProjectFileIndex getProjectFileIndex(@Nullable Project project) {
    ProjectRootManager manager = getProjectRootManager(project);
    return manager == null ? null : manager.getFileIndex();
  }

  @Nullable
  private static ModuleRootManager getModuleRootManager(@NotNull Module module) {
    return module.isDisposed() ? null : ModuleRootManager.getInstance(module);
  }

  @Nullable
  private static ProjectSettingsService getProjectSettingsService(@NotNull Module module) {
    Project project = module.isDisposed() ? null : module.getProject();
    return project == null || project.isDisposed() ? null : ProjectSettingsService.getInstance(project);
  }

  @Nullable
  private static Module getModule(@NotNull VirtualFile file, @Nullable Project project) {
    ProjectFileIndex index = getProjectFileIndex(project);
    return index == null ? null : index.getModuleForFile(file);
  }

  @NotNull
  private static Icon getFolderIcon(@NotNull ProjectFileNode node, @Nullable PsiElement element) {
    Module module = node.getModule();
    if (module.isDisposed()) return AllIcons.Nodes.Folder;
    VirtualFile file = node.getVirtualFile();
    SourceFolder folder = findSourceFolder(module, file);
    if (folder != null) return getSourceRootIcon(folder);
    ProjectFileIndex index = getProjectFileIndex(module.getProject());
    if (index == null) return AllIcons.Nodes.Folder;
    if (index.isExcluded(file)) return AllIcons.Modules.ExcludeRoot;
    if (element == null) element = node.getPsiElement();
    if (getPackageName(element) != null) return AllIcons.Nodes.Package;
    return AllIcons.Nodes.Folder;
  }

  private static String getPackageName(@Nullable PsiElement element) {
    if (element instanceof PsiDirectory && element.isValid()) {
      PsiDirectoryFactory factory = PsiDirectoryFactory.getInstance(element.getProject());
      if (factory != null && factory.isPackage((PsiDirectory)element)) {
        String name = factory.getQualifiedName((PsiDirectory)element, false);
        if (factory.isValidPackageName(name)) return name;
      }
    }
    return null;
  }

  @NotNull
  private static List<String> getModuleNameAsList(@NotNull Module module, boolean split) {
    String name = module.getName();
    Project project = module.isDisposed() ? null : module.getProject();
    ModuleManager manager = getModuleManager(project);
    if (manager != null) {
      if (manager.hasModuleGroups()) {
        String[] path = manager.getModuleGroupPath(module);
        if (path != null && path.length != 0) {
          List<String> list = new SmartList<>(path);
          list.add(name);
          return list;
        }
      }
      else if (split) {
        return StringUtil.split(name, ".");
      }
    }
    return new SmartList<>(name);
  }

  @Nullable
  private static List<VirtualFile> getCompactedFolders(@Nullable VirtualFile ancestor, @NotNull VirtualFile file) {
    if (ancestor == null || !isAncestor(ancestor, file, true)) return null;
    ArrayDeque<VirtualFile> deque = new ArrayDeque<>();
    while (true) {
      file = file.getParent();
      if (file == null || !isAncestor(ancestor, file, true)) break;
      deque.addFirst(file);
    }
    return deque.isEmpty() ? null : new SmartList<>(deque);
  }
}
