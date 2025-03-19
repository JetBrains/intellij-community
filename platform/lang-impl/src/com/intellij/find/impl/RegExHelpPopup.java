// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindUsagesCollector;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RegExHelpPopup extends JPanel {
  private static final Logger LOG = Logger.getInstance(RegExHelpPopup.class);

  public RegExHelpPopup() {
    setLayout(new BorderLayout());

    JEditorPane editorPane = new JEditorPane();
    editorPane.setEditable(false);
    editorPane.setEditorKit(HTMLEditorKitBuilder.simple());
    editorPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    editorPane.setBackground(HintUtil.getInformationColor());

    @NlsSafe String text;
    try (InputStream stream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("messages/RegExHelpPopup.html"))) {
      text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      LOG.error(e);
      text = LangBundle.message("text.failed.to.load.help.page", e.getMessage());
    }
    editorPane.setText(text.replace("LABEL_BACKGROUND", ColorUtil.toHtmlColor(UIUtil.getLabelBackground())));

    editorPane.addHyperlinkListener(e -> {
      if (HyperlinkEvent.EventType.ACTIVATED == e.getEventType()) BrowserUtil.browse(e.getURL());
    });

    editorPane.setCaretPosition(0);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(editorPane);
    scrollPane.setBorder(null);

    add(scrollPane, BorderLayout.CENTER);
  }

  public static LinkLabel<?> createRegExLink(@NotNull @NlsContexts.LinkLabel String title, @Nullable Component owner) {
    return createRegExLink(title, owner, null);
  }

  public static @NotNull LinkLabel<?> createRegExLink(@NotNull @NlsContexts.LinkLabel String title,
                                                      @Nullable Component owner,
                                                      @Nullable String place) {
    Runnable action = createRegExLinkRunnable(owner);
    return new LinkLabel<>(title, null, (aSource, aLinkData) -> {
      FindUsagesCollector.triggerRegexHelpClicked(place);
      action.run();
    });
  }

  public static @NotNull Runnable createRegExLinkRunnable(@Nullable Component owner) {
    return new Runnable() {
      JBPopup helpPopup;

      @Override
      public void run() {
        if (helpPopup != null && !helpPopup.isDisposed() && helpPopup.isVisible()) {
          return;
        }
        RegExHelpPopup content = new RegExHelpPopup();
        ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content);
        helpPopup = builder
          .setCancelOnClickOutside(false)
          .setBelongsToGlobalPopupStack(true)
          .setFocusable(true)
          .setRequestFocus(true)
          .setMovable(true)
          .setResizable(true)
          .setCancelOnOtherWindowOpen(false).setCancelButton(new MinimizeButton(LangBundle.message("tooltip.hide")))
          .setTitle(LangBundle.message("popup.title.regular.expressions.syntax"))
          .setDimensionServiceKey(null, "RegExHelpPopup", true).createPopup();
        Disposer.register(helpPopup, () -> destroyPopup());
        if (owner != null) {
          helpPopup.showInCenterOf(owner);
        }
        else {
          helpPopup.showInFocusCenter();
        }
      }

      private void destroyPopup() {
        helpPopup = null;
      }
    };
  }

  @Override
  public Dimension getPreferredSize() {
    return JBUI.size(600, 300);
  }
}
