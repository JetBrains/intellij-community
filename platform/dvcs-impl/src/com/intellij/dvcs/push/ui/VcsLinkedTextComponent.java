// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VcsLinkedTextComponent extends JLabel {
  private static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");

  @NotNull private final @NlsSafe String myTextBefore;
  @NotNull private final @NlsSafe String myTextAfter;
  @NotNull private @NlsSafe String myHandledLink;

  @Nullable private final VcsLinkListener myLinkListener;
  private boolean mySelected;
  private boolean myUnderlined;
  private boolean myTransparent;

  public VcsLinkedTextComponent(@NotNull String text, @Nullable VcsLinkListener listener) {
    Matcher aMatcher = HREF_PATTERN.matcher(text);
    if (aMatcher.find()) {
      myTextBefore = text.substring(0, aMatcher.start());
      myHandledLink = aMatcher.group(2);
      myTextAfter = text.substring(aMatcher.end());
    }
    else {
      myTextBefore = text;
      myHandledLink = "";
      myTextAfter = "";
    }
    myLinkListener = listener;
  }

  public void updateLinkText(@NotNull @Nls String text) {
    myHandledLink = text;
  }

  public void fireOnClick(@NotNull DefaultMutableTreeNode relatedNode, @NotNull MouseEvent event) {
    if (myLinkListener != null) {
      myLinkListener.hyperlinkActivated(relatedNode, event);
    }
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    boolean isActive = mySelected || myUnderlined;
    SimpleTextAttributes linkTextAttributes = isActive ? SimpleTextAttributes.LINK_ATTRIBUTES : SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
    isActive = isActive || !myTransparent;
    SimpleTextAttributes wrappedTextAttributes = PushLogTreeUtil
      .addTransparencyIfNeeded(renderer, SimpleTextAttributes.REGULAR_ATTRIBUTES, isActive);
    if (!StringUtil.isEmptyOrSpaces(myTextBefore)) {
      renderer.append(myTextBefore, wrappedTextAttributes);
      renderer.append(" ");
    }
    if (!StringUtil.isEmptyOrSpaces(myHandledLink)) {
      renderer.append(myHandledLink, PushLogTreeUtil.addTransparencyIfNeeded(renderer, linkTextAttributes, isActive), this);
    }
    renderer.append(myTextAfter, wrappedTextAttributes);
  }

  public void setUnderlined(boolean underlined) {
    myUnderlined = underlined;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public void setTransparent(boolean transparent) {
    myTransparent = transparent;
  }

  @Override
  @NotNull
  public String getText() {
    return myTextBefore + myHandledLink + myTextAfter;
  }
}
