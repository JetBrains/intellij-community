/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy;

import java.awt.*;
import java.util.EventListener;

// we're extending EventListener to be able to use EventDispatcher in TextWithMarkupProcessor
public interface TextWithMarkupBuilder extends EventListener {
  void init(Color defaultForeground, Color defaultBackground, String defaultFontFamily, int fontSize);
  boolean isOverflowed();
  void setFontFamily(String fontFamily);
  void setFontStyle(int fontStyle);
  void setForeground(Color foreground);
  void setBackground(Color background);
  void addTextFragment(CharSequence charSequence, int startOffset, int endOffset);
  void complete();
}
