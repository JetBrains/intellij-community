// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginDetailsPageComponent extends MultiPanel {
  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;

  @NotNull
  private final AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.BigCentered(IdeBundle.message("progress.text.loading"));

  private static final String MARKETPLACE_LINK = "https://plugins.jetbrains.com/plugin/index?xmlId=";


  private JBPanelWithEmptyText myEmptyPanel;


  private OpaquePanel myPanel;
  private JLabel myIconLabel;
  private final JEditorPane myNameComponent = createNameComponent();
  private final BaselinePanel myNameAndButtons = new BaselinePanel();
  private JButton myRestartButton;
  private InstallButton myInstallButton;
  private JButton myUpdateButton;
  private JButton myEnableDisableButton;
  private JBOptionButton myEnableDisableUninstallButton;
  private JComponent myErrorComponent;
  private JTextField myVersion;
  private JLabel myVersionSize;
  private TagPanel myTagPanel;
  private JLabel myDate;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel mySize;
  private LinkPanel myVendor;
  private final LicensePanel myLicensePanel = new LicensePanel(false);
  private LinkPanel myHomePage;
  private JBScrollPane myBottomScrollPane;
  private JEditorPane myDescriptionComponent;
  private String myDescription;
  private ChangeNotesPanel myChangeNotesPanel;
  private OneLineProgressIndicator myIndicator;

  public IdeaPluginDescriptor myPlugin;
  private IdeaPluginDescriptor myUpdateDescriptor;
  private AbstractAction myEnableDisableAction;

  private ListPluginComponent myShowComponent;

  public PluginDetailsPageComponent(@NotNull MyPluginModel pluginModel, @NotNull LinkListener<Object> searchListener, boolean marketplace) {
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
    myMarketplace = marketplace;
    createPluginPanel();
    select(1, true);
    setEmptyState(EmptyState.NONE_SELECTED);
  }

  public boolean isShowingPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    return myPlugin != null && myPlugin.getPluginId().equals(pluginDescriptor.getPluginId());
  }

  @Override
  protected JComponent create(Integer key) {
    if (key == 0) {
      return myPanel;
    }
    if (key == 1) {
      if (myEmptyPanel == null) {
        myEmptyPanel = new JBPanelWithEmptyText();
        myEmptyPanel.setBorder(new CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insets(1, 0, 0, 0)));
        myEmptyPanel.setOpaque(true);
        myEmptyPanel.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
        myLoadingIcon.setOpaque(true);
        myLoadingIcon.setPaintPassiveIcon(false);
        myEmptyPanel.add(myLoadingIcon);
      }
      return myEmptyPanel;
    }
    return super.create(key);
  }

  private void createPluginPanel() {
    myPanel = new OpaquePanel(new BorderLayout(0, JBUIScale.scale(32)), PluginManagerConfigurable.MAIN_BG_COLOR);
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
    int offset = PluginManagerConfigurable.offset5();
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
    myUpdateButton.addActionListener(
      e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, myUpdateDescriptor, ModalityState.stateForComponent(myUpdateButton)));

    myNameAndButtons.addButtonComponent(myInstallButton = new InstallButton(true));
    myInstallButton
      .addActionListener(e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, null, ModalityState.stateForComponent(myInstallButton)));

    myEnableDisableButton = new JButton();
    myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
    ColorButton.setWidth72(myEnableDisableButton);
    myNameAndButtons.addButtonComponent(myEnableDisableButton);

    myEnableDisableAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myPluginModel.changeEnableDisable(myPlugin);
      }
    };
    AbstractAction uninstallAction = new AbstractAction(IdeBundle.message("plugins.configurable.uninstall.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        doUninstall();
      }
    };
    myNameAndButtons.addButtonComponent(myEnableDisableUninstallButton = new MyOptionButton(myEnableDisableAction, uninstallAction));

    for (Component component : myNameAndButtons.getButtonComponents()) {
      component.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    }
  }

  public void setOnlyUpdateMode() {
    myNameAndButtons.removeButtons();
    myPanel.setBorder(JBUI.Borders.empty(15, 20, 0, 0));
    myEmptyPanel.setBorder(null);
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    // text field without horizontal margins
    myVersion = new JTextField();
    myVersion.putClientProperty("TextFieldWithoutMargins", Boolean.TRUE);
    myVersion.setEditable(false);
    myVersion.setFont(UIUtil.getLabelFont());
    PluginManagerConfigurable.setTinyFont(myVersion);
    myVersion.setBorder(null);
    myVersion.setOpaque(false);
    myVersion.setForeground(ListPluginComponent.GRAY_COLOR);
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
    PluginManagerConfigurable.setTinyFont(myVersionSize);

    int offset = JBUIScale.scale(10);
    JPanel panel1 = new NonOpaquePanel(new TextHorizontalLayout(offset));
    centerPanel.add(panel1);
    if (myMarketplace) {
      myDownloads =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Downloads, ListPluginComponent.GRAY_COLOR, false);

      myRating =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Rating, ListPluginComponent.GRAY_COLOR, false);
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
    panel2.add(myTagPanel = new TagPanel(mySearchListener));
    panel2.add(myVersion);
    myDate =
      ListPluginComponent.createRatingLabel(panel2, TextHorizontalLayout.FIX_LABEL, "", null, ListPluginComponent.GRAY_COLOR, true);
    centerPanel.add(panel2);
  }

  private void createBottomPanel() {
    JPanel bottomPanel =
      new OpaquePanel(new VerticalLayout(PluginManagerConfigurable.offset5()), PluginManagerConfigurable.MAIN_BG_COLOR);
    bottomPanel.setBorder(JBUI.Borders.empty(0, 0, 15, 20));

    myBottomScrollPane = new JBScrollPane(bottomPanel);
    myBottomScrollPane.getVerticalScrollBar().setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    myBottomScrollPane.setBorder(null);
    myPanel.add(myBottomScrollPane);

    bottomPanel.add(myLicensePanel);
    myLicensePanel.setBorder(JBUI.Borders.emptyBottom(20));

    myHomePage = new LinkPanel(bottomPanel, true, null, null);
    bottomPanel.add(new JLabel());

    Object constraints = JBUIScale.scale(700);
    bottomPanel.add(myDescriptionComponent = createDescriptionComponent(view -> {
      float width = view.getPreferredSpan(View.X_AXIS);
      if (width < 0 || width > myBottomScrollPane.getWidth()) {
        myBottomScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
      }
    }), constraints);
    myChangeNotesPanel = new ChangeNotesPanel(bottomPanel, constraints, myDescriptionComponent);

    JLabel separator = new JLabel();
    separator.setBorder(JBUI.Borders.emptyTop(20));
    bottomPanel.add(separator);

    if (myMarketplace) {
      bottomPanel.add(mySize = new JLabel());
    }
  }

  @NotNull
  public static JEditorPane createDescriptionComponent(@Nullable Consumer<View> imageViewHandler) {
    JEditorPane editorPane = new JEditorPane();

    HTMLEditorKit kit;
    if (imageViewHandler == null) {
      kit = UIUtil.getHTMLEditorKit();
    }
    else {
      kit = new JBHtmlEditorKit() {
        private final ViewFactory myFactory = new JBHtmlFactory() {
          @Override
          public View create(Element e) {
            View view = super.create(e);
            if (view instanceof ImageView) {
              imageViewHandler.accept(view);
            }
            return view;
          }
        };

        @Override
        public ViewFactory getViewFactory() {
          return myFactory;
        }
      };
    }
    StyleSheet sheet = kit.getStyleSheet();
    sheet.addRule("ul {margin-left: 16px}"); // list-style-type: none;
    sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor()) + "}");

    editorPane.setEditable(false);
    editorPane.setOpaque(false);
    editorPane.setBorder(null);
    editorPane.setContentType("text/html");
    editorPane.setEditorKit(kit);
    editorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    return editorPane;
  }

  public void showPlugin(@Nullable ListPluginComponent component, boolean multiSelection) {
    if (myShowComponent == component && (component == null || myUpdateDescriptor == component.myUpdateDescriptor)) {
      return;
    }
    myShowComponent = component;

    if (myIndicator != null) {
      MyPluginModel.removeProgress(myPlugin, myIndicator);
      hideProgress(false, false);
    }

    if (component == null) {
      myPlugin = myUpdateDescriptor = null;
      select(1, true);
      setEmptyState(multiSelection ? EmptyState.MULTI_SELECT : EmptyState.NONE_SELECTED);
    }
    else {
      boolean syncLoading = true;
      IdeaPluginDescriptor descriptor = component.getPluginDescriptor();
      if (descriptor instanceof PluginNode) {
        PluginNode node = (PluginNode)descriptor;
        if (node.getDescription() == null && node.getExternalPluginId() != null && node.getExternalUpdateId() != null) {
          syncLoading = false;
          startLoading();
          ProcessIOExecutorService.INSTANCE.execute(() -> {
            PluginNode meta = MarketplaceRequests.getInstance()
              .loadPluginDescriptor(node.getPluginId().getIdString(), node.getExternalPluginId(), node.getExternalUpdateId());
            meta.setRating(node.getRating());
            meta.setDownloads(node.getDownloads());
            component.myPlugin = meta;

            ApplicationManager.getApplication().invokeLater(() -> {
              if (myShowComponent == component) {
                stopLoading();
                showPlugin(component);
              }
            }, ModalityState.stateForComponent(component));
          });
        }
      }

      if (syncLoading) {
        showPlugin(component);
      }
    }
  }

  private void showPlugin(@NotNull ListPluginComponent component) {
    myPlugin = component.myPlugin;
    myUpdateDescriptor = component.myUpdateDescriptor;
    showPlugin();
    select(0, true);
  }

  private enum EmptyState {
    NONE_SELECTED,
    MULTI_SELECT,
    PROGRESS
  }

  private void setEmptyState(EmptyState emptyState) {
    StatusText text = myEmptyPanel.getEmptyText();
    text.clear();
    myLoadingIcon.setVisible(false);
    myLoadingIcon.suspend();
    switch (emptyState) {
      case MULTI_SELECT:
        text.setText(IdeBundle.message("plugins.configurable.several.plugins"));
        text.appendSecondaryText(IdeBundle.message("plugins.configurable.one.plugin.details"), StatusText.DEFAULT_ATTRIBUTES, null);
        break;
      case NONE_SELECTED:
        text.setText(IdeBundle.message("plugins.configurable.plugin.details"));
        break;
      case PROGRESS:
        myLoadingIcon.setVisible(true);
        myLoadingIcon.resume();
        break;
    }
  }

  private void showPlugin() {
    myNameComponent.setText("<html><span>" + myPlugin.getName() + "</span></html>");
    updateIcon();

    updateButtons();

    boolean bundled = myPlugin.isBundled() && !myPlugin.allowBundledUpdate();
    String version = myPlugin.getVersion();
    if (bundled) {
      version = "bundled" + (StringUtil.isEmptyOrSpaces(version) ? "" : " " + version);
    }
    if (myUpdateDescriptor != null) {
      version = PluginManagerConfigurable.getVersion(myPlugin, myUpdateDescriptor);
    }

    myVersion.setText(version);
    myVersionSize.setText(version);
    myVersion
      .setPreferredSize(new Dimension(myVersionSize.getPreferredSize().width + JBUIScale.scale(4), myVersion.getPreferredSize().height));

    myVersion.setVisible(!StringUtil.isEmptyOrSpaces(version));

    myTagPanel.setTags(PluginManagerConfigurable.getTags(myPlugin));

    if (myMarketplace) {
      String rating = PluginManagerConfigurable.getRating(myPlugin);
      myRating.setText(rating);
      myRating.setVisible(rating != null);

      String downloads = PluginManagerConfigurable.getDownloads(myPlugin);
      myDownloads.setText(downloads);
      myDownloads.setVisible(downloads != null);

      String size = PluginManagerConfigurable.getSize(myPlugin);
      mySize.setText(IdeBundle.message("plugins.configurable.size.0", size));
      mySize.setVisible(!StringUtil.isEmptyOrSpaces(size));
    }

    String vendor = myPlugin.isBundled() ? null : StringUtil.trim(myPlugin.getVendor());
    if (StringUtil.isEmptyOrSpaces(vendor)) {
      myVendor.hide();
    }
    else {
      myVendor.show(vendor, () -> mySearchListener
        .linkSelected(null, SearchWords.ORGANIZATION.getValue() + (vendor.indexOf(' ') == -1 ? vendor : StringUtil.wrapWithDoubleQuote(vendor))));
    }

    showLicensePanel();

    if (bundled) {
      myHomePage.hide();
    }
    else {
      try {
        List<String> marketplacePlugins = MarketplaceRequests.getInstance().getMarketplaceCachedPlugins();
        if (marketplacePlugins != null) {
          if (marketplacePlugins.contains(myPlugin.getPluginId().getIdString())) {
            // Prevent the link to the marketplace from showing to external plugins
            showMarketplaceHomePage();
          }
        }
        else {
          // There are no marketplace plugins in the cache, but we should show the title anyway.
          showMarketplaceHomePage();
          // will get the marketplace plugins ids next time
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
              MarketplaceRequests.getInstance().getMarketplacePlugins(null);
            }
            catch (IOException ignore) {
            }
          });
        }
      }
      catch (IOException ignore) {
      }
    }

    String date = PluginManagerConfigurable.getLastUpdatedDate(myUpdateDescriptor == null ? myPlugin : myUpdateDescriptor);
    myDate.setText(date);
    myDate.setVisible(date != null);

    myBottomScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    String description = getDescription();
    if (description != null && !description.equals(myDescription)) {
      myDescription = description;
      myDescriptionComponent.setText(XmlStringUtil.wrapInHtml(description));
      if (myDescriptionComponent.getCaret() != null) {
        myDescriptionComponent.setCaretPosition(0);
      }
    }
    myDescriptionComponent.setVisible(description != null);

    myChangeNotesPanel.show(getChangeNotes());

    ApplicationManager.getApplication().invokeLater(() -> {
      IdeEventQueue.getInstance().flushQueue();
      ((JBScrollBar)myBottomScrollPane.getVerticalScrollBar()).setCurrentValue(0);
    }, ModalityState.any());

    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress();
    }
    else {
      fullRepaint();
    }
  }

  private void showLicensePanel() {
    String productCode = myPlugin.getProductCode();
    if (myPlugin.isBundled() || LicensePanel.isEA2Product(productCode)) {
      myLicensePanel.hideWithChildren();
      return;
    }
    if (productCode == null) {
      if (myUpdateDescriptor != null && myUpdateDescriptor.getProductCode() != null &&
          !LicensePanel.isEA2Product(myUpdateDescriptor.getProductCode())) {
        myLicensePanel.setText(IdeBundle.message("label.next.plugin.version.is.paid.use.the.trial.for.up.to.30.days.or"), true, false);
        myLicensePanel.showBuyPlugin(() -> myUpdateDescriptor);
        myLicensePanel.setVisible(true);
      }
      else {
        myLicensePanel.hideWithChildren();
      }
    }
    else if (myMarketplace) {
      myLicensePanel.setText(IdeBundle.message("label.use.the.trial.for.up.to.30.days.or"), false, false);
      myLicensePanel.showBuyPlugin(() -> myPlugin);
      myLicensePanel.setVisible(true);
    }
    else {
      LicensingFacade instance = LicensingFacade.getInstance();
      if (instance == null) {
        myLicensePanel.hideWithChildren();
        return;
      }

      String stamp = instance.getConfirmationStamp(productCode);
      if (stamp == null) {
        if (ApplicationManager.getApplication().isEAP()) {
          myTagPanel.setFirstTagTooltip("The license is not required for EAP version");
          myLicensePanel.hideWithChildren();
          return;
        }
        myLicensePanel.setText(IdeBundle.message("label.text.plugin.no.license"), true, false);
      }
      else {
        myLicensePanel.setTextFromStamp(stamp, instance.getExpirationDate(productCode));
      }

      myTagPanel.setFirstTagTooltip(myLicensePanel.getMessage());
      //myLicensePanel.setLink("Manage licenses", () -> { XXX }, false);
      myLicensePanel.setVisible(true);
    }
  }

  public void updateButtons() {
    boolean installedWithoutRestart = InstalledPluginsState.getInstance().wasInstalledWithoutRestart(myPlugin.getPluginId());
    if (myMarketplace) {
      boolean installed = InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId());
      myRestartButton.setVisible(installed);

      myInstallButton.setEnabled(PluginManagerCore.getPlugin(myPlugin.getPluginId()) == null && !installedWithoutRestart,
                                 IdeBundle.message("plugins.configurable.installed"));
      myInstallButton.setVisible(!installed);

      myUpdateButton.setVisible(false);
      myEnableDisableButton.setVisible(false);
      myEnableDisableUninstallButton.setVisible(false);
    }
    else {
      myInstallButton.setVisible(false);

      boolean uninstalled = myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted();
      boolean uninstalledWithoutRestart = InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(myPlugin.getPluginId());
      if (!uninstalled) {
        InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
        PluginId id = myPlugin.getPluginId();
        uninstalled = pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id);
      }

      if (uninstalled) {
        if (uninstalledWithoutRestart) {
          myRestartButton.setVisible(false);
          myInstallButton.setVisible(true);
          myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
        }
        else {
          myRestartButton.setVisible(true);
        }
        myUpdateButton.setVisible(false);
        myEnableDisableButton.setVisible(false);
        myEnableDisableUninstallButton.setVisible(false);
      }
      else {
        myRestartButton.setVisible(false);

        boolean bundled = myPlugin.isBundled();
        String title = myPluginModel.getEnabledTitle(myPlugin);

        myUpdateButton.setVisible(myUpdateDescriptor != null && !installedWithoutRestart);

        myEnableDisableButton.setVisible(bundled);
        myEnableDisableButton.setText(title);

        myEnableDisableUninstallButton.setVisible(!bundled);
        myEnableDisableAction.putValue(Action.NAME, title);
      }

      updateEnableForNameAndIcon();
      updateIcon();
      updateErrors();
    }
  }

  private void showMarketplaceHomePage() {
    myHomePage.show(
      IdeBundle.message("plugins.configurable.plugin.homepage.link"),
      () -> BrowserUtil.browse(MARKETPLACE_LINK + URLUtil.encodeURIComponent(myPlugin.getPluginId().getIdString()))
    );
  }


  private void updateIcon() {
    boolean errors = !myMarketplace && myPluginModel.hasErrors(myPlugin);

    myIconLabel.setEnabled(myMarketplace || myPluginModel.isEnabled(myPlugin));
    myIconLabel.setIcon(myPluginModel.getIcon(myPlugin, true, false, errors, false));
    myIconLabel.setDisabledIcon(myPluginModel.getIcon(myPlugin, true, false, errors, true));
  }

  private void updateErrors() {
    Ref<String> enableAction = new Ref<>();
    String message = myPluginModel.getErrorMessage(myPlugin, enableAction);
    if (message != null) {
      ErrorComponent.show(myErrorComponent, message, enableAction.get(), enableAction.isNull() ? null : () -> handleErrors());
    }
    myErrorComponent.setVisible(message != null);
  }

  private void handleErrors() {
    myPluginModel.enableRequiredPlugins(myPlugin);

    updateIcon();
    updateEnabledState();
    fullRepaint();
  }

  public void showProgress() {
    myIndicator = new OneLineProgressIndicator(false);
    myIndicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, null, false, false, true));
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
      updateButtons();
    }
    if (repaint) {
      fullRepaint();
    }
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
    String title = myPluginModel.getEnabledTitle(myPlugin);

    myEnableDisableButton.setText(title);
    myEnableDisableUninstallButton.setText(title);
    myUpdateButton.setVisible(myUpdateDescriptor != null);
    myEnableDisableButton.setVisible(bundled);
    myEnableDisableUninstallButton.setVisible(!bundled);

    fullRepaint();
  }

  private void doUninstall() {
    if (MyPluginModel.showUninstallDialog(this, myPlugin.getName(), 1)) {
      myPluginModel.uninstallAndUpdateUi(this, myPlugin);
    }
  }

  public void startLoading() {
    select(1, true);
    setEmptyState(EmptyState.PROGRESS);
    fullRepaint();
  }

  public void stopLoading() {
    myLoadingIcon.suspend();
    myLoadingIcon.setVisible(false);
    fullRepaint();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    updateIconLocation();
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    updateIconLocation();
  }

  private void updateIconLocation() {
    if (myLoadingIcon.isVisible()) {
      myLoadingIcon.updateLocation(this);
    }
  }

  @Nullable
  private String getDescription() {
    String description = myPlugin.getDescription();
    return StringUtil.isEmptyOrSpaces(description) ? null : description;
  }

  @Nullable
  private String getChangeNotes() {
    if (myUpdateDescriptor != null) {
      String notes = myUpdateDescriptor.getChangeNotes();
      if (!StringUtil.isEmptyOrSpaces(notes)) {
        return notes;
      }
    }
    String notes = myPlugin.getChangeNotes();
    return StringUtil.isEmptyOrSpaces(notes) ? null : notes;
  }
}