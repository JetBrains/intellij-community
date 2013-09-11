package org.hanuna.gitalk.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
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

  private static final Color HEAD = new JBColor(new Color(0xf1ef9e), new Color(113, 111, 64));
  private static final Color LOCAL_BRANCH = new JBColor(new Color(0x75eec7), new Color(0x0D6D4F));
  private static final Color REMOTE_BRANCH = new JBColor(new Color(0xbcbcfc), new Color(0xbcbcfc).darker().darker());
  private static final Color TAG = JBColor.WHITE;

  private static final Color REF_BORDER = JBColor.GRAY;
  private static final Color ROOT_INDICATOR_BORDER = JBColor.LIGHT_GRAY;

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
        return HEAD;
      case LOCAL_BRANCH:
        return LOCAL_BRANCH;
      case REMOTE_BRANCH:
        return REMOTE_BRANCH;
      case TAG:
        return TAG;
      default:
        throw new IllegalArgumentException("Unknown ref type: " + ref.getType() + ", ref: " + ref);
    }
  }

  @NotNull
  @Override
  public Color getRootColor(@NotNull VirtualFile root) {
    return myRoots2Colors.get(root);
  }

  @NotNull
  @Override
  public Color getReferenceBorderColor() {
    return REF_BORDER;
  }

  @NotNull
  @Override
  public Color getRootIndicatorBorder() {
    return ROOT_INDICATOR_BORDER;
  }

}
