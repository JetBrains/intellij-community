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
package com.intellij.dvcs.push.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VcsLinkedText {

  private static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  @NotNull private String myTextBefore;
  @NotNull private String myTextAfter;
  @NotNull private String myHandledLink;

  @Nullable private final VcsLinkListener myLinkListener;

  public VcsLinkedText(@NotNull String text, @Nullable VcsLinkListener listener) {
    Matcher aMatcher = HREF_PATTERN.matcher(text);
    if (aMatcher.find()) {
      myTextBefore = text.substring(0, aMatcher.start());
      myHandledLink = aMatcher.group(2);
      myTextAfter = text.substring(aMatcher.end(), text.length());
    }
    else {
      myTextBefore = text;
      myHandledLink = "";
      myTextAfter = "";
    }
    myLinkListener = listener;
  }

  @NotNull
  public String getTextBefore() {
    return myTextBefore;
  }

  @NotNull
  public String getTextAfter() {
    return myTextAfter;
  }

  @NotNull
  public String getLinkText() {
    return myHandledLink;
  }

  public void hyperLinkActivate(@NotNull DefaultMutableTreeNode relatedNode) {
    if (myLinkListener != null) {
      myLinkListener.hyperlinkActivated(relatedNode);
    }
  }
}
