/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package com.intellij.execution.process;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Roman Chernyatchik
 * @date Oct 17, 2007
 */
public class ColoredProcessHandler extends OSProcessHandler {
  public static final char TEXT_ATTRS_PREFIX_CH = '\u001B';
  public static final String TEXT_ATTRS_PREFIX = Character.toString(TEXT_ATTRS_PREFIX_CH) + "[";

  private Key myCurrentColor;
  @Nullable private final Charset myCharset;

  public static TextAttributes getByKey(final TextAttributesKey key){
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
  }

  // Registering
  // TODO use new Maia API in ConsoleViewContentType to apply ANSI color changes without
  // restarting RM / Idea + Ruby Plugin

  public ColoredProcessHandler(Process process, String commandLine) {
    this(process, commandLine, null);
  }

  public ColoredProcessHandler(final Process process,
                               final String commandLine,
                               @Nullable final Charset charset) {
    super(process, commandLine);
    myCharset = charset;
  }

  @Override
  public Charset getCharset() {
    if (myCharset != null) {
      return myCharset;
    }
    // Charset wan't specified - use default one
    return super.getCharset();
  }

  public final void notifyTextAvailable(final String text, final Key outputType) {
    if (outputType != ProcessOutputTypes.STDOUT) {
      textAvailable(text, outputType);
      return;
    }
    int pos = 0;
    while(true) {
      int macroPos = text.indexOf(TEXT_ATTRS_PREFIX, pos);
      if (macroPos < 0) break;
      if (pos != macroPos) {
        textAvailable(text.substring(pos, macroPos), getCurrentOutputAttributes());
      }
      int macroEndPos = text.indexOf('m', macroPos);
      if (macroEndPos < 0) break;
      final ColoredOutputTypeRegistry registry = ColoredOutputTypeRegistry.getInstance();
      final String colorAttribute = text.substring(macroPos, macroEndPos + 1);
      myCurrentColor = registry.getOutputKey(colorAttribute);
      pos = macroEndPos+1;
    }
    if (pos < text.length()) {
      textAvailable(text.substring(pos), getCurrentOutputAttributes());
    }
  }

  protected void textAvailable(final String text, final Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }

  private Key getCurrentOutputAttributes() {
    return myCurrentColor != null ? myCurrentColor : ProcessOutputTypes.STDOUT;
  }

}
