// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.classpath;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.WeightBasedComparator;
import com.intellij.util.CommonProcessors;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public abstract class ChooseLibrariesDialogBase extends DialogWrapper {
  private final SimpleTree myTree = new SimpleTree();
  private AbstractTreeBuilder myBuilder;
  private List<Library> myResult;
  private final Map<Object, Object> myParentsMap = new HashMap<>();

  protected ChooseLibrariesDialogBase(final JComponent parentComponent, final @NlsContexts.DialogTitle String title) {
    super(parentComponent, false);
    setTitle(title);
  }

  protected ChooseLibrariesDialogBase(Project project, @NlsContexts.DialogTitle String title) {
    super(project, false);
    setTitle(title);
  }

  @Override
  protected void init() {
    super.init();
    updateOKAction();
  }

  private static @Nls String notEmpty(@Nls String nodeText) {
    return StringUtil.isNotEmpty(nodeText) ? nodeText : JavaUiBundle.message("unnamed.title");
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.util.ui.classpath.ChooseLibrariesDialog";
  }

  protected int getLibraryTableWeight(@NotNull LibraryTable libraryTable) {
    return 0;
  }

  protected boolean isAutoExpandLibraryTable(@NotNull LibraryTable libraryTable) {
    return false;
  }

  @Override
  protected void doOKAction() {
    processSelection(new CommonProcessors.CollectProcessor<>(myResult = new ArrayList<>()));
    super.doOKAction();
  }

  private void updateOKAction() {
    setOKActionEnabled(!processSelection(new CommonProcessors.FindFirstProcessor<>()));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @NotNull
  public List<Library> getSelectedLibraries() {
    return myResult == null ? Collections.emptyList() : myResult;
  }

  protected void queueUpdateAndSelect(@NotNull final Library library) {
    myBuilder.queueUpdate().doWhenDone(() -> myBuilder.select(library));
  }

  private boolean processSelection(final Processor<? super Library> processor) {
    for (Object element : myBuilder.getSelectedElements()) {
      if (element instanceof Library) {
        if (!processor.process((Library)element)) return false;
      }
    }
    return true;
  }

  protected boolean acceptsElement(final Object element) {
    return true;
  }

  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    final TreeExpander expander = new DefaultTreeExpander(myTree);
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    group.add(actionsManager.createExpandAllAction(expander, myTree));
    group.add(actionsManager.createCollapseAllAction(expander, myTree));
    final JComponent component = ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, group, true).getComponent();
    component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.darkGray), component.getBorder()));
    return component;
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    myBuilder = new SimpleTreeBuilder(myTree, new DefaultTreeModel(new DefaultMutableTreeNode()),
                                        new MyStructure(getProject()),
                                        WeightBasedComparator.FULL_INSTANCE) {
      // unique class to simplify search through the logs
    };
    myBuilder.initRootNode();

    myTree.setDragEnabled(false);

    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(false);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        updateOKAction();
      }
    });
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (isOKActionEnabled()) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    myTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER");
    myTree.getActionMap().put("ENTER", getOKAction());
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setPreferredSize(JBUI.size(500, 400));
    return pane;
  }

  @NotNull
  protected Project getProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }

  protected LibrariesTreeNodeBase<Library> createLibraryDescriptor(NodeDescriptor parentDescriptor, Library library) {
    return new LibraryDescriptor(getProject(), parentDescriptor, library);
  }

  protected void collectChildren(Object element, final List<Object> result) {
    if (element instanceof Application) {
      Collections.addAll(result, ProjectManager.getInstance().getOpenProjects());
      final LibraryTablesRegistrar instance = LibraryTablesRegistrar.getInstance();
      result.add(instance.getLibraryTable()); //1
      result.addAll(instance.getCustomLibraryTables()); //2
    }
    else if (element instanceof Project) {
      Collections.addAll(result, ModuleManager.getInstance((Project)element).getModules());
      result.add(LibraryTablesRegistrar.getInstance().getLibraryTable((Project)element));
    }
    else if (element instanceof LibraryTable) {
      Collections.addAll(result, ((LibraryTable)element).getLibraries());
    }
    else if (element instanceof Module) {
      for (OrderEntry entry : ModuleRootManager.getInstance((Module)element).getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
          if (LibraryTableImplUtil.MODULE_LEVEL.equals(libraryOrderEntry.getLibraryLevel())) {
            final Library library = libraryOrderEntry.getLibrary();
            result.add(library);
          }
        }
      }
    }
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myBuilder);
    super.dispose();
  }

  protected static class LibrariesTreeNodeBase<T> extends SimpleNode {
    private final T myElement;

    protected LibrariesTreeNodeBase(Project project, NodeDescriptor parentDescriptor, T element) {
      super(project, parentDescriptor);
      myElement = element;
    }

    @Override
    public T getElement() {
      return myElement;
    }

    @Override
    public SimpleNode @NotNull [] getChildren() {
      return NO_CHILDREN;
    }

    @Override
    public int getWeight() {
      return 0;
    }

    @Override
    public Object @NotNull [] getEqualityObjects() {
      return new Object[] {myElement};
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      //todo[nik] this is workaround for bug in getTemplatePresentation().setIcons()
      presentation.setIcon(getTemplatePresentation().getIcon(false));
    }
  }

  private static class RootDescriptor extends LibrariesTreeNodeBase<Object> {
    protected RootDescriptor(final Project project) {
      super(project, null, ApplicationManager.getApplication());
    }
  }

  private static class ProjectDescriptor extends LibrariesTreeNodeBase<Project> {
    protected ProjectDescriptor(final Project project, final Project element) {
      super(project, null, element);
      getTemplatePresentation().setIcon(PlatformIcons.PROJECT_ICON);
      getTemplatePresentation().addText(notEmpty(getElement().getName()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static class ModuleDescriptor extends LibrariesTreeNodeBase<Module> {
    protected ModuleDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Module element) {
      super(project, parentDescriptor, element);
      final PresentationData templatePresentation = getTemplatePresentation();
      templatePresentation.setIcon(ModuleType.get(element).getIcon());
      templatePresentation.addText(notEmpty(element.getName()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    @Override
    public int getWeight() {
      return 1;
    }
  }

  private static class LibraryDescriptor extends LibrariesTreeNodeBase<Library> {
    protected LibraryDescriptor(final Project project, final NodeDescriptor parentDescriptor, final Library element) {
      super(project, parentDescriptor, element);
      final CellAppearanceEx appearance = OrderEntryAppearanceService.getInstance().forLibrary(project, element, false);
      final SimpleColoredComponent coloredComponent = new SimpleColoredComponent();
      appearance.customize(coloredComponent);
      final PresentationData templatePresentation = getTemplatePresentation();
      templatePresentation.setIcon(coloredComponent.getIcon());
      templatePresentation.addText(notEmpty(appearance.getText()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static class LibraryTableDescriptor extends LibrariesTreeNodeBase<LibraryTable> {
    private final int myWeight;
    private final boolean myAutoExpand;

    protected LibraryTableDescriptor(final Project project,
                                     final NodeDescriptor parentDescriptor,
                                     final LibraryTable table,
                                     final int weight,
                                     boolean autoExpand) {
      super(project, parentDescriptor, table);
      myWeight = weight;
      myAutoExpand = autoExpand;
      getTemplatePresentation().setIcon(PlatformIcons.LIBRARY_ICON);
      final String nodeText = table.getPresentation().getDisplayName(true);
      getTemplatePresentation().addText(notEmpty(nodeText), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    @Override
    public boolean isAutoExpandNode() {
      return myAutoExpand;
    }

    @Override
    public int getWeight() {
      return myWeight;
    }
  }

  public boolean isEmpty() {
    List<Object> children = new ArrayList<>();
    final AbstractTreeStructure structure = myBuilder.getTreeStructure();
    if (structure != null) collectChildren(structure.getRootElement(), children);
    return children.isEmpty();
  }

  private class MyStructure extends AbstractTreeStructure {
    private final Project myProject;

    MyStructure(Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public Object getRootElement() {
      return ApplicationManager.getApplication();
    }

    @Override
    public Object @NotNull [] getChildElements(@NotNull Object element) {
      final List<Object> result = new ArrayList<>();
      collectChildren(element, result);
      final Iterator<Object> it = result.iterator();
      while (it.hasNext()) {
        if (!acceptsElement(it.next())) it.remove();
      }
      for (Object o : result) {
        myParentsMap.put(o, element);
      }
      return result.toArray();
    }

    @Override
    public Object getParentElement(@NotNull Object element) {
      if (element instanceof Application) return null;
      if (element instanceof Project) return ApplicationManager.getApplication();
      if (element instanceof Module) return ((Module)element).getProject();
      if (element instanceof LibraryTable) return myParentsMap.get(element);
      if (element instanceof Library) return myParentsMap.get(element);
      throw new AssertionError();
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
      if (element instanceof Application) return new RootDescriptor(myProject);
      if (element instanceof Project) return new ProjectDescriptor(myProject, (Project)element);
      if (element instanceof Module) return new ModuleDescriptor(myProject, parentDescriptor, (Module)element);
      if (element instanceof LibraryTable) {
        final LibraryTable libraryTable = (LibraryTable)element;
        return new LibraryTableDescriptor(myProject, parentDescriptor, libraryTable,
                                          getLibraryTableWeight(libraryTable),
                                          isAutoExpandLibraryTable(libraryTable));
      }
      if (element instanceof Library) return createLibraryDescriptor(parentDescriptor, (Library)element);
      throw new AssertionError();
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }
}
