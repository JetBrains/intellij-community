// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public final class PluginDetailsPageComponent extends MultiPanel {
  private static final String MARKETPLACE_LINK = "/plugin/index?xmlId=";

  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;

  @NotNull
  private final AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.BigCentered(IdeBundle.message("progress.text.loading"));

  private JBPanelWithEmptyText myEmptyPanel;

  private OpaquePanel myRootPanel;
  private OpaquePanel myPanel;
  private JLabel myIconLabel;
  private final JEditorPane myNameComponent = createNameComponent();
  private final BaselinePanel myNameAndButtons = new BaselinePanel();
  private JButton myRestartButton;
  private InstallButton myInstallButton;
  private JButton myUpdateButton;
  private JComponent myGearButton;
  private ErrorComponent myErrorComponent;
  private JTextField myVersion;
  private JLabel myEnabledForProject;
  private JLabel myVersionSize;
  private TagPanel myTagPanel;
  private JLabel myDate;
  private JLabel myRating;
  private JLabel myDownloads;
  private JLabel mySize;
  private LinkPanel myAuthor;
  private BorderLayoutPanel myControlledByOrgNotification;
  private final LicensePanel myLicensePanel = new LicensePanel(false);
  private LinkPanel myHomePage;
  private JBScrollPane myBottomScrollPane;
  private JEditorPane myDescriptionComponent;
  private String myDescription;
  private ChangeNotesPanel myChangeNotesPanel;
  private OneLineProgressIndicator myIndicator;

  private @Nullable IdeaPluginDescriptor myPlugin;
  private boolean myIsPluginAllowed;
  private IdeaPluginDescriptor myUpdateDescriptor;

  private ListPluginComponent myShowComponent;

  public PluginDetailsPageComponent(@NotNull MyPluginModel pluginModel, @NotNull LinkListener<Object> searchListener, boolean marketplace) {
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
    myMarketplace = marketplace;
    createPluginPanel();
    select(1, true);
    setEmptyState(EmptyState.NONE_SELECTED);
  }

  @Nullable IdeaPluginDescriptor getPlugin() {
    return myPlugin;
  }

  void setPlugin(@Nullable IdeaPluginDescriptor plugin) {
    if (plugin != null) {
      myPlugin = plugin;
    }
  }

  public boolean isShowingPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    return myPlugin != null && myPlugin.getPluginId().equals(pluginDescriptor.getPluginId());
  }

  @Override
  protected JComponent create(Integer key) {
    if (key == 0) {
      return myRootPanel;
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

    myRootPanel = new OpaquePanel(new BorderLayout());
    myControlledByOrgNotification = new BorderLayoutPanel();
    Border customLine = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0);
    myControlledByOrgNotification.setBorder(JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true));
    myControlledByOrgNotification.setBackground(JBUI.CurrentTheme.Notification.BACKGROUND);
    myControlledByOrgNotification.setForeground(JBUI.CurrentTheme.Notification.FOREGROUND);

    JBLabel notificationLabel = new JBLabel();
    notificationLabel.setIcon(AllIcons.General.Warning);
    notificationLabel.setVerticalTextPosition(SwingConstants.TOP);
    notificationLabel.setText(HtmlChunk
                                .html()
                                .addText(IdeBundle.message("plugins.configurable.not.allowed"))
                                .toString());

    myControlledByOrgNotification.addToCenter(notificationLabel);
    myControlledByOrgNotification.setVisible(false);

    myRootPanel.add(myControlledByOrgNotification, BorderLayout.NORTH);
    myRootPanel.add(myPanel, BorderLayout.CENTER);
  }

  @NotNull
  private JPanel createHeaderPanel() {
    JPanel header = new NonOpaquePanel(new BorderLayout(JBUIScale.scale(15), 0));
    header.setBorder(JBUI.Borders.emptyRight(20));
    myPanel.add(header, BorderLayout.NORTH);

    myIconLabel = new JLabel();
    myIconLabel.setBorder(JBUI.Borders.emptyTop(5));
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
      myErrorComponent = new ErrorComponent();
      centerPanel.add(myErrorComponent, VerticalLayout.FILL_HORIZONTAL);
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

      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (size.height == 0) {
          size.height = getMinimumSize().height;
        }
        return size;
      }

      @Override
      public void updateUI() {
        super.updateUI();
        setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD, 18));
      }
    };

    UIUtil.convertToLabel(editorPane);
    editorPane.setCaret(EmptyCaret.INSTANCE);

    editorPane.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD, 18));

    @NlsSafe String text = "<html><span>Foo</span></html>";
    editorPane.setText(text);
    editorPane.setMinimumSize(editorPane.getPreferredSize());
    editorPane.setText(null);

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

    myGearButton = SelectionBasedPluginModelAction.createGearButton(this::createEnableDisableAction,
                                                                    this::createUninstallAction);
    myGearButton.setOpaque(false);
    myNameAndButtons.addButtonComponent(myGearButton);

    for (Component component : myNameAndButtons.getButtonComponents()) {
      component.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    }
  }

  public void setOnlyUpdateMode() {
    myNameAndButtons.removeButtons();
    Container parent = myEnabledForProject.getParent();
    if (parent != null) {
      parent.remove(myEnabledForProject);
    }
    myPanel.setBorder(JBUI.Borders.empty(15, 20, 0, 0));
    myEmptyPanel.setBorder(null);
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    // text field without horizontal margins
    myVersion = new JTextField() {
      @Override
      public void setBorder(Border border) {
        super.setBorder(null);
      }

      @Override
      public void updateUI() {
        super.updateUI();
        if (myVersion != null) {
          PluginDetailsPageComponent.setFont(myVersion);
        }
        if (myVersionSize != null) {
          PluginDetailsPageComponent.setFont(myVersionSize);
        }
      }
    };
    myVersion.putClientProperty("TextFieldWithoutMargins", Boolean.TRUE);
    myVersion.setEditable(false);
    setFont(myVersion);
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
    setFont(myVersionSize);

    int offset = JBUIScale.scale(10);
    JPanel panel1 = new NonOpaquePanel(new TextHorizontalLayout(offset));
    centerPanel.add(panel1);
    if (myMarketplace) {
      myDownloads =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Downloads, ListPluginComponent.GRAY_COLOR, true);

      myRating =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Rating, ListPluginComponent.GRAY_COLOR, true);
    }
    myAuthor = new LinkPanel(panel1, false, true, null, TextHorizontalLayout.FIX_LABEL);

    myEnabledForProject = new JLabel();
    myEnabledForProject.add(createDescriptionComponent(null));
    myEnabledForProject.setHorizontalTextPosition(SwingConstants.LEFT);
    myEnabledForProject.setForeground(ListPluginComponent.GRAY_COLOR);
    setFont(myEnabledForProject);

    TextHorizontalLayout layout = myMarketplace ? new TextHorizontalLayout(offset) {
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
    } : new TextHorizontalLayout(JBUIScale.scale(7));

    JPanel panel2 = new NonOpaquePanel(layout);
    panel2.setBorder(JBUI.Borders.emptyTop(5));
    panel2.add(myTagPanel = new TagPanel(mySearchListener));
    (myMarketplace ? panel2 : panel1).add(myVersion);
    panel2.add(myEnabledForProject);

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
    myBottomScrollPane.setBorder(JBUI.Borders.empty());
    myPanel.add(myBottomScrollPane);

    bottomPanel.add(myLicensePanel);
    myLicensePanel.setBorder(JBUI.Borders.emptyBottom(20));

    if (myMarketplace) {
      myHomePage = new LinkPanel(bottomPanel, false);
      bottomPanel.add(new JLabel());
    }

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
    else {
      myHomePage = new LinkPanel(bottomPanel, false);
    }
  }

  private static void setFont(@NotNull JComponent component) {
    component.setFont(StartupUiUtil.getLabelFont());
    PluginManagerConfigurable.setTinyFont(component);
  }

  @NotNull
  public static JEditorPane createDescriptionComponent(@Nullable Consumer<? super View> imageViewHandler) {
    HTMLEditorKit kit = new HTMLEditorKitBuilder().withViewFactoryExtensions((e, view) -> {
      if (view instanceof ParagraphView) {
        return new ParagraphView(e) {
          {
            super.setLineSpacing(0.3f);
          }

          @Override
          protected void setLineSpacing(float ls) {
          }
        };
      }
      if (imageViewHandler != null && view instanceof ImageView) {
        imageViewHandler.accept(view);
      }
      return view;
    }).build();

    StyleSheet sheet = kit.getStyleSheet();
    sheet.addRule("ul { margin-left-ltr: 30; margin-right-rtl: 30; }");
    sheet.addRule("a { color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "; }");
    sheet.addRule("h4 { font-weight: bold; }");
    sheet.addRule("strong { font-weight: bold; }");
    sheet.addRule("p { margin-bottom: 6px; }");

    Font font = StartupUiUtil.getLabelFont();

    if (font != null) {
      int size = font.getSize();
      sheet.addRule("h3 { font-size: " + (size + 3) + "; font-weight: bold; }");
      sheet.addRule("h2 { font-size: " + (size + 5) + "; font-weight: bold; }");
      sheet.addRule("h1 { font-size: " + (size + 9) + "; font-weight: bold; }");
      sheet.addRule("h0 { font-size: " + (size + 12) + "; font-weight: bold; }");
    }

    JEditorPane editorPane = new JEditorPane();
    editorPane.setEditable(false);
    editorPane.setOpaque(false);
    editorPane.setBorder(null);
    editorPane.setContentType("text/html");
    editorPane.setEditorKit(kit);
    editorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    return editorPane;
  }

  public void showPlugins(@NotNull List<? extends ListPluginComponent> selection) {
    int size = selection.size();
    showPlugin(size == 1 ? selection.get(0) : null, size > 1);
  }

  public void showPlugin(@Nullable ListPluginComponent component) {
    showPlugin(component, false);
  }

  private void showPlugin(@Nullable ListPluginComponent component, boolean multiSelection) {
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
        if (!node.detailsLoaded()) {
          syncLoading = false;
          startLoading();
          ProcessIOExecutorService.INSTANCE.execute(() -> {
            PluginNode pluginNode = MarketplaceRequests.getInstance().loadPluginDetails(node);
            if (pluginNode == null) {
              return;
            }

            component.setPluginDescriptor(pluginNode);

            ApplicationManager.getApplication().invokeLater(() -> {
              if (myShowComponent == component) {
                stopLoading();
                showPluginImpl(component.getPluginDescriptor(), component.myUpdateDescriptor);
                PluginManagerUsageCollector.pluginCardOpened(component.getPluginDescriptor(), component.getGroup());
              }
            }, ModalityState.stateForComponent(component));
          });
        }
      }

      if (syncLoading) {
        showPluginImpl(component.getPluginDescriptor(), component.myUpdateDescriptor);
        PluginManagerUsageCollector.pluginCardOpened(component.getPluginDescriptor(), component.getGroup());
      }
    }
  }

  public void showPluginImpl(@NotNull IdeaPluginDescriptor pluginDescriptor, @Nullable IdeaPluginDescriptor updateDescriptor) {
    myPlugin = pluginDescriptor;
    PluginManagerFilters org = PluginManagerFilters.getInstance();
    myUpdateDescriptor = updateDescriptor != null && org.isPluginAllowed(!myMarketplace, updateDescriptor) ? updateDescriptor : null;
    myIsPluginAllowed = org.isPluginAllowed(!myMarketplace, pluginDescriptor);
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
    @NlsSafe String text = "<html><span>" + myPlugin.getName() + "</span></html>";
    myNameComponent.setText(text);
    myControlledByOrgNotification.setVisible(!myIsPluginAllowed);
    updateIcon();

    updateButtons();

    String version = myPlugin.getVersion();
    if (myPlugin.isBundled() && !myPlugin.allowBundledUpdate()) {
      version = IdeBundle.message("plugin.version.bundled") + (Strings.isEmptyOrSpaces(version) ? "" : " " + version);
    }
    if (myUpdateDescriptor != null) {
      version = NewUiUtil.getVersion(myPlugin, myUpdateDescriptor);
    }

    myVersion.setText(version);
    myVersionSize.setText(version);
    myVersion
      .setPreferredSize(
        new Dimension(myVersionSize.getPreferredSize().width + JBUIScale.scale(4), myVersionSize.getPreferredSize().height));

    myVersion.setVisible(!Strings.isEmptyOrSpaces(version));

    myTagPanel.setTags(PluginManagerConfigurable.getTags(myPlugin));

    if (myMarketplace) {
      String rating = null;
      String downloads = null;
      String size = null;
      if (myPlugin instanceof PluginNode) {
        PluginNode pluginNode = (PluginNode)myPlugin;
        rating = pluginNode.getPresentableRating();
        downloads = pluginNode.getPresentableDownloads();
        size = pluginNode.getPresentableSize();
      }

      myRating.setText(rating);
      myRating.setVisible(rating != null);

      myDownloads.setText(downloads);
      myDownloads.setVisible(downloads != null);

      mySize.setText(IdeBundle.message("plugins.configurable.size.0", size));
      mySize.setVisible(size != null);
    }
    else {
      updateEnabledForProject();
    }

    String vendor = myPlugin.isBundled() ? null : Strings.trim(myPlugin.getVendor());
    String organization = myPlugin.isBundled() ? null : Strings.trim(myPlugin.getOrganization());
    if (Strings.isEmptyOrSpaces(vendor)) {
      myAuthor.hide();
    }
    else {
      if (Strings.isEmptyOrSpaces(organization)) {
        myAuthor.show(vendor, null);
      }
      else {
        myAuthor.show(organization, () -> mySearchListener.linkSelected(
          null,
          SearchWords.ORGANIZATION.getValue() +
          (organization.indexOf(' ') == -1 ? organization : '\"' + organization + "\"")
        ));
      }
    }

    showLicensePanel();

    if (myPlugin.isBundled() && !myPlugin.allowBundledUpdate() || !isPluginFromMarketplace()) {
      myHomePage.hide();
    }
    else {
      myHomePage.show(IdeBundle.message(
                        "plugins.configurable.plugin.homepage.link"),
                      () -> {
                        String url = ((ApplicationInfoEx)ApplicationInfo.getInstance()).getPluginManagerUrl() +
                                     MARKETPLACE_LINK +
                                     URLUtil.encodeURIComponent(myPlugin.getPluginId().getIdString());
                        BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url));
                      });
    }

    IdeaPluginDescriptor pluginNode = myUpdateDescriptor != null ? myUpdateDescriptor : myPlugin;
    String date = pluginNode instanceof PluginNode ?
                  ((PluginNode)pluginNode).getPresentableDate() :
                  null;
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

  private boolean isPluginFromMarketplace() {
    assert myPlugin != null;
    PluginInfoProvider provider = PluginInfoProvider.getInstance();
    Set<PluginId> marketplacePlugins = provider.loadCachedPlugins();
    if (marketplacePlugins != null) {
      return marketplacePlugins.contains(myPlugin.getPluginId());
    }

    // will get the marketplace plugins ids next time
    provider.loadPlugins();
    // There are no marketplace plugins in the cache, but we should show the title anyway.
    return true;
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
          myTagPanel.setFirstTagTooltip(IdeBundle.message("tooltip.license.not.required.for.eap.version"));
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
    if (!myIsPluginAllowed) {
      myRestartButton.setVisible(false);
      myInstallButton.setVisible(false);
      myUpdateButton.setVisible(false);
      myGearButton.setVisible(false);
      return;
    }

    boolean installedWithoutRestart = InstalledPluginsState.getInstance().wasInstalledWithoutRestart(myPlugin.getPluginId());
    if (myMarketplace) {
      boolean installed = InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId());
      myRestartButton.setVisible(installed);

      myInstallButton.setEnabled(PluginManagerCore.getPlugin(myPlugin.getPluginId()) == null && !installedWithoutRestart,
                                 IdeBundle.message("plugins.configurable.installed"));
      myInstallButton.setVisible(!installed);

      myUpdateButton.setVisible(false);
      myGearButton.setVisible(false);
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
      }
      else {
        myRestartButton.setVisible(false);

        updateEnabledForProject();

        myUpdateButton.setVisible(myUpdateDescriptor != null && !installedWithoutRestart);
      }
      myGearButton.setVisible(!uninstalled);

      updateEnableForNameAndIcon();
      updateErrors();
    }
  }

  private void updateIcon() {
    updateIcon(myPluginModel.getErrors(myPlugin));
  }

  private void updateIcon(@NotNull List<? extends HtmlChunk> errors) {
    boolean hasErrors = !myMarketplace && !errors.isEmpty();

    myIconLabel.setEnabled(myMarketplace || myPluginModel.isEnabled(myPlugin));
    myIconLabel.setIcon(myPluginModel.getIcon(myPlugin, true, hasErrors, false));
    myIconLabel.setDisabledIcon(myPluginModel.getIcon(myPlugin, true, hasErrors, true));
  }

  private void updateErrors() {
    @NotNull List<? extends HtmlChunk> errors = myPluginModel.getErrors(myPlugin);
    updateIcon(errors);
    myErrorComponent.setErrors(errors, this::handleErrors);
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

    updateEnableForNameAndIcon();
    updateErrors();
    updateEnabledForProject();

    myUpdateButton.setVisible(myUpdateDescriptor != null);

    fullRepaint();
  }

  private void updateEnabledForProject() {
    ProjectDependentPluginEnabledState state = myPluginModel.getProjectDependentState(Objects.requireNonNull(myPlugin));
    myEnabledForProject.setText(state.toString());
    myEnabledForProject.setIcon(state.getIcon());
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
  private @Nls String getDescription() {
    String description = myPlugin.getDescription();
    return Strings.isEmptyOrSpaces(description) ? null : description;
  }

  @Nullable
  @NlsSafe
  private String getChangeNotes() {
    if (myUpdateDescriptor != null) {
      String notes = myUpdateDescriptor.getChangeNotes();
      if (!Strings.isEmptyOrSpaces(notes)) {
        return notes;
      }
    }
    String notes = myPlugin.getChangeNotes();
    return Strings.isEmptyOrSpaces(notes) ? null : notes;
  }

  private @NotNull SelectionBasedPluginModelAction.EnableDisableAction<PluginDetailsPageComponent> createEnableDisableAction(@NotNull PluginEnableDisableAction action) {
    return new SelectionBasedPluginModelAction.EnableDisableAction<>(myPluginModel,
                                                                     action,
                                                                     false,
                                                                     List.of(this),
                                                                     PluginDetailsPageComponent::getPlugin);
  }

  private @NotNull SelectionBasedPluginModelAction.UninstallAction<PluginDetailsPageComponent> createUninstallAction() {
    return new SelectionBasedPluginModelAction.UninstallAction<>(myPluginModel,
                                                                 false,
                                                                 this,
                                                                 List.of(this),
                                                                 PluginDetailsPageComponent::getPlugin);
  }
}