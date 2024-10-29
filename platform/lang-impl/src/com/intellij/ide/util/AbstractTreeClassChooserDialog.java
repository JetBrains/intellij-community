// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public abstract class AbstractTreeClassChooserDialog<T extends PsiNamedElement> extends DialogWrapper implements TreeChooser<T> {

  private final @NotNull Project myProject;
  private final GlobalSearchScope myScope;
  private final @NotNull Filter<? super T> myClassFilter;
  private final @NotNull Comparator<? super NodeDescriptor<?>> myComparator;
  private final Class<T> myElementClass;
  private final @Nullable T myBaseClass;
  private final boolean myIsShowMembers;
  private final boolean myIsShowLibraryContents;
  private Tree myTree;
  private T mySelectedClass;
  private StructureTreeModel<? extends ProjectAbstractTreeStructureBase> myModel;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private T myInitialClass;

  public AbstractTreeClassChooserDialog(@NlsContexts.DialogTitle String title, Project project, final Class<T> elementClass) {
    this(title, project, elementClass, null);
  }

  public AbstractTreeClassChooserDialog(@NlsContexts.DialogTitle String title, Project project, final Class<T> elementClass, @Nullable T initialClass) {
    this(title, project, GlobalSearchScope.projectScope(project), elementClass, null, initialClass);
  }

  public AbstractTreeClassChooserDialog(@NlsContexts.DialogTitle String title,
                                        @NotNull Project project,
                                        GlobalSearchScope scope,
                                        @NotNull Class<T> elementClass,
                                        @Nullable Filter<? super T> classFilter,
                                        @Nullable T initialClass) {
    this(title, project, scope, elementClass, classFilter, null, null, initialClass, false, true);
  }

  public AbstractTreeClassChooserDialog(@NlsContexts.DialogTitle String title,
                                        @NotNull Project project,
                                        GlobalSearchScope scope,
                                        @NotNull Class<T> elementClass,
                                        @Nullable Filter<? super T> classFilter,
                                        @Nullable T baseClass,
                                        @Nullable T initialClass,
                                        boolean isShowMembers,
                                        boolean isShowLibraryContents) {
    this(title, project, scope, elementClass, classFilter, null, baseClass, initialClass, isShowMembers, isShowLibraryContents);
  }

  public AbstractTreeClassChooserDialog(@NlsContexts.DialogTitle String title,
                                        @NotNull Project project,
                                        GlobalSearchScope scope,
                                        @NotNull Class<T> elementClass,
                                        @Nullable Filter<? super T> classFilter,
                                        @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                        @Nullable T baseClass,
                                        @Nullable T initialClass,
                                        boolean isShowMembers,
                                        boolean isShowLibraryContents) {
    super(project, true);
    myScope = scope;
    myElementClass = elementClass;
    myClassFilter = classFilter == null ? allFilter() : classFilter;
    myComparator = comparator == null ? AlphaComparator.getInstance() : comparator;
    myBaseClass = baseClass;
    myInitialClass = initialClass;
    myIsShowMembers = isShowMembers;
    myIsShowLibraryContents = isShowLibraryContents;
    setTitle(title);
    myProject = project;
    init();
    if (initialClass != null) {
      select(initialClass);
    }

    handleSelectionChanged();
  }

  private Filter<? super T> allFilter() {
    return __ -> true;
  }

  @Override
  protected JComponent createCenterPanel() {
    ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(myProject) {
      @Override
      public boolean isShowMembers() {
        return myIsShowMembers;
      }

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return true;
      }

      @Override
      public boolean isShowLibraryContents() {
        return myIsShowLibraryContents;
      }

      @Override
      public boolean isShowModules() {
        return false;
      }
    };

    myModel = new StructureTreeModel<>(treeStructure, getDisposable());
    myModel.setComparator(myComparator);
    myTree = new Tree(new AsyncTreeModel(myModel, getDisposable()));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(JBUI.size(500, 300));
    scrollPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.RIGHT | SideBorder.LEFT | SideBorder.BOTTOM);

    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path != null && myTree.isPathSelected(path)) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    myTree.addTreeSelectionListener(__ -> handleSelectionChanged());

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper(getDisposable());

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    myGotoByNamePanel = new ChooseByNamePanel(myProject, createChooseByNameModel(), null, myScope.isSearchInLibraries(), getContext()) {
      @Override
      protected void showTextFieldPanel() {
      }

      @Override
      protected void close(boolean isOk) {
        super.close(isOk);

        if (isOk) {
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }

      @Override
      protected @NotNull Set<Object> filter(@NotNull Set<Object> elements) {
        return doFilter(elements);
      }

      @Override
      protected void initUI(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        if (myProject != null && !myProject.isDefault() && DumbService.getInstance(myProject).isDumb()) {
          JBLabel dumbLabel = new JBLabel(IdeBundle.message("dumb.mode.results.might.be.incomplete"), SwingConstants.LEFT);
          dumbLabel.setIcon(AllIcons.General.Warning);
          dumbLabel.setBorder(new JBEmptyBorder(10, 3, 0, 3));
          dummyPanel.add(dumbLabel, BorderLayout.SOUTH);
        }
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance()
                                                                                      .requestFocus(IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()), true));
      }

      @Override
      protected void showList() {
        super.showList();
        if (myInitialClass != null && myList.getModel().getSize() > 0) {
          myList.setSelectedValue(myInitialClass, true);
          myInitialClass = null;
        }
      }

      @Override
      protected void chosenElementMightChange() {
        handleSelectionChanged();
      }
    };

    Disposer.register(myDisposable, myGotoByNamePanel);

    myTabbedPane.addTab(IdeBundle.message("tab.chooser.search.by.name"), dummyPanel);
    myTabbedPane.addTab(IdeBundle.message("tab.chooser.project"), scrollPane);

    myGotoByNamePanel.invoke(new MyCallback(), getModalityState(), false);

    myTabbedPane.addChangeListener(__ -> handleSelectionChanged());

    return myTabbedPane.getComponent();
  }

  private Set<Object> doFilter(Set<Object> elements) {
    Set<Object> result = new LinkedHashSet<>();
    for (Object o : elements) {
      //noinspection unchecked
      if (myElementClass.isInstance(o) && getFilter().isAccepted((T)o)) {
        result.add(o);
      }
    }
    return result;
  }

  protected ChooseByNameModel createChooseByNameModel() {
    if (myBaseClass == null) {
      return new MyGotoClassModel<>(myProject, this);
    }
    else {
      BaseClassInheritorsProvider<T> inheritorsProvider = getInheritorsProvider(myBaseClass);
      if (inheritorsProvider != null) {
        return new SubclassGotoClassModel<>(myProject, this, inheritorsProvider);
      }
      else {
        throw new IllegalStateException("inheritors provider is null");
      }
    }
  }

  /**
   * Makes sense only in case of not null base class.
   */
  protected @Nullable BaseClassInheritorsProvider<T> getInheritorsProvider(@NotNull T baseClass) {
    return null;
  }

  private void handleSelectionChanged() {
    mySelectedClass = calcSelectedClass();
    if (mySelectedClass == null) {
      setOKActionEnabled(false);
    } else {
      T selectedClass =  mySelectedClass;
      ReadAction
        .nonBlocking(() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> myClassFilter.isAccepted(selectedClass)))
        .finishOnUiThread(getModalityState(), isAccepted -> setOKActionEnabled(mySelectedClass != null && isAccepted))
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  @Override
  public @Nullable T getSelected() {
    return getExitCode() == OK_EXIT_CODE ? mySelectedClass : null;
  }

  @Override
  public void select(@NotNull T aClass) {
    selectElementInTree(aClass);
  }

  @Override
  public void selectDirectory(@NotNull PsiDirectory directory) {
    selectElementInTree(directory);
  }

  @Override
  public void showDialog() {
    show();
  }

  @Override
  public void showPopup() {
    //todo leak via not shown dialog?
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(myProject, createChooseByNameModel(), getContext());
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      @Override
      public void elementChosen(Object element) {
        //noinspection unchecked
        mySelectedClass = (T)element;
        ((Navigatable)element).navigate(true);
      }
    }, getModalityState(), true);
  }

  private T getContext() {
    return myBaseClass != null ? myBaseClass : myInitialClass;
  }

  private void selectElementInTree(@NotNull PsiElement element) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myModel != null) {
        myModel.select(element, myTree, path -> {
        });
      }
    }, getModalityState());
  }

  private @NotNull ModalityState getModalityState() {
    return ModalityState.stateForComponent(getRootPane());
  }


  protected @Nullable T calcSelectedClass() {
    if (getTabbedPane().getSelectedIndex() == 0) {
      //noinspection unchecked
      return (T)getGotoByNamePanel().getChosenElement();
    }
    else {
      TreePath path = getTree().getSelectionPath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      return getSelectedFromTreeUserObject(node);
    }
  }

  protected abstract T getSelectedFromTreeUserObject(DefaultMutableTreeNode node);

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeClassChooserDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGotoByNamePanel.getPreferredFocusedComponent();
  }

  protected @NotNull Project getProject() {
    return myProject;
  }

  protected GlobalSearchScope getScope() {
    return myScope;
  }

  protected @NotNull Filter<? super T> getFilter() {
    return myClassFilter;
  }

  T getBaseClass() {
    return myBaseClass;
  }

  protected TabbedPaneWrapper getTabbedPane() {
    return myTabbedPane;
  }

  protected Tree getTree() {
    return myTree;
  }

  protected ChooseByNamePanel getGotoByNamePanel() {
    return myGotoByNamePanel;
  }

  protected abstract @NotNull List<T> getClassesByName(final String name,
                                                       final boolean checkBoxState,
                                                       final String pattern,
                                                       final GlobalSearchScope searchScope);

  public void setInitialSelection(Function<Set<Object>, Object> initialSelection) {
    myGotoByNamePanel.setInitialSelection(initialSelection);
  }

  protected static class MyGotoClassModel<T extends PsiNamedElement> extends GotoClassModel2 {
    private final AbstractTreeClassChooserDialog<T> myTreeClassChooserDialog;

    public MyGotoClassModel(@NotNull Project project,
                            AbstractTreeClassChooserDialog<T> treeClassChooserDialog) {
      super(project);
      myTreeClassChooserDialog = treeClassChooserDialog;
    }

    AbstractTreeClassChooserDialog<T> getTreeClassChooserDialog() {
      return myTreeClassChooserDialog;
    }

    @Override
    public Object @NotNull [] getElementsByName(@NotNull String name, @NotNull FindSymbolParameters parameters, @NotNull ProgressIndicator canceled) {
      String patternName = parameters.getLocalPatternName();
      List<T> classes = myTreeClassChooserDialog.getClassesByName(
        name, parameters.isSearchInLibraries(), patternName, myTreeClassChooserDialog.getScope()
      );
      if (classes.isEmpty()) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      if (classes.size() == 1) {
        return isAccepted(classes.get(0)) ? ArrayUtil.toObjectArray(classes) : ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }
      Set<String> qNames = new HashSet<>();
      List<T> list = new ArrayList<>(classes.size());
      for (T aClass : classes) {
        if (qNames.add(getFullName(aClass)) && isAccepted(aClass)) {
          list.add(aClass);
        }
      }
      return ArrayUtil.toObjectArray(list);
    }

    @Override
    public boolean isDumbAware() {
      return false;
    }

    @Override
    public @Nullable String getPromptText() {
      return null;
    }

    protected boolean isAccepted(T aClass) {
      return myTreeClassChooserDialog.getFilter().isAccepted(aClass);
    }
  }

  public abstract static class BaseClassInheritorsProvider<T> {
    private final T myBaseClass;
    private final GlobalSearchScope myScope;

    public BaseClassInheritorsProvider(T baseClass, GlobalSearchScope scope) {
      myBaseClass = baseClass;
      myScope = scope;
    }

    public T getBaseClass() {
      return myBaseClass;
    }

    public GlobalSearchScope getScope() {
      return myScope;
    }

    protected abstract @NotNull Query<T> searchForInheritors(T baseClass, GlobalSearchScope searchScope, boolean checkDeep);

    protected abstract boolean isInheritor(T clazz, T baseClass, boolean checkDeep);

    protected abstract String[] getNames();

    Query<T> searchForInheritorsOfBaseClass() {
      return searchForInheritors(myBaseClass, myScope, true);
    }

    boolean isInheritorOfBaseClass(T aClass) {
      return isInheritor(aClass, myBaseClass, true);
    }
  }

  private static final class SubclassGotoClassModel<T extends PsiNamedElement> extends MyGotoClassModel<T> {
    private final BaseClassInheritorsProvider<T> myInheritorsProvider;

    private boolean myFastMode = true;

    SubclassGotoClassModel(final @NotNull Project project,
                           final @NotNull AbstractTreeClassChooserDialog<T> treeClassChooserDialog,
                           @NotNull BaseClassInheritorsProvider<T> inheritorsProvider) {
      super(project, treeClassChooserDialog);
      myInheritorsProvider = inheritorsProvider;
      assert myInheritorsProvider.getBaseClass() != null;
    }

    @Override
    public void processNames(@NotNull Processor<? super String> nameProcessor, @NotNull FindSymbolParameters parameters) {
      if (myFastMode) {
        myFastMode = myInheritorsProvider.searchForInheritorsOfBaseClass().forEach(new Processor<>() {
          private final long start = System.currentTimeMillis();

          @Override
          public boolean process(T aClass) {
            if (System.currentTimeMillis() - start > 500 && !ApplicationManager.getApplication().isUnitTestMode()) {
              return false;
            }
            if (getTreeClassChooserDialog().getFilter().isAccepted(aClass) && aClass.getName() != null) {
              nameProcessor.process(aClass.getName());
            }
            return true;
          }
        });
      }
      if (!myFastMode) {
        for (String name : myInheritorsProvider.getNames()) {
          nameProcessor.process(name);
        }
      }
    }

    @Override
    protected boolean isAccepted(T aClass) {
      if (myFastMode) {
        return getTreeClassChooserDialog().getFilter().isAccepted(aClass);
      }
      return (aClass == getTreeClassChooserDialog().getBaseClass() ||
              myInheritorsProvider.isInheritorOfBaseClass(aClass)) &&
             getTreeClassChooserDialog().getFilter().isAccepted(aClass);
    }
  }

  private final class MyCallback extends ChooseByNamePopupComponent.Callback {
    @Override
    public void elementChosen(Object element) {
      //noinspection unchecked
      mySelectedClass = (T)element;
      close(OK_EXIT_CODE);
    }
  }
}
