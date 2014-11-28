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
package com.intellij.util;

import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

public class ContentsUtil {
  public static void addOrReplaceContent(ContentManager manager, Content content, boolean select) {
    final String contentName = content.getDisplayName();

    Content[] contents = manager.getContents();
    Content oldContentFound = null;
    for(Content oldContent: contents) {
      if (!oldContent.isPinned() && oldContent.getDisplayName().equals(contentName)) {
        oldContentFound = oldContent;
        break;
      }
    }

    manager.addContent(content);
    if (oldContentFound != null) {
      manager.removeContent(oldContentFound, true);
    }
    if (select) {
      manager.setSelectedContent(content);
    }
  }

  public static void addContent(final ContentManager manager, final Content content, final boolean select) {
    manager.addContent(content);
    if (select) {
      manager.setSelectedContent(content);
    }
  }
}
