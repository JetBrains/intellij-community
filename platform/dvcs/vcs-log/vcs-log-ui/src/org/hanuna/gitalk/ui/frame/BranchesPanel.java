package org.hanuna.gitalk.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.hanuna.gitalk.ui.render.PrintParameters;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.jetbrains.annotations.NotNull;
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

  private final VcsLogDataHolder myUiController;
  private final VcsLogUI myUI;

  private List<VcsRef> myRefs;
  private final RefPainter myRefPainter;

  private Map<Integer, VcsRef> myRefPositions = new HashMap<Integer, VcsRef>();

  public BranchesPanel(@NotNull VcsLogDataHolder logController, @NotNull VcsLogUI UI) {
    myUiController = logController;
    myUI = UI;
    myRefs = getRefsToDisplayOnPanel();
    myRefPainter = new RefPainter();

    setPreferredSize(new Dimension(-1, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        VcsRef ref = findRef(e);
        if (ref != null) {
          myUI.jumpToCommit(ref.getCommitHash());
        }
      }
    });

  }

  @Nullable
  private VcsRef findRef(MouseEvent e) {
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

  private List<VcsRef> getRefsToDisplayOnPanel() {
    Collection<VcsRef> allRefs = myUiController.getDataPack().getRefsModel().getAllRefs();
    final List<VcsRef> localRefs = ContainerUtil.filter(allRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isLocalOrHead();
      }
    });

    return ContainerUtil.filter(allRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        if (ref.getType() == VcsRef.RefType.REMOTE_BRANCH) {
          return !thereIsLocalRefOfHash(ref.getCommitHash(), localRefs);
        }
        if (ref.getType().isBranch()) {
          return true;
        }
        return false;
      }
    });
  }

  private static boolean thereIsLocalRefOfHash(Hash commitHash, List<VcsRef> localRefs) {
    for (VcsRef localRef : localRefs) {
      if (localRef.getCommitHash().equals(commitHash)) {
        return true;
      }
    }
    return false;
  }

}
