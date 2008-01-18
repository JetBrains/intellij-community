package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.SummaryNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.HighlightableCellRenderer;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
final class TodoCompositeRenderer implements TreeCellRenderer{
  private final NodeRenderer myNodeRenderer;
  private final HighlightableCellRenderer myColorTreeCellRenderer;

  public TodoCompositeRenderer(){
    myNodeRenderer=new NodeRenderer();
    myColorTreeCellRenderer=new HighlightableCellRenderer();
  }

  public Component getTreeCellRendererComponent(JTree tree,Object obj,boolean selected,boolean expanded,boolean leaf,int row,boolean hasFocus){
    Object userObject=((DefaultMutableTreeNode)obj).getUserObject();
    if(userObject instanceof SummaryNode){
      myNodeRenderer.getTreeCellRendererComponent(tree,userObject.toString(),selected,expanded,leaf,row,hasFocus);
      myNodeRenderer.setFont(UIUtil.getTreeFont().deriveFont(Font.BOLD));
      myNodeRenderer.setIcon(null);
      return myNodeRenderer;
    }else if(userObject instanceof HighlightedRegionProvider){
      NodeDescriptor descriptor=(NodeDescriptor)userObject;
      HighlightedRegionProvider regionProvider=(HighlightedRegionProvider)userObject;
      myColorTreeCellRenderer.getTreeCellRendererComponent(tree,obj,selected,expanded,leaf,row,hasFocus);
      for (HighlightedRegion highlightedRegion : regionProvider.getHighlightedRegions()) {
        myColorTreeCellRenderer.addHighlighter(
            highlightedRegion.startOffset,
            highlightedRegion.endOffset,
            highlightedRegion.textAttributes
        );
      }
      myColorTreeCellRenderer.setIcon(descriptor.getOpenIcon());
      return myColorTreeCellRenderer;
    }else{
      return myNodeRenderer.getTreeCellRendererComponent(tree,null,selected,expanded,leaf,row,hasFocus);
    }
  }
}
