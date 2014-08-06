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
  public static final Icon FailedTask = load("/icons/failed.png");
  public static final Icon Prev = load("/icons/prev.png");
  public static final Icon Next = load("/icons/next.png");
  public static final Icon Run = load("/icons/Run.png");
  public static final Icon ShortcutReminder = load("/icons/ShortcutReminder.png");
  public static final Icon WatchInput = load("/icons/WatchInput.png");
  public static final Icon Refresh24 = load("/icons/refresh24.png");
  public static final Icon Refresh = load("/icons/refresh.png");
  public static final Icon Playground = load("/icons/playground.png");
  public static final Icon ErrorIcon = load("/icons/fatalError.png");
  public static final Icon Add = load("/icons/add.png");
}
