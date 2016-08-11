/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.ui;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author Alexey Kudravtsev
 */
public class ConsoleViewContentType {
  private final String myName;
  private final TextAttributes myTextAttributes;
  private final TextAttributesKey myTextAttributesKey;

  private static final Map<Key, ConsoleViewContentType> ourRegisteredTypes = new HashMap<>();

  public static final ColorKey CONSOLE_BACKGROUND_KEY = ColorKey.createColorKey("CONSOLE_BACKGROUND_KEY");

  public static final TextAttributesKey NORMAL_OUTPUT_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_NORMAL_OUTPUT");
  public static final TextAttributesKey LOG_WARNING_OUTPUT_KEY = TextAttributesKey.createTextAttributesKey("LOG_WARNING_OUTPUT");
  public static final TextAttributesKey ERROR_OUTPUT_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_ERROR_OUTPUT");
  public static final TextAttributesKey LOG_ERROR_OUTPUT_KEY = TextAttributesKey.createTextAttributesKey("LOG_ERROR_OUTPUT");
  public static final TextAttributesKey LOG_EXPIRED_ENTRY = TextAttributesKey.createTextAttributesKey("LOG_EXPIRED_ENTRY");
  public static final TextAttributesKey USER_INPUT_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_USER_INPUT");
  public static final TextAttributesKey SYSTEM_OUTPUT_KEY = TextAttributesKey.createTextAttributesKey("CONSOLE_SYSTEM_OUTPUT");

  public static final ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", NORMAL_OUTPUT_KEY);
  public static final ConsoleViewContentType ERROR_OUTPUT = new ConsoleViewContentType("ERROR_OUTPUT", ERROR_OUTPUT_KEY);
  public static final ConsoleViewContentType USER_INPUT = new ConsoleViewContentType("USER_OUTPUT", USER_INPUT_KEY);
  public static final ConsoleViewContentType SYSTEM_OUTPUT = new ConsoleViewContentType("SYSTEM_OUTPUT", SYSTEM_OUTPUT_KEY);

  public static final ConsoleViewContentType[] OUTPUT_TYPES = {NORMAL_OUTPUT, ERROR_OUTPUT, USER_INPUT, SYSTEM_OUTPUT};

  static {
    ourRegisteredTypes.put(ProcessOutputTypes.SYSTEM, SYSTEM_OUTPUT);
    ourRegisteredTypes.put(ProcessOutputTypes.STDOUT, NORMAL_OUTPUT);
    ourRegisteredTypes.put(ProcessOutputTypes.STDERR, ERROR_OUTPUT);
  }

  public ConsoleViewContentType(@NonNls final String name, final TextAttributes textAttributes) {
    myName = name;
    myTextAttributes = textAttributes;
    myTextAttributesKey = null;
  }

  public ConsoleViewContentType(@NonNls String name, TextAttributesKey textAttributesKey) {
    myName = name;
    myTextAttributes = null;
    myTextAttributesKey = textAttributesKey;
  }

  public String toString() {
    return myName;
  }

  public TextAttributes getAttributes() {
    if (myTextAttributesKey != null) {
      return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(myTextAttributesKey);
    }
    return myTextAttributes;
  }

  public static ConsoleViewContentType registerNewConsoleViewType(@NotNull Key key, @NotNull TextAttributesKey attributesKey) {
    ConsoleViewContentType type = new ConsoleViewContentType(key.toString(), attributesKey);
    registerNewConsoleViewType(key, type);
    return type;
  }

  public static synchronized void registerNewConsoleViewType(final Key processOutputType, final ConsoleViewContentType attributes) {
    ourRegisteredTypes.put(processOutputType, attributes);
  }

  public static synchronized ConsoleViewContentType getConsoleViewType(final Key processOutputType) {
    if (ourRegisteredTypes.containsKey(processOutputType)) {
      return ourRegisteredTypes.get(processOutputType);
    }
    else {
      return SYSTEM_OUTPUT;
    }
  }

  public static synchronized Collection<ConsoleViewContentType> getRegisteredTypes() {
    return ourRegisteredTypes.values();
  }

}
