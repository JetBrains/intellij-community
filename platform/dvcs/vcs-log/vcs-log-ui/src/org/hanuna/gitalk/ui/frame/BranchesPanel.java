package org.hanuna.gitalk.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import org.hanuna.gitalk.ui.VcsLogController;
import org.hanuna.gitalk.ui.render.Print_Parameters;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel with branch labels, above the graph.
 *
 * @author Kirill Likhodedov
 */
public class BranchesPanel extends JPanel {

  private final VcsLogController myUiController;
  private List<Ref> myRefs;
  private final RefPainter myRefPainter;

  private Map<Integer, Ref> myRefPositions = new HashMap<Integer, Ref>();

  public BranchesPanel(VcsLogController vcsLog_controller) {
    myUiController = vcsLog_controller;
    myRefs = getRefsToDisplayOnPanel();
    myRefPainter = new RefPainter();

    setPreferredSize(new Dimension(-1, Print_Parameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        Ref ref = findRef(e);
        if (ref != null) {
          myUiController.jumpToCommit(ref.getCommitHash());
        }
      }
    });

  }

  @Nullable
  private Ref findRef(MouseEvent e) {
    List<Integer> sortedPositions = Ordering.natural().sortedCopy(myRefPositions.keySet());
    int index = Ordering.natural().binarySearch(sortedPositions, e.getX());
    if (index < 0) {
      index = -index - 2;
    }
    if (index < 0) {
      return null;
    }
    return myRefPositions.get(sortedPositions.get(index));
  }

  @Override
  protected void paintComponent(Graphics g) {
    myRefPositions = myRefPainter.draw((Graphics2D)g, myRefs, 0);
  }

  public void rebuild() {
    myRefs = getRefsToDisplayOnPanel();
    getParent().repaint();
  }

  private List<Ref> getRefsToDisplayOnPanel() {
    Collection<Ref> allRefs = myUiController.getRefs();
    final List<Ref> localRefs = ContainerUtil.filter(allRefs, new Condition<Ref>() {
      @Override
      public boolean value(Ref ref) {
        return ref.getType().isLocalOrHead();
      }
    });

    return ContainerUtil.filter(allRefs, new Condition<Ref>() {
      @Override
      public boolean value(Ref ref) {
        if (ref.getType() == Ref.RefType.REMOTE_BRANCH) {
          return !thereIsLocalRefOfHash(ref.getCommitHash(), localRefs);
        }
        if (ref.getType().isBranch()) {
          return true;
        }
        return false;
      }
    });
  }

  private static boolean thereIsLocalRefOfHash(Hash commitHash, List<Ref> localRefs) {
    for (Ref localRef : localRefs) {
      if (localRef.getCommitHash().equals(commitHash)) {
        return true;
      }
    }
    return false;
  }

}
