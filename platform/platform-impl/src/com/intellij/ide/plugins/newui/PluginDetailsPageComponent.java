// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
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

/**
 * @author Alexander Lobas
 */
public class PluginDetailsPageComponent extends MultiPanel {
  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;

  private JBPanelWithEmptyText myEmptyPanel;

  private OpaquePanel myPanel;
  private JLabel myIconLabel;
  private final JEditorPane myNameComponent = createNameComponent();
  private final BaselinePanel myNameAndButtons = new BaselinePanel();
  private JButton myRestartButton;
  private JButton myInstallButton;
  private JButton myUpdateButton;
  private JButton myEnableDisableButton;
  private JBOptionButton myEnableDisableUninstallButton;
  private JButton myUninstallButton;
  private JComponent myErrorComponent;
  private JTextField myVersion;
  private JLabel myVersionSize;
  private TagPanel myTagPanel;
  private JLabel myDate;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel mySize;
  private LinkPanel myVendor;
  private LinkPanel myHomePage;
  private JEditorPane myDescriptionComponent;
  private OneLineProgressIndicator myIndicator;

  public IdeaPluginDescriptor myPlugin;
  private IdeaPluginDescriptor myUpdateDescriptor;

  public PluginDetailsPageComponent(@NotNull MyPluginModel pluginModel, @NotNull LinkListener<Object> searchListener, boolean marketplace) {
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
    myMarketplace = marketplace;
    createPluginPanel();
    select(1, true);
    setEmptyState(false);
  }

  @Override
  protected JComponent create(Integer key) {
    if (key == 0) {
      return myPanel;
    }
    if (key == 1) {
      myEmptyPanel = new JBPanelWithEmptyText();
      myEmptyPanel.setBorder(new CustomLineBorder(PluginManagerConfigurableNew.SEARCH_FIELD_BORDER_COLOR, JBUI.insets(1, 0, 0, 0)));
      myEmptyPanel.setOpaque(true);
      myEmptyPanel.setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
      return myEmptyPanel;
    }
    return super.create(key);
  }

  private void createPluginPanel() {
    myPanel = new OpaquePanel(new BorderLayout(0, JBUIScale.scale(32)), PluginManagerConfigurableNew.MAIN_BG_COLOR);
    myPanel.setBorder(new CustomLineBorder(new JBColor(0xC5C5C5, 0x515151), JBUI.insets(1, 0, 0, 0)) {
      @Override
      public Insets getBorderInsets(Component c) {
        return JBUI.insets(15, 20, 0, 0);
      }
    });

    createHeaderPanel().add(createCenterPanel());
    createBottomPanel();
  }

  @NotNull
  private JPanel createHeaderPanel() {
    JPanel header = new NonOpaquePanel(new BorderLayout(JBUIScale.scale(20), 0));
    header.setBorder(JBUI.Borders.emptyRight(20));
    myPanel.add(header, BorderLayout.NORTH);

    myIconLabel = new JLabel();
    myIconLabel.setVerticalAlignment(SwingConstants.TOP);
    myIconLabel.setOpaque(false);
    header.add(myIconLabel, BorderLayout.WEST);

    return header;
  }

  @NotNull
  private JPanel createCenterPanel() {
    int offset = PluginManagerConfigurableNew.offset5();
    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset));

    myNameAndButtons.setYOffset(JBUIScale.scale(3));
    myNameAndButtons.add(myNameComponent);
    createButtons();
    centerPanel.add(myNameAndButtons, VerticalLayout.FILL_HORIZONTAL);
    if (!myMarketplace) {
      myErrorComponent = ErrorComponent.create(centerPanel, VerticalLayout.FILL_HORIZONTAL);
    }
    createMetricsPanel(centerPanel);

    return centerPanel;
  }

  @NotNull
  private static JEditorPane createNameComponent() {
    JEditorPane editorPane = new JEditorPane() {
      JLabel myBaselineComponent;

      @Override
      public int getBaseline(int width, int height) {
        if (myBaselineComponent == null) {
          myBaselineComponent = new JLabel();
          myBaselineComponent.setFont(getFont());
        }
        myBaselineComponent.setText(getText());
        Dimension size = myBaselineComponent.getPreferredSize();
        return myBaselineComponent.getBaseline(size.width, size.height);
      }
    };

    ErrorComponent.convertToLabel(editorPane);
    editorPane.setEditorKit(UIUtil.getHTMLEditorKit());

    Font font = editorPane.getFont();
    if (font != null) {
      editorPane.setFont(font.deriveFont(Font.BOLD, 25));
    }

    return editorPane;
  }

  private void createButtons() {
    myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

    myNameAndButtons.addButtonComponent(myUpdateButton = new UpdateButton());
    myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, myUpdateDescriptor));

    myNameAndButtons.addButtonComponent(myInstallButton = new InstallButton(true));
    myInstallButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, null));

    myEnableDisableButton = new JButton();
    myEnableDisableButton.addActionListener(e -> changeEnableDisable());
    ColorButton.setWidth72(myEnableDisableButton);
    myNameAndButtons.addButtonComponent(myEnableDisableButton);

    AbstractAction enableDisableAction = new AbstractAction() {
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
    myNameAndButtons.addButtonComponent(myEnableDisableUninstallButton = new MyOptionButton(enableDisableAction, uninstallAction));

    myUninstallButton = new JButton("Uninstall");
    myUninstallButton.addActionListener(e -> doUninstall());
    ColorButton.setWidth72(myUninstallButton);
    myNameAndButtons.addButtonComponent(myUninstallButton);

    for (Component component : myNameAndButtons.getButtonComponents()) {
      component.setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
    }
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    // text field without horizontal margins
    myVersion = new JTextField();
    myVersion.putClientProperty("TextFieldWithoutMargins", Boolean.TRUE);
    myVersion.setEditable(false);
    myVersion.setFont(UIUtil.getLabelFont());
    PluginManagerConfigurableNew.installTiny(myVersion);
    myVersion.setBorder(null);
    myVersion.setOpaque(false);
    myVersion.setForeground(CellPluginComponent.GRAY_COLOR);
    myVersion.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        int caretPosition = myVersion.getCaretPosition();
        myVersion.setSelectionStart(caretPosition);
        myVersion.setSelectionEnd(caretPosition);
      }
    });

    myVersionSize = new JLabel();
    myVersionSize.setFont(UIUtil.getLabelFont());
    PluginManagerConfigurableNew.installTiny(myVersionSize);

    int offset = JBUIScale.scale(10);
    JPanel panel1 = new NonOpaquePanel(new TextHorizontalLayout(offset));
    centerPanel.add(panel1);
    if (myMarketplace) {
      myDownloads =
        GridCellPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Downloads, CellPluginComponent.GRAY_COLOR, false);

      myRating =
        GridCellPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Rating, CellPluginComponent.GRAY_COLOR, false);
    }
    myVendor = new LinkPanel(panel1, false, null, TextHorizontalLayout.FIX_LABEL);

    JPanel panel2 = new NonOpaquePanel(new TextHorizontalLayout(myMarketplace ? offset : JBUIScale.scale(7)) {
      @Override
      public void layoutContainer(Container parent) {
        super.layoutContainer(parent);
        if (myTagPanel != null && myTagPanel.isVisible()) {
          int baseline = myTagPanel.getBaseline(-1, -1);
          if (baseline != -1) {
            Rectangle versionBounds = myVersion.getBounds();
            Dimension versionSize = myVersion.getPreferredSize();
            int versionY = myTagPanel.getY() + baseline - myVersion.getBaseline(versionSize.width, versionSize.height);
            myVersion.setBounds(versionBounds.x, versionY, versionBounds.width, versionBounds.height);

            if (myDate.isVisible()) {
              Rectangle dateBounds = myDate.getBounds();
              Dimension dateSize = myDate.getPreferredSize();
              int dateY = myTagPanel.getY() + baseline - myDate.getBaseline(dateSize.width, dateSize.height);
              myDate.setBounds(dateBounds.x - JBUIScale.scale(4), dateY, dateBounds.width, dateBounds.height);
            }
          }
        }
      }
    });
    if (myMarketplace) {
      panel2.add(myTagPanel = new TagPanel(mySearchListener));
    }
    panel2.add(myVersion);
    myDate =
      GridCellPluginComponent.createRatingLabel(panel2, TextHorizontalLayout.FIX_LABEL, "", null, CellPluginComponent.GRAY_COLOR, true);
    centerPanel.add(panel2);
  }

  private void createBottomPanel() {
    JPanel bottomPanel =
      new OpaquePanel(new VerticalLayout(PluginManagerConfigurableNew.offset5()), PluginManagerConfigurableNew.MAIN_BG_COLOR);
    bottomPanel.setBorder(JBUI.Borders.empty(0, 0, 15, 20));

    JBScrollPane scrollPane = new JBScrollPane(bottomPanel);
    scrollPane.getVerticalScrollBar().setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    myPanel.add(scrollPane);

    myHomePage = new LinkPanel(bottomPanel, true, null, null);
    bottomPanel.add(new JLabel());

    myDescriptionComponent = new JEditorPane();
    HTMLEditorKit kit = UIUtil.getHTMLEditorKit();
    StyleSheet sheet = kit.getStyleSheet();
    sheet.addRule("ul {margin-left: 16px}"); // list-style-type: none;
    sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor()) + "}");
    myDescriptionComponent.setEditable(false);
    myDescriptionComponent.setOpaque(false);
    myDescriptionComponent.setBorder(null);
    myDescriptionComponent.setContentType("text/html");
    myDescriptionComponent.setEditorKit(kit);
    myDescriptionComponent.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    bottomPanel.add(myDescriptionComponent, JBUIScale.scale(700), -1);

    JLabel separator = new JLabel();
    separator.setBorder(JBUI.Borders.emptyTop(20));
    bottomPanel.add(separator);

    if (myMarketplace) {
      bottomPanel.add(mySize = new JLabel());
    }
  }

  public void showPlugin(@Nullable CellPluginComponent component, boolean multiSelection) {
    if (myIndicator != null) {
      MyPluginModel.removeProgress(myPlugin, myIndicator);
      hideProgress(false, false);
    }

    if (component == null) {
      myPlugin = myUpdateDescriptor = null;
      select(1, true);
      setEmptyState(multiSelection);
    }
    else {
      myPlugin = component.myPlugin;
      myUpdateDescriptor = ((NewListPluginComponent)component).myUpdateDescriptor;
      showPlugin();
      select(0, true);
    }
  }

  private void setEmptyState(boolean multiSelection) {
    StatusText text = myEmptyPanel.getEmptyText();
    text.clear();
    if (multiSelection) {
      text.setText("Several plugins selected.");
      text.appendSecondaryText("Select one plugin to preview plugin details.", StatusText.DEFAULT_ATTRIBUTES, null);
    }
    else {
      text.setText("Select plugin to preview details");
    }
  }

  private void showPlugin() {
    myNameComponent.setText("<html><span>" + myPlugin.getName() + "</span></html>");
    updateIcon();

    if (myMarketplace) {
      boolean installed = InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId());
      myRestartButton.setVisible(installed);

      myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
      myInstallButton.setVisible(!installed);

      myUpdateButton.setVisible(false);
      myEnableDisableButton.setVisible(false);
      myEnableDisableUninstallButton.setVisible(false);
      myUninstallButton.setVisible(false);
    }
    else {
      myInstallButton.setVisible(false);

      boolean restart = myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted();
      if (!restart) {
        InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
        PluginId id = myPlugin.getPluginId();
        restart = pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id);
      }

      if (restart) {
        myRestartButton.setVisible(true);
        myUpdateButton.setVisible(false);
        myEnableDisableButton.setVisible(false);
        myEnableDisableUninstallButton.setVisible(false);
        myUninstallButton.setVisible(false);
      }
      else {
        myRestartButton.setVisible(false);

        boolean bundled = myPlugin.isBundled();
        String title = myPluginModel.getEnabledTitle(myPlugin);
        boolean errors = myPluginModel.hasErrors(myPlugin);

        myUpdateButton.setVisible(myUpdateDescriptor != null && !errors);

        myEnableDisableButton.setVisible(bundled && !errors);
        myEnableDisableButton.setText(title);

        myEnableDisableUninstallButton.setVisible(!bundled && !errors);
        myEnableDisableUninstallButton.setText(title);

        myUninstallButton.setVisible(!bundled && errors);
      }

      updateEnableForNameAndIcon();
      updateErrors();
    }

    boolean bundled = myPlugin.isBundled() && !myPlugin.allowBundledUpdate();
    String version = bundled ? "bundled" : myPlugin.getVersion();
    if (myUpdateDescriptor != null) {
      version = myPlugin.getVersion() + " " + UIUtil.rightArrow() + " " + myUpdateDescriptor.getVersion();
    }

    myVersion.setText(version);
    myVersionSize.setText(version);
    myVersion
      .setPreferredSize(new Dimension(myVersionSize.getPreferredSize().width + JBUIScale.scale(4), myVersion.getPreferredSize().height));

    myVersion.setVisible(!StringUtil.isEmptyOrSpaces(version));

    if (myMarketplace) {
      myTagPanel.setTags(PluginManagerConfigurableNew.getTags(myPlugin));

      String rating = PluginManagerConfigurableNew.getRating(myPlugin);
      myRating.setText(rating);
      myRating.setVisible(rating != null);

      String downloads = PluginManagerConfigurableNew.getDownloads(myPlugin);
      myDownloads.setText(downloads);
      myDownloads.setVisible(downloads != null);

      String size = PluginManagerConfigurableNew.getSize(myPlugin);
      mySize.setText("Size: " + size);
      mySize.setVisible(!StringUtil.isEmptyOrSpaces(size));
    }

    String vendor = myPlugin.isBundled() ? null : StringUtil.trim(myPlugin.getVendor());
    if (StringUtil.isEmptyOrSpaces(vendor)) {
      myVendor.hide();
    }
    else {
      myVendor.show(vendor, () -> mySearchListener
        .linkSelected(null, "/vendor:" + (vendor.indexOf(' ') == -1 ? vendor : StringUtil.wrapWithDoubleQuote(vendor))));
    }

    if (bundled) {
      myHomePage.hide();
    }
    else {
      myHomePage.show("Plugin homepage", () -> BrowserUtil.browse("https://plugins.jetbrains.com/plugin/index?xmlId=" +
                                                                  URLUtil.encodeURIComponent(myPlugin.getPluginId().getIdString())));
    }

    String date = PluginManagerConfigurableNew.getLastUpdatedDate(myUpdateDescriptor == null ? myPlugin : myUpdateDescriptor);
    myDate.setText(date);
    myDate.setVisible(date != null);

    String description = getDescription();
    if (description != null) {
      myDescriptionComponent.setText(XmlStringUtil.wrapInHtml(description));
      if (myDescriptionComponent.getCaret() != null) {
        myDescriptionComponent.setCaretPosition(0);
      }
    }
    myDescriptionComponent.setVisible(description != null);

    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress();
    }
    else {
      fullRepaint();
    }
  }

  private void updateIcon() {
    boolean jb = PluginManagerConfigurableNew.isJBPlugin(myPlugin);
    boolean errors = myPluginModel.hasErrors(myPlugin);

    myIconLabel.setEnabled(myPlugin instanceof PluginNode || myPluginModel.isEnabled(myPlugin));
    myIconLabel.setIcon(PluginLogo.getIcon(myPlugin, true, jb, errors, false));
    myIconLabel.setDisabledIcon(PluginLogo.getIcon(myPlugin, true, jb, errors, true));
  }

  private void updateErrors() {
    boolean errors = myPluginModel.hasErrors(myPlugin);
    if (errors) {
      Ref<String> enableAction = new Ref<>();
      String message = myPluginModel.getErrorMessage(myPlugin, enableAction);
      ErrorComponent.show(myErrorComponent, message, enableAction.get(), enableAction.isNull() ? null : this::handleErrors);
    }
    myErrorComponent.setVisible(errors);
  }

  private void handleErrors() {
    myPluginModel.enableRequiredPlugins(myPlugin);
    updateIcon();
    updateEnabledState();
    fullRepaint();
  }

  public void showProgress() {
    myIndicator = new OneLineProgressIndicator(false);
    myIndicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false, false));
    myNameAndButtons.setProgressComponent(null, myIndicator.createBaselineWrapper());

    MyPluginModel.addProgress(myPlugin, myIndicator);

    fullRepaint();
  }

  private void fullRepaint() {
    doLayout();
    revalidate();
    repaint();
  }

  public void hideProgress(boolean success) {
    hideProgress(success, true);
  }

  private void hideProgress(boolean success, boolean repaint) {
    myIndicator = null;
    myNameAndButtons.removeProgressComponent();

    if (success) {
      enableRestart();
    }
    if (repaint) {
      fullRepaint();
    }
  }

  private void changeEnableDisable() {
    myPluginModel.changeEnableDisable(myPlugin);
    updateEnabledState();
  }

  private void updateEnableForNameAndIcon() {
    boolean enabled = myPluginModel.isEnabled(myPlugin);
    myNameComponent.setForeground(enabled ? null : ListPluginComponent.DisabledColor);
    myIconLabel.setEnabled(enabled);
  }

  public void updateEnabledState() {
    if (myMarketplace || myPlugin == null) {
      return;
    }

    updateIcon();
    updateEnableForNameAndIcon();
    updateErrors();

    boolean bundled = myPlugin.isBundled();
    boolean errors = myPluginModel.hasErrors(myPlugin);
    String title = myPluginModel.getEnabledTitle(myPlugin);

    myEnableDisableButton.setText(title);
    myEnableDisableUninstallButton.setText(title);
    myUpdateButton.setVisible(myUpdateDescriptor != null && !errors);
    myEnableDisableButton.setVisible(bundled && !errors);
    myEnableDisableUninstallButton.setVisible(!bundled && !errors);
    myUninstallButton.setVisible(!bundled && errors);

    fullRepaint();
  }

  private void doUninstall() {
    if (MyPluginModel.showUninstallDialog(myPlugin.getName(), 1)) {
      myPluginModel.doUninstall(this, myPlugin, null);
    }
  }

  public void enableRestart() {
    myInstallButton.setVisible(false);
    myUpdateButton.setVisible(false);
    myEnableDisableButton.setVisible(false);
    myEnableDisableUninstallButton.setVisible(false);
    myUninstallButton.setVisible(false);
    myRestartButton.setVisible(true);
  }

  @Nullable
  private String getDescription() {
    String description = myPlugin.getDescription();
    return StringUtil.isEmptyOrSpaces(description) ? null : description;
  }
}