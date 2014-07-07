package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * author: liana
 * data: 7/7/14.
 */
public class StudyIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, StudyIcons.class);
  }

  public static final Icon Resolve = load("/icons/resolve.png"); // 24*24
  public static final Icon UncheckedTask = load("/icons/unchecked.png");
  public static final Icon CheckedTask = load("/icons/checked.png");
}
