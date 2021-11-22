// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EventListeners;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.function.Consumer;

@Internal
public final class DocumentationLinkHandler {

  private final @NotNull DocumentationEditorPane myEditorPane;
  private final @NotNull Consumer<? super @NotNull String> myUrlConsumer;

  private int myHighlightedLink;

  private DocumentationLinkHandler(
    @NotNull DocumentationEditorPane pane,
    @NotNull Consumer<? super @NotNull String> urlConsumer
  ) {
    myEditorPane = pane;
    myUrlConsumer = urlConsumer;
    myHighlightedLink = -1;
  }

  @RequiresEdt
  public int getHighlightedLink() {
    return myHighlightedLink;
  }

  @RequiresEdt
  public void highlightLink(int n) {
    myHighlightedLink = n;
    myEditorPane.highlightLink(n);
  }

  private final class PreviousLinkAction extends AnAction implements ActionToIgnore {

    PreviousLinkAction() {
      setShortcutSet(CustomShortcutSet.fromString("shift TAB"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int linkCount = myEditorPane.getLinkCount();
      if (linkCount <= 0) return;
      highlightLink(myHighlightedLink < 0 ? (linkCount - 1) : (myHighlightedLink + linkCount - 1) % linkCount);
    }
  }

  private final class NextLinkAction extends AnAction implements ActionToIgnore {

    NextLinkAction() {
      setShortcutSet(CustomShortcutSet.fromString("TAB"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int linkCount = myEditorPane.getLinkCount();
      if (linkCount <= 0) return;
      highlightLink((myHighlightedLink + 1) % linkCount);
    }
  }

  private final class ActivateLinkAction extends AnAction implements ActionToIgnore {

    ActivateLinkAction() {
      setShortcutSet(CustomShortcutSet.fromString("ENTER"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String href = myEditorPane.getLinkHref(myHighlightedLink);
      if (href != null) {
        myUrlConsumer.accept(href);
      }
    }
  }

  public @Unmodifiable @NotNull List<? extends @NotNull AnAction> createLinkActions() {
    return List.of(new PreviousLinkAction(), new NextLinkAction(), new ActivateLinkAction());
  }

  public static @NotNull DocumentationLinkHandler createAndRegister(
    @NotNull DocumentationEditorPane pane,
    @NotNull Disposable parent,
    @NotNull Consumer<? super @NotNull String> urlConsumer
  ) {
    EventListeners.addHyperLinkListener(pane, parent, e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        urlConsumer.accept(e.getDescription());
      }
    });
    return new DocumentationLinkHandler(pane, urlConsumer);
  }
}
