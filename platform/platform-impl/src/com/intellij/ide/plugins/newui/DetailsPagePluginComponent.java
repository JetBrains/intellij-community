// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;

/**
 * @author Alexander Lobas
 */
public class DetailsPagePluginComponent extends OpaquePanel {
  private final MyPluginModel myPluginsModel;
  private final TagBuilder myTagBuilder;
  private final LinkListener<String> mySearchListener;
  public final IdeaPluginDescriptor myPlugin;
  private JLabel myNameComponent;
  private JLabel myIconLabel;
  private JButton myUpdateButton;
  private JButton myInstallButton;
  private JButton myEnableDisableButton;
  private RestartButton myRestartButton;
  private JBOptionButton myEnableDisableUninstallButton;
  private JPanel myButtonsPanel;
  private final JPanel myCenterPanel;
  private OneLineProgressIndicator myIndicator;

  public int backTabIndex;

  public DetailsPagePluginComponent(@NotNull MyPluginModel pluginsModel,
                                    @NotNull TagBuilder tagBuilder,
                                    @NotNull LinkListener<String> searchListener,
                                    @NotNull IdeaPluginDescriptor plugin,
                                    boolean update) {
    super(new BorderLayout(0, JBUI.scale(32)), PluginManagerConfigurableNew.MAIN_BG_COLOR);
    myPluginsModel = pluginsModel;
    myTagBuilder = tagBuilder;
    mySearchListener = searchListener;
    myPlugin = plugin;

    JPanel header = createHeaderPanel();
    myCenterPanel = createCenterPanel(update);
    header.add(myCenterPanel);

    if (!update) {
      createTagPanel();
    }
    createMetricsPanel();
    createErrorPanel();
    createProgressPanel(!update);
    createBottomPanel();

    setBorder(new CustomLineBorder(new JBColor(0xC5C5C5, 0x515151), JBUI.insets(1, 0, 0, 0)) {
      @Override
      public Insets getBorderInsets(Component c) {
        return JBUI.insets(15, 20, 0, 0);
      }
    });
  }

  @NotNull
  private JPanel createCenterPanel(boolean update) {
    int offset = PluginManagerConfigurableNew.offset5();
    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset));
    JPanel nameButtons = new NonOpaquePanel(new BorderLayout(offset, 0) {
      Component myVersion;

      @Override
      public void addLayoutComponent(Component comp, Object constraints) {
        super.addLayoutComponent(comp, constraints);
        if (comp != myNameComponent && comp != myButtonsPanel) {
          myVersion = comp;
        }
      }

      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        Insets insets = target.getInsets();
        int left = insets.left;
        int right = myButtonsPanel.getX() - offset;
        int parentWidth = right - left;
        int versionWidth = myVersion == null ? 0 : myVersion.getPreferredSize().width;
        int nameWidth = myNameComponent.getPreferredSize().width;
        int nameWithVersionWidth = nameWidth + versionWidth;
        if (versionWidth > 0) {
          nameWithVersionWidth += offset;
        }

        if (nameWithVersionWidth <= parentWidth) {
          myNameComponent.setToolTipText(null);
          return;
        }

        myNameComponent.setToolTipText(myNameComponent.getText());

        int top = insets.top;
        int bottom = target.getHeight() - insets.bottom;

        if (myVersion != null) {
          myVersion.setBounds(right - versionWidth, top, versionWidth, bottom - top);
          parentWidth -= versionWidth + offset;
        }

        myNameComponent.setBounds(left, top, parentWidth, bottom - top);
      }
    });

    boolean bundled = myPlugin.isBundled() && !myPlugin.allowBundledUpdate();

    if (bundled) {
      myNameComponent = new JLabel();
    }
    else {
      LinkComponent linkComponent = new LinkComponent();
      linkComponent.setPaintUnderline(false);
      //noinspection unchecked
      linkComponent.setListener((_0, _1) -> BrowserUtil.browse("https://plugins.jetbrains.com/plugin/index?xmlId=" +
                                                               URLUtil.encodeURIComponent(myPlugin.getPluginId().getIdString())), null);
      myNameComponent = linkComponent;
    }

    myNameComponent.setOpaque(false);
    myNameComponent.setText(myPlugin.getName());
    Font font = myNameComponent.getFont();
    if (font != null) {
      myNameComponent.setFont(font.deriveFont(Font.BOLD, 30));
    }
    if (!(myPlugin instanceof PluginNode) && !myPluginsModel.isEnabled(myPlugin)) {
      myNameComponent.setForeground(ListPluginComponent.DisabledColor);
    }

    nameButtons.add(myNameComponent, BorderLayout.WEST);
    nameButtons.add(myButtonsPanel = createButtons(update), BorderLayout.EAST);
    centerPanel.add(nameButtons, VerticalLayout.FILL_HORIZONTAL);

    String version = bundled ? "bundled" : myPlugin.getVersion();

    if (!StringUtil.isEmptyOrSpaces(version)) {
      if (!bundled) {
        version = "v" + version;
      }
      JTextField versionComponent = new JTextField(version);
      versionComponent.setEditable(false);
      versionComponent.setFont(UIUtil.getLabelFont());
      versionComponent.setBorder(null);
      versionComponent.setOpaque(false);
      versionComponent.setForeground(CellPluginComponent.GRAY_COLOR);
      versionComponent.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          int caretPosition = versionComponent.getCaretPosition();
          versionComponent.setSelectionStart(caretPosition);
          versionComponent.setSelectionEnd(caretPosition);
        }
      });
      nameButtons.add(versionComponent);

      int nameBaseline = myNameComponent.getBaseline(myNameComponent.getWidth(), myNameComponent.getHeight());
      int versionBaseline = versionComponent.getBaseline(versionComponent.getWidth(), versionComponent.getHeight());
      versionComponent.setBorder(JBUI.Borders.empty(nameBaseline - versionBaseline, 4, 0, 0));
    }

    return centerPanel;
  }

  @NotNull
  private JPanel createButtons(boolean update) {
    JPanel buttons = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
    buttons.setBorder(JBUI.Borders.emptyTop(1));

    InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
    PluginId id = myPlugin.getPluginId();

    if ((myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) ||
        pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id)) {
      buttons.add(myRestartButton = new RestartButton(myPluginsModel));
    }
    else if (update) {
      buttons.add(myUpdateButton = new UpdateButton());
    }
    else if (myPlugin instanceof PluginNode) {
      buttons.add(myInstallButton = new InstallButton(true));
      myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
    }
    else if (myPlugin.isBundled()) {
      myEnableDisableButton = new JButton(myPluginsModel.getEnabledTitle(myPlugin));
      myEnableDisableButton.addActionListener(e -> changeEnableDisable());
      ColorButton.setWidth72(myEnableDisableButton);
      buttons.add(myEnableDisableButton);
    }
    else {
      AbstractAction enableDisableAction = new AbstractAction(myPluginsModel.getEnabledTitle(myPlugin)) {
        @Override
        public void actionPerformed(ActionEvent e) {
          changeEnableDisable();
        }
      };
      AbstractAction uninstallAction = new AbstractAction("Uninstall") {
        @Override
        public void actionPerformed(ActionEvent e) {
          doUninstall();
        }
      };
      buttons.add(myEnableDisableUninstallButton = new MyOptionButton(enableDisableAction, uninstallAction));
    }

    for (Component component : UIUtil.uiChildren(buttons)) {
      component.setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
    }

    return buttons;
  }

  @NotNull
  private JPanel createHeaderPanel() {
    JPanel header = new NonOpaquePanel(new BorderLayout(JBUI.scale(20), 0));
    header.setBorder(JBUI.Borders.emptyRight(20));
    add(header, BorderLayout.NORTH);

    myIconLabel = new JLabel();
    updateIcon();
    myIconLabel.setVerticalAlignment(SwingConstants.TOP);
    myIconLabel.setOpaque(false);
    myIconLabel.setEnabled(myPlugin instanceof PluginNode || myPluginsModel.isEnabled(myPlugin));
    header.add(myIconLabel, BorderLayout.WEST);

    return header;
  }

  private void updateIcon() {
    boolean jb = PluginManagerConfigurableNew.isJBPlugin(myPlugin);
    boolean errors = myPluginsModel.hasErrors(myPlugin);

    myIconLabel.setIcon(PluginLogo.getIcon(myPlugin, true, jb, errors, false));
    myIconLabel.setDisabledIcon(PluginLogo.getIcon(myPlugin, true, jb, errors, true));
  }

  private void createTagPanel() {
    java.util.List<String> tags = PluginManagerConfigurableNew.getTags(myPlugin);

    if (!tags.isEmpty()) {
      NonOpaquePanel tagPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
      tagPanel.setBorder(JBUI.Borders.emptyTop(2));
      myCenterPanel.add(tagPanel);

      for (String tag : tags) {
        TagComponent component = myTagBuilder.createTagComponent(tag);
        //noinspection unchecked
        component.setListener(mySearchListener, SearchQueryParser.getTagQuery(tag));
        tagPanel.add(component);
      }
    }
  }

  private void createMetricsPanel() {
    if (!(myPlugin instanceof PluginNode)) {
      return;
    }

    String downloads = PluginManagerConfigurableNew.getDownloads(myPlugin);
    String date = PluginManagerConfigurableNew.getLastUpdatedDate(myPlugin);
    String rating = PluginManagerConfigurableNew.getRating(myPlugin);

    if (downloads != null || date != null || rating != null) {
      JPanel metrics = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(20)));
      metrics.setBorder(JBUI.Borders.emptyTop(3));
      myCenterPanel.add(metrics);

      if (date != null) {
        createRatingLabel(metrics, date, AllIcons.Plugins.Updated, CellPluginComponent.GRAY_COLOR);
      }
      if (downloads != null) {
        createRatingLabel(metrics, downloads, AllIcons.Plugins.Downloads, CellPluginComponent.GRAY_COLOR);
      }

      if (rating != null) {
        RatesPanel ratesPanel = new RatesPanel();
        ratesPanel.setRate(rating);
        metrics.add(PluginManagerConfigurableNew.installTiny(ratesPanel));
      }
    }
  }

  private static void createRatingLabel(@NotNull JPanel panel, @NotNull String text, @NotNull Icon icon, @NotNull Color color) {
    JLabel label = new JLabel(text, icon, SwingConstants.CENTER);
    label.setOpaque(false);
    label.setForeground(color);
    panel.add(PluginManagerConfigurableNew.installTiny(label));
  }

  private void createErrorPanel() {
    if (myPluginsModel.hasErrors(myPlugin)) {
      int offset = JBUI.scale(8);
      JPanel errorPanel = new NonOpaquePanel(new HorizontalLayout(offset) {
        @Override
        public void layoutContainer(Container parent) {
          super.layoutContainer(parent);
          if (parent.getComponentCount() != 2) {
            return;
          }

          Component message = parent.getComponent(0);
          Component action = parent.getComponent(1);
          int actionWidth = action.getPreferredSize().width;
          int width = message.getPreferredSize().width + offset + actionWidth;
          Insets insets = parent.getInsets();
          int parentWidth = parent.getWidth() - insets.left - insets.right;

          if (width <= parentWidth) {
            return;
          }

          int right = parent.getWidth() - insets.right;
          action.setLocation(right - actionWidth, action.getY());
          right -= actionWidth + offset;
          message.setBounds(insets.left, message.getY(), right - insets.left, message.getHeight());
        }
      });
      errorPanel.setBorder(JBUI.Borders.emptyTop(15));
      myCenterPanel.add(errorPanel);

      JLabel errorMessage = new JLabel();
      errorMessage.setForeground(DialogWrapper.ERROR_FOREGROUND_COLOR);
      errorMessage.setOpaque(false);
      errorPanel.add(errorMessage);

      Ref<Boolean> enableAction = new Ref<>();
      errorMessage.setText(PluginManagerConfigurableNew.getErrorMessage(myPluginsModel, myPlugin, enableAction));

      if (!enableAction.isNull()) {
        LinkLabel<Object> errorAction = new LinkLabel<>("Enable", null);
        errorAction.setOpaque(false);
        errorPanel.add(errorAction);

        errorAction.setListener((aSource, aLinkData) -> {
          myPluginsModel.enableRequiredPlugins(myPlugin);

          myCenterPanel.remove(errorPanel);
          createErrorPanel();
          myCenterPanel.doLayout();

          updateIcon();
          updateEnabledState();

          doLayout();
          revalidate();
          repaint();
        }, null);
      }
    }
  }

  private void createProgressPanel(boolean install) {
    JButton button = myInstallButton == null ? myUpdateButton : myInstallButton;
    if (button == null) {
      return;
    }

    button.addActionListener(e -> myPluginsModel.installOrUpdatePlugin(myPlugin, install));

    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress(false);
    }
  }

  public void showProgress() {
    showProgress(true);
  }

  private void showProgress(boolean repaint) {
    myIndicator = new OneLineProgressIndicator();
    myIndicator.setCancelRunnable(() -> myPluginsModel.finishInstall(myPlugin, false, false));

    myButtonsPanel.remove(myInstallButton == null ? myUpdateButton : myInstallButton);
    if (myEnableDisableButton != null) {
      myButtonsPanel.remove(myEnableDisableButton);
    }
    if (myEnableDisableUninstallButton != null) {
      myButtonsPanel.remove(myEnableDisableUninstallButton);
    }
    myButtonsPanel.doLayout();
    myCenterPanel.add(myIndicator.getComponent());

    myPluginsModel.addProgress(myPlugin, myIndicator);

    if (repaint) {
      doLayout();
      revalidate();
      repaint();
    }
  }

  public void hideProgress(boolean success) {
    assert myIndicator != null;
    myCenterPanel.remove(myIndicator.getComponent());
    myIndicator = null;

    if (success) {
      changeInstallOrUpdateToRestart();
    }
    else {
      myButtonsPanel.add(myInstallButton == null ? myUpdateButton : myInstallButton);
      if (myEnableDisableButton != null) {
        myButtonsPanel.add(myEnableDisableButton);
      }
      if (myEnableDisableUninstallButton != null) {
        myButtonsPanel.add(myEnableDisableUninstallButton);
      }

      doLayout();
      revalidate();
      repaint();
    }
  }

  public void close() {
    if (myIndicator != null) {
      myPluginsModel.removeProgress(myPlugin, myIndicator);
      myIndicator = null;
    }
  }

  private void createBottomPanel() {
    String description = getDescriptionAndChangeNotes();
    String vendor = myPlugin.isBundled() ? null : myPlugin.getVendor();
    String size = myPlugin instanceof PluginNode ? ((PluginNode)myPlugin).getSize() : null;

    if (!StringUtil.isEmptyOrSpaces(description) || !StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
      JPanel bottomPanel =
        new OpaquePanel(new VerticalLayout(PluginManagerConfigurableNew.offset5()), PluginManagerConfigurableNew.MAIN_BG_COLOR);
      bottomPanel.setBorder(JBUI.Borders.emptyBottom(15));

      JBScrollPane scrollPane = new JBScrollPane(bottomPanel);
      scrollPane.getVerticalScrollBar().setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(null);
      add(scrollPane);

      if (!StringUtil.isEmptyOrSpaces(description)) {
        JEditorPane descriptionComponent = new JEditorPane();
        HTMLEditorKit kit = UIUtil.getHTMLEditorKit();
        StyleSheet sheet = kit.getStyleSheet();
        sheet.addRule("ul {margin-left: 16px}"); // list-style-type: none;
        sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor()) + "}");
        descriptionComponent.setEditable(false);
        descriptionComponent.setOpaque(false);
        descriptionComponent.setBorder(null);
        descriptionComponent.setContentType("text/html");
        descriptionComponent.setEditorKit(kit);
        descriptionComponent.setText(XmlStringUtil.wrapInHtml(description));
        descriptionComponent.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

        if (descriptionComponent.getCaret() != null) {
          descriptionComponent.setCaretPosition(0);
        }

        bottomPanel.add(descriptionComponent, JBUI.scale(700), -1);
      }

      if (!StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
        java.util.List<JLabel> labels = new ArrayList<>();

        if (!StringUtil.isEmptyOrSpaces(vendor)) {
          JPanel linePanel = createLabelsPanel(bottomPanel, labels, "Vendor:", vendor, myPlugin.getVendorUrl());
          linePanel.setBorder(JBUI.Borders.emptyTop(20));
        }

        if (!StringUtil.isEmptyOrSpaces(size)) {
          createLabelsPanel(bottomPanel, labels, "Size:", PluginManagerColumnInfo.getFormattedSize(size), null);
        }

        if (labels.size() > 1) {
          int width = 0;
          for (JLabel label : labels) {
            width = Math.max(width, label.getPreferredSize().width);
          }
          for (JLabel label : labels) {
            label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
          }
        }
      }
    }
  }

  @NotNull
  private static JPanel createLabelsPanel(@NotNull JPanel parent,
                                          @NotNull java.util.List<? super JLabel> labels,
                                          @NotNull String labelText,
                                          @NotNull String text,
                                          @Nullable String link) {
    JPanel linePanel = new NonOpaquePanel(new HorizontalLayout(5));
    parent.add(linePanel);

    JLabel label = new JLabel(labelText);
    linePanel.add(label);
    labels.add(label);

    if (StringUtil.isEmptyOrSpaces(link)) {
      linePanel.add(new JLabel(text));
    }
    else {
      LinkLabel<Object> linkLabel = new LinkLabel<>(text, AllIcons.Ide.External_link_arrow, (_0, _1) -> BrowserUtil.browse(link));
      linkLabel.setIconTextGap(0);
      linkLabel.setHorizontalTextPosition(SwingConstants.LEFT);
      linePanel.add(linkLabel);
    }

    return linePanel;
  }

  @Nullable
  private String getDescriptionAndChangeNotes() {
    StringBuilder result = new StringBuilder();

    String description = myPlugin.getDescription();
    if (!StringUtil.isEmptyOrSpaces(description)) {
      result.append(description);
    }

    String notes = myPlugin.getChangeNotes();
    if (!StringUtil.isEmptyOrSpaces(notes)) {
      result.append("<h4>Change Notes</h4>").append(notes);
    }

    return result.length() > 0 ? result.toString() : null;
  }

  private void changeEnableDisable() {
    myPluginsModel.changeEnableDisable(myPlugin);
    updateEnabledState();
  }

  private void updateEnabledState() {
    if (!(myPlugin instanceof PluginNode)) {
      boolean enabled = myPluginsModel.isEnabled(myPlugin);
      myNameComponent.setForeground(enabled ? null : ListPluginComponent.DisabledColor);
      myIconLabel.setEnabled(enabled);
    }

    String title = myPluginsModel.getEnabledTitle(myPlugin);
    if (myEnableDisableButton != null) {
      myEnableDisableButton.setText(title);
    }
    if (myEnableDisableUninstallButton != null) {
      myEnableDisableUninstallButton.setText(title);
    }
  }

  private void doUninstall() {
    if (myPluginsModel.showUninstallDialog(myPlugin.getName(), 1)) {
      myPluginsModel.doUninstall(this, myPlugin, this::changeInstallOrUpdateToRestart);
    }
  }

  private void changeInstallOrUpdateToRestart() {
    if (myEnableDisableUninstallButton != null) {
      myButtonsPanel.remove(myEnableDisableUninstallButton);
      myEnableDisableUninstallButton = null;
    }

    if (myUpdateButton != null) {
      myButtonsPanel.remove(myUpdateButton);
      myUpdateButton = null;
    }
    if (myInstallButton != null) {
      myButtonsPanel.remove(myInstallButton);
      myInstallButton = null;
    }
    if (myRestartButton == null) {
      myButtonsPanel.add(myRestartButton = new RestartButton(myPluginsModel));
    }

    doLayout();
    revalidate();
    repaint();
  }
}