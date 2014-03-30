/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public abstract class AbstractTreeClassChooserDialog<T extends PsiNamedElement> extends DialogWrapper implements TreeChooser<T> {
  private Tree myTree;
  private T mySelectedClass = null;
  @NotNull private final Project myProject;
  private BaseProjectTreeBuilder myBuilder;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private final GlobalSearchScope myScope;
  @NotNull private final Filter<T> myClassFilter;
  private final Class<T> myElementClass;
  @Nullable private final T myBaseClass;
  private T myInitialClass;
  private final boolean myIsShowMembers;
  private final boolean myIsShowLibraryContents;

  public AbstractTreeClassChooserDialog(String title, Project project, final Class<T> elementClass) {
    this(title, project, elementClass, null);
  }

  public AbstractTreeClassChooserDialog(String title, Project project, final Class<T> elementClass, @Nullable T initialClass) {
    this(title, project, GlobalSearchScope.projectScope(project), elementClass, null, initialClass);
  }

  public AbstractTreeClassChooserDialog(String title,
                                        @NotNull Project project,
                                        GlobalSearchScope scope,
                                        @NotNull Class<T> elementClass,
                                        @Nullable Filter<T> classFilter,
                                        @Nullable T initialClass) {
    this(title, project, scope, elementClass, classFilter, null, initialClass, false, true);
  }

  public AbstractTreeClassChooserDialog(String title,
                                        @NotNull Project project,
                                        GlobalSearchScope scope,
                                        @NotNull Class<T> elementClass,
                                        @Nullable Filter<T> classFilter,
                                        @Nullable T baseClass,
                                        @Nullable T initialClass,
                                        boolean isShowMembers,
                                        boolean isShowLibraryContents) {
    super(project, true);
    myScope = scope;
    myElementClass = elementClass;
    myClassFilter = classFilter == null ? allFilter() : classFilter;
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

  private Filter<T> allFilter() {
    return new Filter<T>() {
      @Override
      public boolean isAccepted(T element) {
        return true;
      }
    };
  }

  @Override
  protected JComponent createCenterPanel() {
    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);

    ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(myProject) {
      @Override
      public boolean isFlattenPackages() {
        return false;
      }

      @Override
      public boolean isShowMembers() {
        return myIsShowMembers;
      }

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return true;
      }

      @Override
      public boolean isAbbreviatePackageNames() {
        return false;
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
    myBuilder = new ProjectTreeBuilder(myProject, myTree, model, AlphaComparator.INSTANCE, treeStructure);

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());
    UIUtil.setLineStyleAngled(myTree);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

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
      protected boolean onDoubleClick(MouseEvent event) {
        TreePath path = myTree.getPathForLocation(event.getX(), event.getY());
        if (path != null && myTree.isPathSelected(path)) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    myTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
          handleSelectionChanged();
        }
      }
    );

    new TreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper(getDisposable());

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    String name = null;
/*
    if (myInitialClass != null) {
      name = myInitialClass.getName();
    }
*/
    myGotoByNamePanel = new ChooseByNamePanel(myProject, createChooseByNameModel(), name, myScope.isSearchInLibraries(), getContext()) {

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

      @NotNull
      @Override
      protected Set<Object> filter(@NotNull Set<Object> elements) {
        return doFilter(elements);
      }

      @Override
      protected void initUI(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()).requestFocus();
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

    myTabbedPane.addChangeListener(
      new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          handleSelectionChanged();
        }
      }
    );

    return myTabbedPane.getComponent();
  }

  private Set<Object> doFilter(Set<Object> elements) {
    Set<Object> result = new LinkedHashSet<Object>();
    for (Object o : elements) {
      if (myElementClass.isInstance(o) && getFilter().isAccepted((T)o)) {
        result.add(o);
      }
    }
    return result;
  }

  protected ChooseByNameModel createChooseByNameModel() {
    if (myBaseClass == null) {
      return new MyGotoClassModel<T>(myProject, this);
    }
    else {
      BaseClassInheritorsProvider<T> inheritorsProvider = getInheritorsProvider(myBaseClass);
      if (inheritorsProvider != null) {
        return new SubclassGotoClassModel<T>(myProject, this, inheritorsProvider);
      }
      else {
        throw new IllegalStateException("inheritors provider is null");
      }
    }
  }

  /**
   * Makes sense only in case of not null base class.
   *
   * @param baseClass
   * @return
   */
  @Nullable
  protected BaseClassInheritorsProvider<T> getInheritorsProvider(@NotNull T baseClass) {
    return null;
  }

  private void handleSelectionChanged() {
    T selection = calcSelectedClass();
    setOKActionEnabled(selection != null);
  }

  @Override
  protected void doOKAction() {
    mySelectedClass = calcSelectedClass();
    if (mySelectedClass == null) return;
    if (!myClassFilter.isAccepted(mySelectedClass)) {
      Messages.showErrorDialog(myTabbedPane.getComponent(),
                               SymbolPresentationUtil.getSymbolPresentableText(mySelectedClass) + " is not acceptable");
      return;
    }
    super.doOKAction();
  }

  @Override
  public T getSelected() {
    return mySelectedClass;
  }

  @Override
  public void select(@NotNull final T aClass) {
    selectElementInTree(aClass);
  }

  @Override
  public void selectDirectory(@NotNull final PsiDirectory directory) {
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
        mySelectedClass = (T)element;
        ((Navigatable)element).navigate(true);
      }
    }, getModalityState(), true);
  }

  private T getContext() {
    return myBaseClass != null ? myBaseClass : myInitialClass != null ? myInitialClass : null;
  }


  private void selectElementInTree(@NotNull final PsiElement element) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myBuilder == null) return;
        final VirtualFile vFile = PsiUtilBase.getVirtualFile(element);
        myBuilder.select(element, vFile, false);
      }
    }, getModalityState());
  }

  private ModalityState getModalityState() {
    return ModalityState.stateForComponent(getRootPane());
  }


  @Nullable
  protected T calcSelectedClass() {
    if (getTabbedPane().getSelectedIndex() == 0) {
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
  public void dispose() {
    if (myBuilder != null) {
      Disposer.dispose(myBuilder);
      myBuilder = null;
    }
    super.dispose();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeClassChooserDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGotoByNamePanel.getPreferredFocusedComponent();
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  GlobalSearchScope getScope() {
    return myScope;
  }

  @NotNull
  protected Filter<T> getFilter() {
    return myClassFilter;
  }

  T getBaseClass() {
    return myBaseClass;
  }

  T getInitialClass() {
    return myInitialClass;
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

  protected static class MyGotoClassModel<T extends PsiNamedElement> extends GotoClassModel2 {
    private final AbstractTreeClassChooserDialog<T> myTreeClassChooserDialog;

    AbstractTreeClassChooserDialog<T> getTreeClassChooserDialog() {
      return myTreeClassChooserDialog;
    }

    public MyGotoClassModel(@NotNull Project project,
                            AbstractTreeClassChooserDialog<T> treeClassChooserDialog) {
      super(project);
      myTreeClassChooserDialog = treeClassChooserDialog;
    }

    @NotNull
    @Override
    public Object[] getElementsByName(String name, FindSymbolParameters parameters, @NotNull ProgressIndicator canceled) {
      String patternName = parameters.getLocalPatternName();
      List<T> classes = myTreeClassChooserDialog.getClassesByName(
        name, parameters.isSearchInLibraries(), patternName, myTreeClassChooserDialog.getScope()
      );
      if (classes.size() == 0) return ArrayUtil.EMPTY_OBJECT_ARRAY;
      if (classes.size() == 1) {
        return isAccepted(classes.get(0)) ? ArrayUtil.toObjectArray(classes) : ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
      Set<String> qNames = ContainerUtil.newHashSet();
      List<T> list = new ArrayList<T>(classes.size());
      for (T aClass : classes) {
        if (qNames.add(getFullName(aClass)) && isAccepted(aClass)) {
          list.add(aClass);
        }
      }
      return ArrayUtil.toObjectArray(list);
    }

    @Override
    @Nullable
    public String getPromptText() {
      return null;
    }

    protected boolean isAccepted(T aClass) {
      return myTreeClassChooserDialog.getFilter().isAccepted(aClass);
    }
  }


  @NotNull
  protected abstract List<T> getClassesByName(final String name,
                                              final boolean checkBoxState,
                                              final String pattern,
                                              final GlobalSearchScope searchScope);

  public abstract static class BaseClassInheritorsProvider<T> {
    private final T myBaseClass;
    private final GlobalSearchScope myScope;

    public T getBaseClass() {
      return myBaseClass;
    }

    public GlobalSearchScope getScope() {
      return myScope;
    }

    public BaseClassInheritorsProvider(T baseClass, GlobalSearchScope scope) {
      myBaseClass = baseClass;
      myScope = scope;
    }

    @NotNull
    protected abstract Query<T> searchForInheritors(T baseClass, GlobalSearchScope searchScope, boolean checkDeep);

    protected abstract boolean isInheritor(T clazz, T baseClass, boolean checkDeep);

    protected abstract String[] getNames();

    protected Query<T> searchForInheritorsOfBaseClass() {
      return searchForInheritors(myBaseClass, myScope, true);
    }

    protected boolean isInheritorOfBaseClass(T aClass) {
      return isInheritor(aClass, myBaseClass, true);
    }
  }

  private static class SubclassGotoClassModel<T extends PsiNamedElement> extends MyGotoClassModel<T> {
    private final BaseClassInheritorsProvider<T> myInheritorsProvider;

    private boolean myFastMode = true;

    public SubclassGotoClassModel(@NotNull final Project project,
                                  @NotNull final AbstractTreeClassChooserDialog<T> treeClassChooserDialog,
                                  @NotNull BaseClassInheritorsProvider<T> inheritorsProvider) {
      super(project, treeClassChooserDialog);
      myInheritorsProvider = inheritorsProvider;
      assert myInheritorsProvider.getBaseClass() != null;
    }

    @NotNull
    @Override
    public String[] getNames(boolean checkBoxState) {
      if (!myFastMode) {
        return myInheritorsProvider.getNames();
      }
      final List<String> names = new ArrayList<String>();

      myFastMode = myInheritorsProvider.searchForInheritorsOfBaseClass().forEach(new Processor<T>() {
        private long start = System.currentTimeMillis();

        @Override
        public boolean process(T aClass) {
          if (System.currentTimeMillis() - start > 500) {
            return false;
          }
          if ((getTreeClassChooserDialog().getFilter().isAccepted(aClass)) && aClass.getName() != null) {
            names.add(aClass.getName());
          }
          return true;
        }
      });
      if (!myFastMode) {
        return myInheritorsProvider.getNames();
      }
      if ((getTreeClassChooserDialog().getFilter().isAccepted(myInheritorsProvider.getBaseClass())) &&
          myInheritorsProvider.getBaseClass().getName() != null) {
        names.add(myInheritorsProvider.getBaseClass().getName());
      }
      return ArrayUtil.toStringArray(names);
    }


    @Override
    protected boolean isAccepted(T aClass) {
      if (myFastMode) {
        return getTreeClassChooserDialog().getFilter().isAccepted(aClass);
      }
      else {
        return (aClass == getTreeClassChooserDialog().getBaseClass() ||
                myInheritorsProvider.isInheritorOfBaseClass(aClass)) &&
               getTreeClassChooserDialog().getFilter().isAccepted(
                 aClass);
      }
    }
  }

  private class MyCallback extends ChooseByNamePopupComponent.Callback {
    @Override
    public void elementChosen(Object element) {
      mySelectedClass = (T)element;
      close(OK_EXIT_CODE);
    }
  }
}
