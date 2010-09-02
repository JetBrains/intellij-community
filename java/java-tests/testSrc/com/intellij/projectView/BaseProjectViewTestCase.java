/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class BaseProjectViewTestCase extends TestSourceBasedTestCase {
  protected AbstractTreeStructure myStructure;
  protected boolean myShowMembers = false;
  private List<AbstractProjectViewPSIPane> myPanes = new ArrayList<AbstractProjectViewPSIPane>();

  protected Queryable.PrintInfo myPrintInfo;

  protected void setUp() throws Exception {
    super.setUp();

    myStructure = new TestProjectTreeStructure(myProject) {
      public boolean isShowMembers() {
        return myShowMembers;
      }
    };
  }

  protected void tearDown() throws Exception {
    for (final AbstractProjectViewPSIPane myPane : myPanes) {
      Disposer.dispose(myPane);
    }
    myPanes = null;
    myStructure = null;
    super.tearDown();
  }

  protected AbstractProjectViewPSIPane createPane() {
    final AbstractProjectViewPSIPane pane = new MyAbstractProjectViewPSIPane();
    pane.createComponent();
    myPanes.add(pane);
    return pane;
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, @NonNls String expected) {
    assertStructureEqual(packageDirectory, expected, 17, myStructure);
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, @NonNls String expected, int maxRowCount) {
    assertStructureEqual(packageDirectory, expected, maxRowCount, myStructure);
  }

  protected void useStandardProviders() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject));
  }

  protected AbstractProjectTreeStructure getProjectTreeStructure() {
    return (AbstractProjectTreeStructure)myStructure;
  }

  protected void assertStructureEqual(@NonNls String expected) {
    assertStructureEqual(myStructure.getRootElement(), expected);
  }

  protected void assertStructureEqual(String expected, Comparator comparator) {
    assertStructureEqual(myStructure.getRootElement(), expected, 27, comparator);
  }


  private void assertStructureEqual(PsiDirectory root, String expected, int maxRowCount, AbstractTreeStructure structure) {
    assertNotNull(root);
    PsiDirectoryNode rootNode = new PsiDirectoryNode(myProject, root, (ViewSettings)structure);
    assertStructureEqual(rootNode, expected, maxRowCount, IdeaTestUtil.DEFAULT_COMPARATOR);
  }

  private void assertStructureEqual(Object rootNode, String expected) {
    assertStructureEqual(rootNode, expected, 17, IdeaTestUtil.DEFAULT_COMPARATOR);
  }

  private void assertStructureEqual(Object rootNode, String expected, int maxRowCount, Comparator comparator) {
    checkGetParentConsistency(rootNode);
    StringBuffer actual = IdeaTestUtil.print(myStructure, rootNode, 0, comparator, maxRowCount, ' ', myPrintInfo);
    assertEquals(expected, actual.toString());
  }

  private void checkGetParentConsistency(Object from) {
    Object[] childElements = myStructure.getChildElements(from);
    for (Object childElement : childElements) {
      assertSame(from, myStructure.getParentElement(childElement));
      checkGetParentConsistency(childElement);
    }
  }

  protected static boolean isExpanded(DefaultMutableTreeNode nodeForElement, AbstractProjectViewPSIPane pane) {
    TreePath path = new TreePath(nodeForElement.getPath());
    return pane.getTree().isExpanded(path.getParentPath());
  }

  protected static DefaultMutableTreeNode getNodeForElement(PsiElement element, AbstractProjectViewPSIPane pane) {
    JTree tree = pane.getTree();
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    return getNodeForElement(root, model, element);
  }

  private static DefaultMutableTreeNode getNodeForElement(Object root, TreeModel model, PsiElement element) {
    if (root instanceof DefaultMutableTreeNode) {
      Object userObject = ((DefaultMutableTreeNode)root).getUserObject();
      if (userObject instanceof AbstractTreeNode) {
        AbstractTreeNode treeNode = (AbstractTreeNode)userObject;
        if (element.equals(treeNode.getValue())) return (DefaultMutableTreeNode)root;
        for (int i = 0; i < model.getChildCount(root); i++) {
          DefaultMutableTreeNode nodeForChild = getNodeForElement(model.getChild(root, i), model, element);
          if (nodeForChild != null) return nodeForChild;
        }
      }
    }
    return null;
  }

  public static void checkNavigateFromSourceBehaviour(PsiElement element, VirtualFile virtualFile, AbstractProjectViewPSIPane pane) {
    Disposer.dispose(pane);
    pane.createComponent();
    assertNull(getNodeForElement(element, pane));
    pane.select(element, virtualFile, true);
    assertTrue(isExpanded(element, pane));
  }

  public static boolean isExpanded(PsiElement element, AbstractProjectViewPSIPane pane) {
    DefaultMutableTreeNode nodeForElement = getNodeForElement(element, pane);
    return nodeForElement != null && isExpanded((DefaultMutableTreeNode)nodeForElement.getParent(), pane);
  }

  protected static void assertListsEqual(ListModel model, String expected) {
    assertEquals(expected, IdeaTestUtil.print(model));
  }

  public static void checkContainsMethod(final Object rootElement, final AbstractTreeStructure structure) {
    ProjectViewTestUtil.checkContainsMethod(rootElement, structure, new Function<AbstractTreeNode, VirtualFile[]>() {
      public VirtualFile[] fun(AbstractTreeNode kid) {
        if (kid instanceof PackageElementNode) {
          return ((PackageElementNode)kid).getVirtualFiles();
        }
        return null;
      }
    });
  }

  protected String getTestPath() {
    return "projectView";
  }

  protected static String getPackageRelativePath() {
    return "com/package1";
  }

  protected PsiDirectory getPackageDirectory() {
    return getPackageDirectory(getPackageRelativePath());
  }

  private class MyAbstractProjectViewPSIPane extends AbstractProjectViewPSIPane {
    public MyAbstractProjectViewPSIPane() {
      super(BaseProjectViewTestCase.this.myProject);
    }

    public SelectInTarget createSelectInTarget() {
      return null;
    }

    @NonNls
    public String getComponentName() {
      return "comp name";
    }

    protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
      return new AbstractTreeUpdater(treeBuilder);
    }

    @NotNull
    protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
      return new ProjectTreeBuilder(myProject, myTree, treeModel, AlphaComparator.INSTANCE,
                                    (ProjectAbstractTreeStructureBase)myTreeStructure) {
        protected AbstractTreeUpdater createUpdater() {
          return createTreeUpdater(this);
        }

        protected void addTaskToWorker(final Runnable runnable, boolean first, final Runnable postRunnable) {
          runnable.run();
          postRunnable.run();
        }
      };
    }

    protected ProjectAbstractTreeStructureBase createStructure() {
      return (ProjectAbstractTreeStructureBase)myStructure;
    }

    protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
      return new ProjectViewTree(treeModel) {
        public DefaultMutableTreeNode getSelectedNode() {
          return null;
        }
      };
    }

    public Icon getIcon() {
      return null;
    }

    @NotNull
    public String getId() {
      return "";
    }

    public String getTitle() {
      return null;
    }

    public int getWeight() {
      return 0;
    }

    public void projectOpened() {
      final Runnable runnable = new DumbAwareRunnable() {
        public void run() {
          final ProjectView projectView = ProjectView.getInstance(myProject);
          projectView.addProjectPane(MyAbstractProjectViewPSIPane.this);
        }
      };
      StartupManager.getInstance(myProject).registerPostStartupActivity(runnable);
    }

    public void projectClosed() {
    }

    public void initComponent() { }

    public void disposeComponent() {

    }
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath(getClass());
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17();
  }
}
