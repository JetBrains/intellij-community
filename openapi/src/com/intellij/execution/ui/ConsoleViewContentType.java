/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;

import java.awt.*;
import java.util.Map;

/**
 * @author Alexey Kudravtsev
 */
public class ConsoleViewContentType {
  private final String myName;
  private final TextAttributes myTextAttributes;

  private static final TextAttributes NORMAL_OUTPUT_ATTRIBUTES = new TextAttributes();
  private static final TextAttributes ERROR_OUTPUT_ATTRIBUTES = new TextAttributes();
  private static final TextAttributes USER_INPUT_ATTRIBUTES = new TextAttributes();
  private static final TextAttributes SYSTEM_OUTPUT_ATTRIBUTES = new TextAttributes();

  private static final Map<Key, ConsoleViewContentType> ourRegisteredTypes = new HashMap<Key, ConsoleViewContentType>();

  public static final ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", NORMAL_OUTPUT_ATTRIBUTES);
  public static final ConsoleViewContentType ERROR_OUTPUT = new ConsoleViewContentType("ERROR_OUTPUT", ERROR_OUTPUT_ATTRIBUTES);
  public static final ConsoleViewContentType USER_INPUT = new ConsoleViewContentType("USER_OUTPUT", USER_INPUT_ATTRIBUTES);
  public static final ConsoleViewContentType SYSTEM_OUTPUT = new ConsoleViewContentType("SYSTEM_OUTPUT", SYSTEM_OUTPUT_ATTRIBUTES);

  static {
    NORMAL_OUTPUT_ATTRIBUTES.setForegroundColor(Color.black);
    ERROR_OUTPUT_ATTRIBUTES.setForegroundColor(new Color(128, 0, 0));
    USER_INPUT_ATTRIBUTES.setForegroundColor(new Color(0, 128, 0));
    USER_INPUT_ATTRIBUTES.setFontType(Font.ITALIC);
    SYSTEM_OUTPUT_ATTRIBUTES.setForegroundColor(new Color(0, 0, 128));

    ourRegisteredTypes.put(ProcessOutputTypes.SYSTEM, SYSTEM_OUTPUT);
    ourRegisteredTypes.put(ProcessOutputTypes.STDOUT, NORMAL_OUTPUT);
    ourRegisteredTypes.put(ProcessOutputTypes.STDERR, ERROR_OUTPUT);
  }

  protected ConsoleViewContentType(final String name, final TextAttributes textAttributes) {
    this.myName = name;
    myTextAttributes = textAttributes;
  }

  public String toString() {
    return myName;
  }

  public TextAttributes getAttributes() {
    return myTextAttributes;
  }

  public static void registerNewConsoleViewType(final Key processOutputType, final ConsoleViewContentType attributes) {
    ourRegisteredTypes.put(processOutputType, attributes);
  }

  public static ConsoleViewContentType getConsoleViewType(final Key processOutputType) {
    if (ourRegisteredTypes.containsKey(processOutputType)) {
      return ourRegisteredTypes.get(processOutputType);
    }
    else {
      return SYSTEM_OUTPUT;
    }
  }

}
