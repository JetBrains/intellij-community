package com.intellij.slicer;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author cdr
 */
public class SliceTreeBuilder extends AbstractTreeBuilder {
  public boolean splitByLeafExpressions;
  public final boolean dataFlowToThis;
  private static final Comparator<NodeDescriptor> SLICE_NODE_COMPARATOR = new Comparator<NodeDescriptor>() {
    public int compare(NodeDescriptor o1, NodeDescriptor o2) {
      if (!(o1 instanceof SliceNode) || !(o2 instanceof SliceNode)) {
        return AlphaComparator.INSTANCE.compare(o1, o2);
      }
      SliceNode node1 = (SliceNode)o1;
      SliceNode node2 = (SliceNode)o2;
      SliceUsage usage1 = node1.getValue();
      SliceUsage usage2 = node2.getValue();

      PsiElement element1 = usage1.getElement();
      PsiElement element2 = usage2.getElement();

      PsiFile file1 = element1 == null ? null : element1.getContainingFile();
      PsiFile file2 = element2 == null ? null : element2.getContainingFile();

      if (file1 == null) return file2 == null ? 0 : 1;
      if (file2 == null) return -1;

      if (file1 == file2) {
        return element1.getTextOffset() - element2.getTextOffset();
      }

      return Comparing.compare(file1.getName(), file2.getName());
    }
  };

  public SliceTreeBuilder(JTree tree, Project project, boolean dataFlowToThis, final SliceNode rootNode) {
    super(tree, (DefaultTreeModel)tree.getModel(), new SliceTreeStructure(project, rootNode), SLICE_NODE_COMPARATOR, false);
    this.dataFlowToThis = dataFlowToThis;
    initRootNode();
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  public void switchToSplittedNodes() {
    final SliceRootNode root = (SliceRootNode)getRootNode().getUserObject();

    Collection<PsiElement> leaves = calcLeafExpressions(root);
    if (leaves == null) return;  //cancelled

    if (leaves.isEmpty()) {
      Messages.showErrorDialog("Unable to find leaf expressions to group by", "Cannot group");
      return;
    }

    root.setChanged();
    root.restructureByLeaves(leaves);
    root.setChanged();
    splitByLeafExpressions = true;
    root.targetEqualUsages.clear();

    getUpdater().cancelAllRequests();
    getUpdater().addSubtreeToUpdateByElement(root);
  }

  @Nullable("null means canceled")
  public static Collection<PsiElement> calcLeafExpressions(final SliceRootNode root) {
    final Ref<Collection<PsiElement>> leafExpressions = Ref.create(null);
    boolean b = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        Collection<PsiElement> l = SliceLeafAnalyzer.calcLeafExpressions(root);
        leafExpressions.set(l);
      }
    }, "Expanding all nodes... (may very well take the whole day)", true, root.getProject());
    if (!b) return null;

    Collection<PsiElement> leaves = leafExpressions.get();
    return leaves;
  }

  public void switchToUnsplittedNodes() {
    SliceRootNode root = (SliceRootNode)getRootNode().getUserObject();
    SliceLeafValueRootNode valueNode = (SliceLeafValueRootNode)root.myCachedChildren.get(0);
    SliceNode rootNode = valueNode.myCachedChildren.get(0);

    root.switchToAllLeavesTogether(rootNode.getValue());
    root.setChanged();
    splitByLeafExpressions = false;
    root.targetEqualUsages.clear();

    getUpdater().cancelAllRequests();
    getUpdater().addSubtreeToUpdateByElement(root);
  }
}
