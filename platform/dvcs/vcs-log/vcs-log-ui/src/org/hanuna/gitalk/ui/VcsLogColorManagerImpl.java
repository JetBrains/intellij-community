package org.hanuna.gitalk.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogColorManagerImpl implements VcsLogColorManager {

  // TODO select colors carefully
  private static Color[] ROOT_COLORS = { Color.RED, Color.YELLOW, Color.LIGHT_GRAY, Color.BLUE, Color.MAGENTA };

  @NotNull private final List<VirtualFile> myRoots;

  @NotNull private final Map<VirtualFile, Color> myRoots2Colors;

  public VcsLogColorManagerImpl(@NotNull Collection<VirtualFile> roots) {
    myRoots = new ArrayList<VirtualFile>(roots);
    Collections.sort(myRoots, new Comparator<VirtualFile>() { // TODO add a common util method to sort roots
      @Override
      public int compare(VirtualFile o1, VirtualFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    myRoots2Colors = ContainerUtil.newHashMap();
    int i = 0;
    for (VirtualFile root : roots) {
      myRoots2Colors.put(root, ROOT_COLORS[i]);
      i++; // TODO handle the case when there are more roots than colors
    }
  }

  @Override
  public boolean isMultipleRoots() {
    return myRoots.size() > 1;
  }

  @NotNull
  @Override
  public Color getBackgroundColor(@NotNull VcsRef ref) {
    switch (ref.getType()) {
      case HEAD:
        return Color.GREEN;
      case LOCAL_BRANCH:
        return Color.ORANGE;
      case REMOTE_BRANCH:
        return Color.CYAN;
      case TAG:
        return Color.WHITE;
      default:
        throw new IllegalArgumentException("Unknown ref type: " + ref.getType() + ", ref: " + ref);
    }
  }

  @NotNull
  @Override
  public Color getRootColor(@NotNull VirtualFile root) {
    return myRoots2Colors.get(root);
  }

}
