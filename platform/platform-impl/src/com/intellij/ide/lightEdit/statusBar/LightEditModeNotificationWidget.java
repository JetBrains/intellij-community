// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.actions.LightEditOpenFileInProjectAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBPoint;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Supplier;

public final class LightEditModeNotificationWidget implements CustomStatusBarWidget {
  private final PopupState<JPopupMenu> myPopupState = PopupState.forPopupMenu();

  public LightEditModeNotificationWidget() {
  }

  @Override
  public @NonNls @NotNull String ID() {
    return "light.edit.mode.notification";
  }

  @Override
  public JComponent getComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBag gc = new GridBag().nextLine().setDefaultFill(GridBagConstraints.VERTICAL).setDefaultWeightY(1.0);
    JBLabel label = new JBLabel(ApplicationBundle.message("light.edit.status.bar.notification.label.text"));
    panel.add(label, gc.next().insets(JBUI.insets(0, 7, 0, 5)));
    ActionLink actionLink = createActionLink();
    panel.add(actionLink, gc.next());
    panel.setOpaque(false);

    configureTooltip(label, actionLink);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(EditorColorsManager.TOPIC, scheme -> {
      configureTooltip(label, actionLink);
    });

    return panel;
  }

  private void configureTooltip(@NotNull JBLabel label, @NotNull ActionLink actionLink) {
    IdeTooltip tooltip = createTooltip(actionLink);
    IdeTooltipManager.getInstance().setCustomTooltip(label, tooltip);
    IdeTooltipManager.getInstance().setCustomTooltip(actionLink, tooltip);
  }

  private @NotNull ActionLink createActionLink() {
    ActionLink actionLink = new ActionLink();
    actionLink.setText(ApplicationBundle.message("light.edit.status.bar.notification.link.text"));
    actionLink.setDropDownLinkIcon();
    actionLink.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showPopupMenu(actionLink);
      }
    });
    return actionLink;
  }

  private @NotNull IdeTooltip createTooltip(@NotNull JComponent component) {
    IdeTooltip tooltip = new TooltipWithClickableLinks(component, getTooltipHtml(), new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        HelpManager.getInstance().invokeHelp("LightEdit_Mode");
      }
    }) {
      @Override
      public boolean canBeDismissedOnTimeout() {
        return false;
      }

      @Override
      protected boolean beforeShow() {
        return !myPopupState.isShowing();
      }
    };
    tooltip.setToCenter(false);
    tooltip.setToCenterIfSmall(false);
    // Unable to get rid of the tooltip pointer (https://youtrack.jetbrains.com/issue/IDEA-251569).
    // Let's position it between the label and the link.
    tooltip.setPoint(new JBPoint(-3, 11));
    return tooltip;
  }

  private static @NotNull @Nls String getTooltipHtml() {
    HtmlChunk.Element link = HtmlChunk.link("", ApplicationBundle.message("light.edit.status.bar.notification.tooltip.link.text"));
    link = link.child(HtmlChunk.tag("icon").attr("src", "AllIcons.Ide.External_link_arrow"));
    @NlsSafe String pTag = "<p>";
    String tooltipText = ApplicationBundle.message("light.edit.status.bar.notification.tooltip") + pTag + link;
    tooltipText = tooltipText.replace(pTag, HtmlChunk.tag("p").style("padding: " + JBUI.scale(3) + "px 0 0 0").toString());
    return tooltipText;
  }

  private void showPopupMenu(@NotNull JComponent actionLink) {
    if (!myPopupState.isRecentlyHidden()) {
      DataContext dataContext = CustomizedDataContext.withSnapshot(
        DataManager.getInstance().getDataContext(actionLink), sink -> {
          sink.set(CommonDataKeys.VIRTUAL_FILE,
                   LightEditService.getInstance().getSelectedFile());
        });
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
        ActionPlaces.STATUS_BAR_PLACE, createAccessFullIdeActionGroup());
      popupMenu.setDataContext(() -> dataContext);
      JPopupMenu menu = popupMenu.getComponent();
      myPopupState.prepareToShow(menu);
      JBPopupMenu.showAbove(actionLink, menu);
    }
  }

  private static @NotNull ActionGroup createAccessFullIdeActionGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    return new DefaultActionGroup(
      new LightEditDelegatingAction(new LightEditOpenFileInProjectAction(),
                                    ApplicationBundle.messagePointer("light.edit.open_current_file_in_project.text")),
      new Separator(),
      new LightEditDelegatingAction(actionManager.getAction("ManageRecentProjects"),
                                    ApplicationBundle.messagePointer("light.edit.open_recent_project.text")),
      new LightEditDelegatingAction(actionManager.getAction("NewProject"),
                                    ApplicationBundle.messagePointer("light.edit.create_new_project.text"))
    );
  }

  private static final class LightEditDelegatingAction extends DumbAwareAction implements LightEditCompatible {
    private final AnAction myDelegate;

    private LightEditDelegatingAction(@Nullable AnAction delegate, @NotNull Supplier<@Nls String> textSupplier) {
      super(textSupplier);
      myDelegate = delegate;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myDelegate == null) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      myDelegate.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return myDelegate == null ? ActionUpdateThread.BGT : myDelegate.getActionUpdateThread();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myDelegate != null) {
        myDelegate.actionPerformed(e);
      }
    }
  }
}
