// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.RatesPanel;
import com.intellij.ide.plugins.marketplace.PluginReviewComment;
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

@ApiStatus.Internal
public final class ReviewCommentComponent extends JPanel {
  private final String myPluginId;
  private final String myCommendId;
  private final JComponent myMoreButton;
  private EventHandler.SelectionType myState;
  private boolean myShowPopup;

  public ReviewCommentComponent(@NotNull PluginReviewComment comment) {
    myPluginId = comment.getPlugin().getLink();
    myCommendId = comment.getId();
    setLayout(new BorderLayout());
    setOpaque(true);
    setBorder(JBUI.Borders.empty(6, 16));

    JPanel topWrapper = new NonOpaquePanel(new BorderLayout());
    topWrapper.setBorder(JBUI.Borders.emptyBottom(7));
    add(topWrapper, BorderLayout.NORTH);

    JPanel topPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(12)));
    topWrapper.add(topPanel, BorderLayout.WEST);

    JLabel name = new JLabel(comment.getAuthor().getName());
    name.setForeground(ListPluginComponent.GRAY_COLOR);
    topPanel.add(name);

    long dateValue = comment.getDate();
    if (dateValue > 0) {
      JLabel date = new JLabel(DateFormatUtil.formatPrettyDate(dateValue));
      date.setForeground(ListPluginComponent.GRAY_COLOR);
      topPanel.add(date);
    }

    int rating = comment.getRating();
    if (rating > 0) {
      RatesPanel panel = new RatesPanel();
      topPanel.add(panel);
      panel.setRate(Integer.toString(rating));
    }

    myMoreButton = createMoreAction();
    Wrapper moreWrapper = new Wrapper(myMoreButton);
    moreWrapper.setPreferredSize(moreWrapper.getPreferredSize());
    myMoreButton.setVisible(false);
    topWrapper.add(moreWrapper, BorderLayout.EAST);

    JEditorPane editorPane = PluginDetailsPageComponent.createDescriptionComponent(null);
    add(editorPane);
    if (editorPane.getCaret() != null) {
      editorPane.setCaretPosition(0);
    }
    editorPane.setText(XmlStringUtil.wrapInHtml(comment.getComment()));

    setState(EventHandler.SelectionType.NONE);
  }

  private @NotNull JComponent createMoreAction() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.getTemplatePresentation().setPopupGroup(true);
    group.getTemplatePresentation().setIcon(AllIcons.Actions.More);
    group.getTemplatePresentation().putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true);
    group.add(new DumbAwareAction(IdeBundle.message("plugins.review.action.copy.link.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String url = MarketplaceUrls.getPluginManagerUrl() + "/" +
                     myPluginId + "/reviews#review=" + URLUtil.encodeURIComponent(myCommendId);

        CopyPasteManager.getInstance().setContents(new StringSelection(url));
      }
    });

    return new ActionButton(group, null, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      @Override
      protected @NotNull JBPopup createAndShowActionGroupPopup(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
        myShowPopup = true;
        JBPopup popup = super.createAndShowActionGroupPopup(actionGroup, event);
        popup.addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            myShowPopup = false;
            ApplicationManager.getApplication().invokeLater(() -> myMoreButton.setVisible(myState == EventHandler.SelectionType.HOVER));
          }
        });
        return popup;
      }
    };
  }

  public @NotNull EventHandler.SelectionType getState() {
    return myState;
  }

  public void setState(@NotNull EventHandler.SelectionType state) {
    if (state == EventHandler.SelectionType.NONE) {
      setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    }
    else if (state == EventHandler.SelectionType.HOVER) {
      setBackground(ListPluginComponent.HOVER_COLOR);
    }
    //myMoreButton.setVisible(state == EventHandler.SelectionType.HOVER || myShowPopup); // TODO: rollback after adding a few more actions
    myState = state;
  }
}