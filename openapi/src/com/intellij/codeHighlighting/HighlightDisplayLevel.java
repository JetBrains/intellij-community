package com.intellij.codeHighlighting;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Map;

public class HighlightDisplayLevel {
  private static Map<String, HighlightDisplayLevel> ourMap = new HashMap<String, HighlightDisplayLevel>();

  public static final HighlightDisplayLevel ERROR = new HighlightDisplayLevel("ERROR", IconLoader.getIcon("/general/errorsFound.png"));
  public static final HighlightDisplayLevel WARNING = new HighlightDisplayLevel("WARNING",
                                                                                IconLoader.getIcon("/general/warningsFound.png"));
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel("DO_NOT_SHOW",
                                                                                    IconLoader.getIcon("/general/errorsOK.png"));

  private final String myName;
  private final Icon myIcon;

  public static HighlightDisplayLevel find(String name) {
    return ourMap.get(name);
  }

  private HighlightDisplayLevel(String name, Icon icon) {
    myName = name;
    myIcon = icon;
    ourMap.put(myName, this);
  }

  public String toString() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

}
