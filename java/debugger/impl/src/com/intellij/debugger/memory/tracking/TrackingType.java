package org.jetbrains.debugger.memory.tracking;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

public enum TrackingType {

  IDENTITY("Id Based Tracking",JBColor.yellow),
  HASH("Hash Based Tracking", JBColor.yellow),
  RETAIN("Retaining Reference Object Tracking", JBColor.yellow),
  CREATION("Track Constructors", JBColor.orange);

  private final String myDescription;
  private final JBColor myHighlighting;

  TrackingType(@NotNull String description, @NotNull JBColor highlighting) {
    myDescription = description;
    myHighlighting = highlighting;
  }

  @NotNull
  public JBColor color() {
    return myHighlighting;
  }

  @NotNull
  public String description() {
    return myDescription;
  }

  public static JBColor inactiveColor() { return JBColor.GRAY; }
}
