package com.intellij.projectView;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.*;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public abstract class BaseProjectViewTestCase extends TestSourceBasedTestCase {
  protected AbstractTreeStructure myStructure;
  protected boolean myShowMembers = false;
  private List<AbstractProjectViewPSIPane> myPanes = new ArrayList<AbstractProjectViewPSIPane>();

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
      myPane.dispose();
    }
    myPanes = null;
    myStructure = null;
    super.tearDown();
  }

  protected AbstractProjectViewPSIPane createPane() {
    final AbstractProjectViewPSIPane pane = new AbstractProjectViewPSIPane(myProject) {
      public SelectInTarget createSelectInTarget() {
        return null;
      }

      @NonNls
      public String getComponentName() {
        return null;
      }

      protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
        return new AbstractTreeUpdater(treeBuilder);
      }

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

      public String getId() {
        return null;
      }

      public String getTitle() {
        return null;
      }

      public int getWeight() {
        return 0;
      }
    };
    pane.createComponent();
    myPanes.add(pane);
    return pane;
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, String expected) {
    assertStructureEqual(packageDirectory, expected, 17, myStructure);
  }

  protected void assertStructureEqual(PsiDirectory packageDirectory, String expected, int maxRowCount) {
    assertStructureEqual(packageDirectory, expected, maxRowCount, myStructure);
  }

  private static VirtualFile[] getFiles(AbstractTreeNode kid) {
    if (kid instanceof BasePsiNode) {
      Object value = kid.getValue();
      VirtualFile virtualFile = PsiUtil.getVirtualFile((PsiElement)value);
      return new VirtualFile[]{virtualFile};
    }
    else if (kid instanceof PackageElementNode) {
      return ((PackageElementNode)kid).getVirtualFiles();
    }
    else {
      return new VirtualFile[0];
    }

  }

  private static void collect(AbstractTreeNode node, MultiValuesMap<VirtualFile, AbstractTreeNode> map,
                                final AbstractTreeStructure structure) {
    Object[] kids = structure.getChildElements(node);
    for (Object kid1 : kids) {
      ProjectViewNode kid = (ProjectViewNode)kid1;
      final VirtualFile[] files = getFiles(kid);
      for (VirtualFile vFile : files) {
        map.put(vFile, kid);
        ProjectViewNode eachParent = (ProjectViewNode)kid.getParent();
        while (eachParent != null) {
          map.put(vFile, eachParent);
          eachParent = (ProjectViewNode)eachParent.getParent();
        }

      }
      collect(kid, map, structure);
    }
  }

  protected void useStandardProviders() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject));
  }

  protected AbstractProjectTreeStructure getProjectTreeStructure() {
    return (AbstractProjectTreeStructure)myStructure;
  }

  protected void assertStructureEqual(String expected) {
    assertStructureEqual(myStructure.getRootElement(), expected);
  }

  protected void assertStructureEqual(String expected, Comparator comparator) {
    assertStructureEqual(myStructure.getRootElement(), expected, 17, comparator);
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
    StringBuffer actual = IdeaTestUtil.print(myStructure, rootNode, 0, comparator, maxRowCount, ' ');
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

  protected static void checkNavigateFromSourceBehaviour(PsiElement element, VirtualFile virtualFile, AbstractProjectViewPSIPane pane) {
    pane.dispose();
    pane.createComponent();
    assertNull(getNodeForElement(element,pane));
    pane.select(element, virtualFile, true);
    assertTrue(isExpanded(element, pane));
  }

  protected static boolean isExpanded(PsiElement element, AbstractProjectViewPSIPane pane) {
    DefaultMutableTreeNode nodeForElement = getNodeForElement(element, pane);
    return nodeForElement != null && isExpanded((DefaultMutableTreeNode)nodeForElement.getParent(), pane);
  }

  protected static void assertListsEqual(DefaultListModel model, String expected) {
    assertEquals(expected, IdeaTestUtil.print(model));
  }

  protected static void checkContainsMethod(final Object rootElement, final AbstractTreeStructure structure) {
    MultiValuesMap<VirtualFile, AbstractTreeNode> map = new MultiValuesMap<VirtualFile, AbstractTreeNode>();
    collect((AbstractTreeNode)rootElement, map, structure);

    for (VirtualFile eachFile : map.keySet()) {
      Collection<AbstractTreeNode> nodes = map.values();
      for (final AbstractTreeNode node : nodes) {
        ProjectViewNode eachNode = (ProjectViewNode)node;
        boolean actual = eachNode.contains(eachFile);
        boolean expected = map.get(eachFile).contains(eachNode);
        if (actual != expected) {
          assertTrue("file=" + eachFile + " node=" + eachNode.getTestPresentation() + " expected:" + expected, false);
        }
      }
    }
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
}
