// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate;
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.marketplace.PluginReviewComment;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.ide.impl.feedback.PlatformFeedbackDialogs;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.*;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.ListLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.dsl.builder.HyperlinkEventAction;
import com.intellij.ui.dsl.builder.components.DslLabel;
import com.intellij.ui.dsl.builder.components.DslLabelType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.TripleFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * @author Alexander Lobas
 */
public final class PluginDetailsPageComponent extends MultiPanel {
  private final MyPluginModel myPluginModel;
  private final LinkListener<Object> mySearchListener;
  private final boolean myMarketplace;
  private final boolean myMultiTabs;

  private final @NotNull AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.BigCentered(IdeBundle.message("progress.text.loading"));

  private JBPanelWithEmptyText myEmptyPanel;

  private JBTabbedPane myTabbedPane;

  private OpaquePanel myRootPanel;
  private OpaquePanel myPanel;
  private JLabel myIconLabel;
  private final JEditorPane myNameComponent = createNameComponent();
  private final BaselinePanel myNameAndButtons;
  private JButton myRestartButton;
  private InstallButton myInstallButton;
  private final SuggestedIdeBanner mySuggestedIdeBanner = new SuggestedIdeBanner();
  private JButton myUpdateButton;
  private JComponent myGearButton;
  private JButton myEnableDisableButton;
  private ErrorComponent myErrorComponent;
  private JTextField myVersion;
  private JLabel myEnabledForProject;
  private JLabel myVersionSize;
  private TagPanel myTagPanel;

  private JLabel myDate;
  private JLabel myRating;
  private JLabel myDownloads;
  private JBLabel myVersion1;
  private JLabel myVersion2;
  private JLabel mySize;
  private JEditorPane myRequiredPlugins;

  private LinkPanel myAuthor;
  private BorderLayoutPanel myControlledByOrgNotification;
  private BorderLayoutPanel myPlatformIncompatibleNotification;
  private BorderLayoutPanel myUninstallFeedbackNotification;
  private BorderLayoutPanel myDisableFeedbackNotification;
  private HashSet<PluginId> mySentFeedbackPlugins = new HashSet<>();
  private final LicensePanel myLicensePanel = new LicensePanel(false);
  private LinkPanel myHomePage;
  private LinkPanel myForumUrl;
  private LinkPanel myLicenseUrl;
  private VendorInfoPanel myVendorInfoPanel;
  private LinkPanel myBugtrackerUrl;
  private LinkPanel myDocumentationUrl;
  private LinkPanel mySourceCodeUrl;
  private SuggestedComponent mySuggestedFeatures;
  private JBScrollPane myBottomScrollPane;
  private final List<JBScrollPane> myScrollPanes = new ArrayList<>();
  private JEditorPane myDescriptionComponent;
  private String myDescription;
  private ChangeNotes myChangeNotesPanel;
  private JBPanelWithEmptyText myChangeNotesEmptyState;
  private PluginImagesComponent myImagesComponent;
  private ReviewCommentListContainer myReviewPanel;
  private JButton myReviewNextPageButton;
  private OneLineProgressIndicator myIndicator;

  private @Nullable IdeaPluginDescriptor myPlugin;
  private boolean myIsPluginAvailable;
  private boolean myIsPluginCompatible;
  private IdeaPluginDescriptor myUpdateDescriptor;
  private IdeaPluginDescriptor myInstalledDescriptorForMarketplace;

  private ListPluginComponent myShowComponent;

  private @NotNull PluginsViewCustomizer.PluginDetailsCustomizer myCustomizer;

  private SelectionBasedPluginModelAction.OptionButtonController<PluginDetailsPageComponent> myEnableDisableController;

  public static boolean isMultiTabs() {
    return Registry.is("plugins.show.multi.tabs", true);
  }

  public PluginDetailsPageComponent(@NotNull MyPluginModel pluginModel, @NotNull LinkListener<Object> searchListener, boolean marketplace) {
    this(pluginModel, searchListener, marketplace, isMultiTabs());
  }

  public PluginDetailsPageComponent(@NotNull MyPluginModel pluginModel,
                                    @NotNull LinkListener<Object> searchListener,
                                    boolean marketplace,
                                    boolean multiTabs) {
    myPluginModel = pluginModel;
    mySearchListener = searchListener;
    myMarketplace = marketplace;
    myMultiTabs = multiTabs;
    if (multiTabs) {
      myNameAndButtons = new BaselinePanel(12, false);
    }
    else {
      myNameAndButtons = new BaselinePanel();
    }

    myCustomizer = PluginsViewCustomizerKt.getPluginsViewCustomizer().getPluginDetailsCustomizer(myPluginModel);

    createPluginPanel();
    select(1, true);
    setEmptyState(EmptyState.NONE_SELECTED);
  }

  IdeaPluginDescriptor getDescriptorForActions() {
    return !myMarketplace || myInstalledDescriptorForMarketplace == null ? myPlugin : myInstalledDescriptorForMarketplace;
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
        myEmptyPanel.setBorder(new CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insetsTop(1)));
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
    if (myMultiTabs) {
      createTabsContentPanel();
    }
    else {
      createContentPanel();
    }

    myRootPanel = new OpaquePanel(new BorderLayout());
    myControlledByOrgNotification = createNotificationPanel(
      AllIcons.General.Warning,
      IdeBundle.message("plugins.configurable.not.allowed"));
    myPlatformIncompatibleNotification = createNotificationPanel(
      AllIcons.General.Information,
      IdeBundle.message("plugins.configurable.plugin.unavailable.for.platform", SystemInfo.getOsName()));

    final PlatformFeedbackDialogs feedbackDialogProvider = PlatformFeedbackDialogs.getInstance();
    myUninstallFeedbackNotification = createFeedbackNotificationPanel(feedbackDialogProvider::getUninstallFeedbackDialog);
    myDisableFeedbackNotification = createFeedbackNotificationPanel(feedbackDialogProvider::getDisableFeedbackDialog);
    myRootPanel.add(myPanel, BorderLayout.CENTER);
  }

  private void createContentPanel() {
    myPanel = new OpaquePanel(new BorderLayout(0, JBUIScale.scale(32)), PluginManagerConfigurable.MAIN_BG_COLOR);
    myPanel.setBorder(createMainBorder());

    createHeaderPanel().add(createCenterPanel());
    createBottomPanel();
  }

  private static @NotNull CustomLineBorder createMainBorder() {
    return new CustomLineBorder(JBColor.border(), JBUI.insetsTop(1)) {
      @Override
      public Insets getBorderInsets(Component c) {
        return JBUI.insets(15, 20, 0, 20);
      }
    };
  }

  private void createTabsContentPanel() {
    myPanel = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);

    JPanel topPanel = new OpaquePanel(new VerticalLayout(JBUI.scale(8)), PluginManagerConfigurable.MAIN_BG_COLOR);
    topPanel.setBorder(createMainBorder());
    myPanel.add(topPanel, BorderLayout.NORTH);

    topPanel.add(myTagPanel = new TagPanel(mySearchListener));
    topPanel.add(myNameComponent);

    JPanel linkPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(12)));
    topPanel.add(linkPanel);
    myAuthor = new LinkPanel(linkPanel, false, false, null, null);
    myHomePage = new LinkPanel(linkPanel, false);

    topPanel.add(myNameAndButtons);
    topPanel.add(mySuggestedIdeBanner, VerticalLayout.FILL_HORIZONTAL);

    mySuggestedFeatures = new SuggestedComponent();
    topPanel.add(mySuggestedFeatures, VerticalLayout.FILL_HORIZONTAL);

    myNameAndButtons.add(myVersion1 = new JBLabel().setCopyable(true));

    createButtons();
    myNameAndButtons.setProgressDisabledButton(myMarketplace ? myInstallButton : myUpdateButton);

    topPanel.add(myErrorComponent = new ErrorComponent(), VerticalLayout.FILL_HORIZONTAL);
    topPanel.add(myLicensePanel);
    myLicensePanel.setBorder(JBUI.Borders.emptyBottom(5));

    createTabs(myPanel);
  }

  private @NotNull BorderLayoutPanel createFeedbackNotificationPanel(
    @NotNull TripleFunction<@NotNull String, @NotNull String, @Nullable Project, @Nullable DialogWrapper> createDialogWrapperFunction) {
    final BorderLayoutPanel panel = createBaseNotificationPanel();

    final HyperlinkEventAction action = (e) -> {
      if (myPlugin == null) {
        return;
      }

      if (e.getDescription().equals("showFeedback")) {
        final String pluginIdString = myPlugin.getPluginId().getIdString();
        final String pluginName = myPlugin.getName();
        final Component component = e.getInputEvent().getComponent();
        final Project project = ProjectUtil.getProjectForComponent(component);

        final DialogWrapper feedbackDialog = createDialogWrapperFunction.fun(pluginIdString, pluginName, project);
        if (feedbackDialog == null) {
          return;
        }
        boolean isSent = feedbackDialog.showAndGet();
        if (isSent) {
          mySentFeedbackPlugins.add(myPlugin.getPluginId());
          updateNotifications();
        }
      }
    };
    final DslLabel label = new DslLabel(DslLabelType.LABEL);
    label.setText(IdeBundle.message("plugins.configurable.plugin.feedback"));
    label.setAction(action);

    panel.addToCenter(label);
    return panel;
  }

  private void updateNotifications() {
    myRootPanel.remove(myControlledByOrgNotification);
    myRootPanel.remove(myPlatformIncompatibleNotification);
    myRootPanel.remove(myUninstallFeedbackNotification);
    myRootPanel.remove(myDisableFeedbackNotification);

    if (!myIsPluginAvailable) {
      if (!myIsPluginCompatible) {
        myRootPanel.add(myPlatformIncompatibleNotification, BorderLayout.NORTH);
      }
      else {
        myRootPanel.add(myControlledByOrgNotification, BorderLayout.NORTH);
      }
    }

    if (myPlugin != null && !mySentFeedbackPlugins.contains(myPlugin.getPluginId())) {
      final Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.INSTANCE.buildPluginIdMap();
      final IdeaPluginDescriptorImpl pluginDescriptor = pluginIdMap.getOrDefault(myPlugin.getPluginId(), null);
      if (pluginDescriptor != null && myPluginModel.isUninstalled(pluginDescriptor)) {
        myRootPanel.add(myUninstallFeedbackNotification, BorderLayout.NORTH);
      }
      else if (myPluginModel.isDisabledInDiff(myPlugin.getPluginId())) {
        myRootPanel.add(myDisableFeedbackNotification, BorderLayout.NORTH);
      }
    }
  }

  private @NotNull SelectionBasedPluginModelAction.EnableDisableAction<PluginDetailsPageComponent> createEnableDisableAction(@NotNull PluginEnableDisableAction action) {
    return new SelectionBasedPluginModelAction.EnableDisableAction<>(
      myPluginModel, action, false, List.of(this),
      PluginDetailsPageComponent::getDescriptorForActions, () -> {
      updateNotifications();
    });
  }

  private @NotNull JPanel createHeaderPanel() {
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

  private @NotNull JPanel createCenterPanel() {
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

  private static @NotNull JEditorPane createNameComponent() {
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

    editorPane.setFont(JBFont.create(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD, 18)));

    @NlsSafe String text = "<html><span>Foo</span></html>";
    editorPane.setText(text);
    editorPane.setMinimumSize(editorPane.getPreferredSize());
    editorPane.setText(null);

    return editorPane;
  }

  private void createButtons() {
    myNameAndButtons.addButtonComponent(myRestartButton = new RestartButton(myPluginModel));

    myNameAndButtons.addButtonComponent(myUpdateButton = new UpdateButton());
    myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(this, getDescriptorForActions(), myUpdateDescriptor,
                                                                              ModalityState.stateForComponent(myUpdateButton)));

    myNameAndButtons.addButtonComponent(myInstallButton = new InstallButton(true));
    myInstallButton
      .addActionListener(e -> myPluginModel.installOrUpdatePlugin(this, myPlugin, null, ModalityState.stateForComponent(myInstallButton)));

    if (myMultiTabs) {
      myEnableDisableController =
        SelectionBasedPluginModelAction.createOptionButton(this::createEnableDisableAction, this::createUninstallAction);
      myNameAndButtons.addButtonComponent(myGearButton = myEnableDisableController.button);
      myNameAndButtons.addButtonComponent(myEnableDisableButton = myEnableDisableController.bundledButton);
    }
    else {
      myGearButton = SelectionBasedPluginModelAction.createGearButton(this::createEnableDisableAction, this::createUninstallAction);
      myGearButton.setOpaque(false);
      myNameAndButtons.addButtonComponent(myGearButton);
    }

    for (Component component : myNameAndButtons.getButtonComponents()) {
      component.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    }

    myCustomizer.processPluginNameAndButtonsComponent(myNameAndButtons);
  }

  public void setOnlyUpdateMode() {
    if (myMultiTabs) {
      myNameAndButtons.removeButtons();
    }
    else {
      myNameAndButtons.removeButtons();
      Container parent = myEnabledForProject.getParent();
      if (parent != null) {
        parent.remove(myEnabledForProject);
      }
      myPanel.setBorder(JBUI.Borders.empty(15, 20, 0, 0));
    }
    myEmptyPanel.setBorder(null);
  }

  private void createMetricsPanel(@NotNull JPanel centerPanel) {
    createVersionComponent(true);

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
    myEnabledForProject.setHorizontalTextPosition(SwingConstants.LEFT);
    myEnabledForProject.setForeground(ListPluginComponent.GRAY_COLOR);
    setFont(myEnabledForProject, true);

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

  private void createVersionComponent(boolean tiny) {
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
          PluginDetailsPageComponent.setFont(myVersion, tiny);
        }
        if (myVersionSize != null) {
          PluginDetailsPageComponent.setFont(myVersionSize, tiny);
        }
      }
    };
    myVersion.putClientProperty("TextFieldWithoutMargins", Boolean.TRUE);
    myVersion.setEditable(false);
    setFont(myVersion, tiny);
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
    setFont(myVersionSize, tiny);
  }

  private @NotNull JBScrollPane createScrollPane(@NotNull JComponent component) {
    JBScrollPane scrollPane = new JBScrollPane(component);
    scrollPane.getVerticalScrollBar().setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    scrollPane.setBorder(JBUI.Borders.empty());
    myScrollPanes.add(scrollPane);
    return scrollPane;
  }

  private void createBottomPanel() {
    JPanel bottomPanel = new OpaquePanel(new VerticalLayout(PluginManagerConfigurable.offset5()), PluginManagerConfigurable.MAIN_BG_COLOR);
    bottomPanel.setBorder(JBUI.Borders.empty(0, 0, 15, 20));

    myBottomScrollPane = createScrollPane(bottomPanel);
    myPanel.add(myBottomScrollPane);

    bottomPanel.add(myLicensePanel);
    myLicensePanel.setBorder(JBUI.Borders.emptyBottom(20));

    if (myMarketplace) {
      myHomePage = new LinkPanel(bottomPanel, false);
      bottomPanel.add(new JLabel());
    }

    Object constraints = JBUIScale.scale(700);
    bottomPanel.add(myDescriptionComponent = createDescriptionComponent(createHtmlImageViewHandler()), constraints);
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

  private @NotNull Consumer<View> createHtmlImageViewHandler() {
    return view -> {
      float width = view.getPreferredSpan(View.X_AXIS);
      if (width < 0 || width > myBottomScrollPane.getWidth()) {
        myBottomScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
      }
    };
  }

  private void createTabs(@NotNull JPanel parent) {
    JBTabbedPane pane = new JBTabbedPane() {
      @Override
      public void setUI(TabbedPaneUI ui) {
        putClientProperty("TabbedPane.hoverColor", ListPluginComponent.HOVER_COLOR);

        boolean contentOpaque = UIManager.getBoolean("TabbedPane.contentOpaque");
        UIManager.getDefaults().put("TabbedPane.contentOpaque", Boolean.FALSE);
        try {
          super.setUI(ui);
        }
        finally {
          UIManager.getDefaults().put("TabbedPane.contentOpaque", Boolean.valueOf(contentOpaque));
        }
        setTabContainerBorder(this);
      }

      @Override
      public void setEnabledAt(int index, boolean enabled) {
        super.setEnabledAt(index, enabled);
        getTabComponentAt(index).setEnabled(enabled);
      }
    };
    pane.setOpaque(false);
    pane.setBorder(JBUI.Borders.emptyTop(6));
    pane.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    parent.add(pane);
    myTabbedPane = pane;

    createDescriptionTab(pane);
    createChangeNotesTab(pane);
    createReviewTab(pane);
    createAdditionalInfoTab(pane);

    setTabContainerBorder(pane);
  }

  private static void setTabContainerBorder(@NotNull JComponent pane) {
    Component tabContainer = UIUtil.uiChildren(pane).find(component -> component.getClass().getSimpleName().equals("TabContainer"));
    if (tabContainer instanceof JComponent) {
      ((JComponent)tabContainer).setBorder(new SideBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, SideBorder.BOTTOM));
    }
  }

  private void createDescriptionTab(@NotNull JBTabbedPane pane) {
    myDescriptionComponent = createDescriptionComponent(createHtmlImageViewHandler());

    myImagesComponent = new PluginImagesComponent();
    myImagesComponent.setBorder(JBUI.Borders.emptyRight(16));

    JPanel parent = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);
    parent.setBorder(JBUI.Borders.empty(16, 16, 0, 0));
    parent.add(myImagesComponent, BorderLayout.NORTH);
    parent.add(myDescriptionComponent);

    addTabWithoutBorders(pane, () -> {
      pane.addTab(IdeBundle.message("plugins.configurable.overview.tab.name"), myBottomScrollPane = createScrollPane(parent));
    });
    myImagesComponent.setParent(myBottomScrollPane.getViewport());
  }

  private void createChangeNotesTab(@NotNull JBTabbedPane pane) {
    JEditorPane changeNotes = createDescriptionComponent(null);
    myChangeNotesPanel = new ChangeNotes() {
      @Override
      public void show(@Nullable String text) {
        if (text != null) {
          changeNotes.setText(XmlStringUtil.wrapInHtml(text));
          if (changeNotes.getCaret() != null) {
            changeNotes.setCaretPosition(0);
          }
        }
        changeNotes.setVisible(text != null);
      }
    };
    JBPanelWithEmptyText parent = new JBPanelWithEmptyText(new BorderLayout());
    parent.setOpaque(true);
    parent.setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
    parent.setBorder(JBUI.Borders.emptyLeft(12));
    parent.add(changeNotes);
    myChangeNotesEmptyState = parent;
    pane.add(IdeBundle.message("plugins.configurable.whats.new.tab.name"), createScrollPane(parent));
  }

  private void createReviewTab(@NotNull JBTabbedPane pane) {
    JPanel topPanel = new Wrapper(new BorderLayout(0, JBUI.scale(5)));
    topPanel.setBorder(JBUI.Borders.empty(16, 16, 12, 16));

    LinkPanel newReviewLink = new LinkPanel(topPanel, true, false, null, BorderLayout.WEST);
    newReviewLink.showWithBrowseUrl(IdeBundle.message("plugins.new.review.action"), false, () -> {
      PluginId pluginId = requireNonNull(myPlugin).getPluginId();
      IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(pluginId);
      return MarketplaceUrls.getPluginWriteReviewUrl(pluginId, installedPlugin != null ? installedPlugin.getVersion() : null);
    });

    JPanel notePanel = new Wrapper(ListLayout.horizontal(JBUI.scale(5), ListLayout.Alignment.CENTER, ListLayout.GrowPolicy.NO_GROW));
    LinkPanel noteLink = new LinkPanel(notePanel, true, true, null, null);
    noteLink.showWithBrowseUrl(IdeBundle.message("plugins.review.note"), IdeBundle.message("plugins.review.note.link"), false,
                               () -> MarketplaceUrls.getPluginReviewNoteUrl());
    topPanel.add(notePanel, BorderLayout.SOUTH);

    JPanel reviewsPanel = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);
    reviewsPanel.add(topPanel, BorderLayout.NORTH);

    myReviewPanel = new ReviewCommentListContainer();
    reviewsPanel.add(myReviewPanel);

    myReviewNextPageButton = new JButton(IdeBundle.message("plugins.review.panel.next.page.button"));
    myReviewNextPageButton.setOpaque(false);
    reviewsPanel.add(new Wrapper(new FlowLayout(), myReviewNextPageButton), BorderLayout.SOUTH);

    myReviewNextPageButton.addActionListener(e -> {
      myReviewNextPageButton.setIcon(AnimatedIcon.Default.INSTANCE);
      myReviewNextPageButton.setEnabled(false);

      ListPluginComponent component = myShowComponent;
      PluginNode installedNode = getInstalledPluginMarketplaceNode();
      PluginNode node = installedNode != null ? installedNode : (PluginNode)component.getPluginDescriptor();
      PageContainer<PluginReviewComment> reviewComments = requireNonNull(node.getReviewComments());
      int page = reviewComments.getNextPage();

      ProcessIOExecutorService.INSTANCE.execute(() -> {
        List<PluginReviewComment> items = MarketplaceRequests.getInstance().loadPluginReviews(node, page);

        ApplicationManager.getApplication().invokeLater(() -> {
          if (myShowComponent != component) {
            return;
          }

          if (items != null) {
            reviewComments.addItems(items);
            myReviewPanel.addComments(items);
            myReviewPanel.fullRepaint();
          }

          myReviewNextPageButton.setIcon(null);
          myReviewNextPageButton.setEnabled(true);
          myReviewNextPageButton.setVisible(reviewComments.isNextPage());
        }, ModalityState.stateForComponent(component));
      });
    });

    addTabWithoutBorders(pane, () -> {
      pane.add(IdeBundle.message("plugins.configurable.reviews.tab.name"), createScrollPane(reviewsPanel));
    });
  }

  private void createAdditionalInfoTab(@NotNull JBTabbedPane pane) {
    JPanel infoPanel = new OpaquePanel(new VerticalLayout(JBUI.scale(16)), PluginManagerConfigurable.MAIN_BG_COLOR);
    infoPanel.setBorder(JBUI.Borders.empty(16, 12, 0, 0));

    myDocumentationUrl = new LinkPanel(infoPanel, false);
    myBugtrackerUrl = new LinkPanel(infoPanel, false);
    myForumUrl = new LinkPanel(infoPanel, false);
    mySourceCodeUrl = new LinkPanel(infoPanel, false);
    myLicenseUrl = new LinkPanel(infoPanel, false);

    infoPanel.add(myVendorInfoPanel = new VendorInfoPanel());
    infoPanel.add(myRating = new JLabel());
    infoPanel.add(myDownloads = new JLabel());
    infoPanel.add(myVersion2 = new JLabel());
    infoPanel.add(myDate = new JLabel());
    infoPanel.add(mySize = new JLabel());
    infoPanel.add(myRequiredPlugins = createRequiredPluginsComponent(), VerticalLayout.FILL_HORIZONTAL);

    myRating.setForeground(ListPluginComponent.GRAY_COLOR);
    myDownloads.setForeground(ListPluginComponent.GRAY_COLOR);
    myVersion2.setForeground(ListPluginComponent.GRAY_COLOR);
    myDate.setForeground(ListPluginComponent.GRAY_COLOR);
    mySize.setForeground(ListPluginComponent.GRAY_COLOR);

    pane.add(IdeBundle.message("plugins.configurable.additional.info.tab.name"), new Wrapper(infoPanel));
  }

  private static @NotNull JEditorPane createRequiredPluginsComponent() {
    JEditorPane editorPane = new JEditorPane();
    UIUtil.convertToLabel(editorPane);
    editorPane.setCaret(EmptyCaret.INSTANCE);
    editorPane.setForeground(ListPluginComponent.GRAY_COLOR);
    editorPane.setContentType("text/plain");
    return editorPane;
  }

  private static void addTabWithoutBorders(@NotNull JBTabbedPane pane, @NotNull Runnable callback) {
    Insets insets = pane.getTabComponentInsets();
    pane.setTabComponentInsets(JBInsets.emptyInsets());
    callback.run();
    pane.setTabComponentInsets(insets);
  }

  private static void setFont(@NotNull JComponent component, boolean tiny) {
    component.setFont(StartupUiUtil.getLabelFont());
    if (tiny) {
      PluginManagerConfigurable.setTinyFont(component);
    }
  }

  public static @NotNull JEditorPane createDescriptionComponent(@Nullable Consumer<? super View> imageViewHandler) {
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

    int size = font.getSize();
    sheet.addRule("h3 { font-size: " + (size + 3) + "; font-weight: bold; }");
    sheet.addRule("h2 { font-size: " + (size + 5) + "; font-weight: bold; }");
    sheet.addRule("h1 { font-size: " + (size + 9) + "; font-weight: bold; }");
    sheet.addRule("h0 { font-size: " + (size + 12) + "; font-weight: bold; }");

    JEditorPane editorPane = new JEditorPane();
    editorPane.setEditable(false);
    editorPane.setOpaque(false);
    editorPane.setBorder(null);
    editorPane.setContentType("text/html");
    editorPane.setEditorKit(kit);
    editorPane.addHyperlinkListener(HelpIdAwareLinkListener.getInstance());

    return editorPane;
  }

  public void showPlugins(@NotNull List<ListPluginComponent> selection) {
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
      MyPluginModel.removeProgress(getDescriptorForActions(), myIndicator);
      hideProgress(false, false);
    }

    if (component == null) {
      myPlugin = myUpdateDescriptor = myInstalledDescriptorForMarketplace = null;
      select(1, true);
      setEmptyState(multiSelection ? EmptyState.MULTI_SELECT : EmptyState.NONE_SELECTED);
    }
    else {
      boolean syncLoading = true;
      IdeaPluginDescriptor descriptor = component.getPluginDescriptor();
      if (descriptor instanceof PluginNode node) {
        if (!node.detailsLoaded()) {
          syncLoading = false;
          doLoad(component, () -> {
            MarketplaceRequests marketplace = MarketplaceRequests.getInstance();

            PluginNode pluginNode = marketplace.loadPluginDetails(node);
            if (pluginNode == null) {
              return;
            }

            loadAllPluginDetails(marketplace, node, pluginNode);
            component.setPluginDescriptor(pluginNode);
          });
        }
        else if (!node.isConverted() &&
                 (node.getScreenShots() == null || node.getReviewComments() == null || node.getDependencyNames() == null)) {
          syncLoading = false;
          doLoad(component, () -> {
            MarketplaceRequests marketplace = MarketplaceRequests.getInstance();

            if (node.getScreenShots() == null && node.getExternalPluginIdForScreenShots() != null) {
              IntellijPluginMetadata metadata = marketplace.loadPluginMetadata(node.getExternalPluginIdForScreenShots());
              if (metadata != null) {
                if (metadata.getScreenshots() != null) {
                  node.setScreenShots(metadata.getScreenshots());
                }
                metadata.toPluginNode(node);
              }
            }
            if (node.getReviewComments() == null) {
              loadReviews(marketplace, node, node);
            }
            if (node.getDependencyNames() == null) {
              loadDependencyNames(marketplace, node);
            }
          });
        }
      }
      else if (!descriptor.isBundled() && component.getInstalledPluginMarketplaceNode() == null) {
        syncLoading = false;
        doLoad(component, () -> {
          MarketplaceRequests marketplace = MarketplaceRequests.getInstance();
          PluginNode node = marketplace.getLastCompatiblePluginUpdate(component.getPluginDescriptor().getPluginId());

          if (node != null) {
            List<IdeCompatibleUpdate> update =
              MarketplaceRequests.getLastCompatiblePluginUpdate(Set.of(component.getPluginDescriptor().getPluginId()));
            if (!update.isEmpty()) {
              IdeCompatibleUpdate compatibleUpdate = update.get(0);
              node.setExternalPluginId(compatibleUpdate.getExternalPluginId());
              node.setExternalUpdateId(compatibleUpdate.getExternalUpdateId());
            }

            PluginNode fullNode = marketplace.loadPluginDetails(node);
            if (fullNode != null) {
              loadAllPluginDetails(marketplace, node, fullNode);
              component.setInstalledPluginMarketplaceNode(fullNode);
            }
          }
        });
      }

      if (syncLoading) {
        showPluginImpl(component.getPluginDescriptor(), component.myUpdateDescriptor);
        PluginManagerUsageCollector.pluginCardOpened(component.getPluginDescriptor(), component.getGroup());
      }
    }
  }

  public static void loadAllPluginDetails(@NotNull MarketplaceRequests marketplace,
                                          @NotNull PluginNode node,
                                          @NotNull PluginNode resultNode) {
    if (node.getSuggestedFeatures() != null) {
      resultNode.setSuggestedFeatures(node.getSuggestedFeatures());
    }

    IntellijPluginMetadata metadata = marketplace.loadPluginMetadata(node);
    if (metadata != null) {
      if (metadata.getScreenshots() != null) {
        resultNode.setScreenShots(metadata.getScreenshots());
        resultNode.setExternalPluginIdForScreenShots(node.getExternalPluginId());
      }
      metadata.toPluginNode(resultNode);
    }

    loadReviews(marketplace, node, resultNode);
    loadDependencyNames(marketplace, resultNode);
  }

  private static void loadReviews(@NotNull MarketplaceRequests marketplace, @NotNull PluginNode node, @NotNull PluginNode resultNode) {
    PageContainer<PluginReviewComment> reviewComments = new PageContainer<>(20, 0);
    List<PluginReviewComment> items = marketplace.loadPluginReviews(node, reviewComments.getNextPage());
    if (items != null) {
      reviewComments.addItems(items);
    }
    resultNode.setReviewComments(reviewComments);
  }

  private static void loadDependencyNames(@NotNull MarketplaceRequests marketplace, @NotNull PluginNode resultNode) {
    List<String> dependencyNames = resultNode.getDependencies().stream()
      .filter(dependency -> !dependency.isOptional())
      .map(IdeaPluginDependency::getPluginId)
      .filter(PluginDetailsPageComponent::isNotPlatformModule)
      .map(pluginId -> {
        IdeaPluginDescriptorImpl existingPlugin = PluginManagerCore.findPlugin(pluginId);
        if (existingPlugin != null) return existingPlugin.getName();

        PluginNode pluginFromMarketplace = marketplace.getLastCompatiblePluginUpdate(pluginId);
        return pluginFromMarketplace == null ? pluginId.getIdString() : pluginFromMarketplace.getName();
      })
      .toList();

    resultNode.setDependencyNames(dependencyNames);
  }

  private void doLoad(@NotNull ListPluginComponent component, @NotNull Runnable task) {
    startLoading();
    ProcessIOExecutorService.INSTANCE.execute(() -> {
      task.run();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (myShowComponent == component) {
          stopLoading();
          showPluginImpl(component.getPluginDescriptor(), component.myUpdateDescriptor);
          PluginManagerUsageCollector.pluginCardOpened(component.getPluginDescriptor(), component.getGroup());
        }
      }, ModalityState.stateForComponent(component));
    });
  }

  public void showPluginImpl(@NotNull IdeaPluginDescriptor pluginDescriptor, @Nullable IdeaPluginDescriptor updateDescriptor) {
    myPlugin = pluginDescriptor;
    PluginManagementPolicy policy = PluginManagementPolicy.getInstance();
    myUpdateDescriptor = updateDescriptor != null && policy.canEnablePlugin(updateDescriptor) ? updateDescriptor : null;
    myIsPluginCompatible = PluginManagerCore.INSTANCE.getIncompatiblePlatform(pluginDescriptor) == null;
    myIsPluginAvailable = myIsPluginCompatible && policy.canEnablePlugin(updateDescriptor);
    if (myMarketplace && myMultiTabs) {
      myInstalledDescriptorForMarketplace = PluginManagerCore.findPlugin(myPlugin.getPluginId());
      myNameAndButtons.setProgressDisabledButton(myUpdateDescriptor == null ? myInstallButton : myUpdateButton);
    }
    showPlugin();

    select(0, true);

    String suggestedCommercialIde = null;

    if (myPlugin instanceof PluginNode) {
      suggestedCommercialIde = ((PluginNode)myPlugin).getSuggestedCommercialIde();
      if (suggestedCommercialIde != null) {
        myInstallButton.setVisible(false);
      }
    }

    if (myPlugin != null) {
      myCustomizer.processShowPlugin(myPlugin);
    }

    mySuggestedIdeBanner.suggestIde(suggestedCommercialIde, myPlugin.getPluginId());
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
      case MULTI_SELECT -> {
        text.setText(IdeBundle.message("plugins.configurable.several.plugins"));
        text.appendSecondaryText(IdeBundle.message("plugins.configurable.one.plugin.details"), StatusText.DEFAULT_ATTRIBUTES, null);
      }
      case NONE_SELECTED -> text.setText(IdeBundle.message("plugins.configurable.plugin.details"));
      case PROGRESS -> {
        myLoadingIcon.setVisible(true);
        myLoadingIcon.resume();
      }
    }
  }

  private void showPlugin() {
    @NlsSafe String text = "<html><span>" + requireNonNull(myPlugin).getName() + "</span></html>";
    myNameComponent.setText(text);
    myNameComponent.setForeground(null);
    updateNotifications();
    updateIcon();

    if (myErrorComponent != null) {
      myErrorComponent.setVisible(false);
    }

    updateButtons();

    IdeaPluginDescriptor plugin = getDescriptorForActions();
    String version = plugin.getVersion();
    if (plugin.isBundled() && !plugin.allowBundledUpdate()) {
      version = IdeBundle.message("plugin.version.bundled") + (Strings.isEmptyOrSpaces(version) ? "" : " " + version);
    }
    if (myUpdateDescriptor != null) {
      version = NewUiUtil.getVersion(plugin, myUpdateDescriptor);
    }

    boolean isVersion = !Strings.isEmptyOrSpaces(version);

    if (myVersion != null) {
      myVersion.setText(version);
      myVersionSize.setText(version);
      myVersion.setPreferredSize(
        new Dimension(myVersionSize.getPreferredSize().width + JBUIScale.scale(4), myVersionSize.getPreferredSize().height));

      myVersion.setVisible(isVersion);
    }

    if (myVersion1 != null) {
      myVersion1.setText(version);
      myVersion1.setVisible(isVersion);
    }

    if (myVersion2 != null) {
      myVersion2.setText(IdeBundle.message("plugins.configurable.version.0", version));
      myVersion2.setVisible(isVersion);
    }

    myTagPanel.setTags(PluginManagerConfigurable.getTags(myPlugin));

    if (myMarketplace) {
      showMarketplaceData(myPlugin);
      updateMarketplaceTabsVisible(myPlugin instanceof PluginNode node && !node.isConverted());
    }
    else {
      PluginNode node = getInstalledPluginMarketplaceNode();
      updateMarketplaceTabsVisible(node != null);
      if (node != null) {
        showMarketplaceData(node);
      }
      updateEnabledForProject();
    }

    String vendor = myPlugin.isBundled() ? null : Strings.trim(myPlugin.getVendor());
    String organization = myPlugin.isBundled() ? null : Strings.trim(myPlugin.getOrganization());
    if (!Strings.isEmptyOrSpaces(organization)) {
      myAuthor.show(organization, () -> mySearchListener.linkSelected(
        null,
        SearchWords.VENDOR.getValue() + (organization.indexOf(' ') == -1 ? organization : '\"' + organization + "\"")
      ));
    }
    else if (!Strings.isEmptyOrSpaces(vendor)) {
      myAuthor.show(vendor, null);
    }
    else {
      myAuthor.hide();
    }

    showLicensePanel();

    if (myPlugin.isBundled() && !myPlugin.allowBundledUpdate() || !isPluginFromMarketplace()) {
      myHomePage.hide();
    } else {
      myHomePage.showWithBrowseUrl(
        IdeBundle.message("plugins.configurable.plugin.homepage.link"),
        true,
        () -> MarketplaceUrls.getPluginHomepage(myPlugin.getPluginId())
      );
    }

    if (myDate != null) {
      IdeaPluginDescriptor pluginNode = myUpdateDescriptor != null ? myUpdateDescriptor : myPlugin;
      String date = pluginNode instanceof PluginNode ? ((PluginNode)pluginNode).getPresentableDate() : null;
      myDate.setText(myMultiTabs ? IdeBundle.message("plugins.configurable.release.date.0", date) : date);
      myDate.setVisible(date != null);
    }

    if (mySuggestedFeatures != null) {
      String feature = null;
      if (myMarketplace && myPlugin instanceof PluginNode node) {
        feature = ContainerUtil.getFirstItem(node.getSuggestedFeatures());
      }
      mySuggestedFeatures.setSuggestedText(feature);
    }

    for (JBScrollPane scrollPane : myScrollPanes) {
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

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

    if (myChangeNotesEmptyState != null) {
      String message = IdeBundle.message("plugins.configurable.notes.empty.text",
                                         StringUtil.defaultIfEmpty(StringUtil.defaultIfEmpty(organization, vendor), IdeBundle.message(
                                           "plugins.configurable.notes.empty.text.default.vendor")));
      myChangeNotesEmptyState.getEmptyText().setText(message);
    }

    if (myImagesComponent != null) {
      PluginNode node = getInstalledPluginMarketplaceNode();
      myImagesComponent.show(node == null ? myPlugin : node);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      IdeEventQueue.getInstance().flushQueue();
      for (JBScrollPane scrollPane : myScrollPanes) {
        ((JBScrollBar)scrollPane.getVerticalScrollBar()).setCurrentValue(0);
      }
    }, ModalityState.any());

    if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
      showProgress();
    }
    else {
      fullRepaint();
    }
  }

  private void showMarketplaceData(@Nullable IdeaPluginDescriptor descriptor) {
    String rating = null;
    String downloads = null;
    String size = null;
    Collection<String> requiredPluginNames = emptyList();

    if (descriptor instanceof PluginNode pluginNode) {
      rating = pluginNode.getPresentableRating();
      downloads = pluginNode.getPresentableDownloads();
      size = pluginNode.getPresentableSize();

      if (myReviewPanel != null) {
        updateReviews(pluginNode);
      }

      updateUrlComponent(myForumUrl, "plugins.configurable.forum.url", pluginNode.getForumUrl());
      updateUrlComponent(myLicenseUrl, "plugins.configurable.license.url", pluginNode.getLicenseUrl());
      updateUrlComponent(myBugtrackerUrl, "plugins.configurable.bugtracker.url", pluginNode.getBugtrackerUrl());
      updateUrlComponent(myDocumentationUrl, "plugins.configurable.documentation.url", pluginNode.getDocumentationUrl());
      updateUrlComponent(mySourceCodeUrl, "plugins.configurable.source.code", pluginNode.getSourceCodeUrl());

      myVendorInfoPanel.show(pluginNode);

      requiredPluginNames = pluginNode.getDependencyNames() != null ? pluginNode.getDependencyNames() : emptyList();
    }

    myRating.setText(myMultiTabs ? IdeBundle.message("plugins.configurable.rate.0", rating) : rating);
    myRating.setVisible(rating != null);

    myDownloads.setText(myMultiTabs ? IdeBundle.message("plugins.configurable.downloads.0", downloads) : downloads);
    myDownloads.setVisible(downloads != null);

    mySize.setText(IdeBundle.message("plugins.configurable.size.0", size));
    mySize.setVisible(size != null);

    myRequiredPlugins.setText(IdeBundle.message("plugins.configurable.required.plugins.0",
                                                StringUtil.join(ContainerUtil.map(requiredPluginNames, x -> "    • " + x), "\n")));
    myRequiredPlugins.setVisible(!requiredPluginNames.isEmpty());
  }

  private void updateMarketplaceTabsVisible(boolean show) {
    if (!show && myReviewPanel != null) {
      myReviewPanel.clear();
    }
    if (!show && myTabbedPane.getSelectedIndex() > 1) {
      myTabbedPane.setSelectedIndex(0);
    }
    myTabbedPane.setEnabledAt(2, show); // review
    myTabbedPane.setEnabledAt(3, show); // additional info
  }

  private @Nullable PluginNode getInstalledPluginMarketplaceNode() {
    return myShowComponent == null ? null : myShowComponent.getInstalledPluginMarketplaceNode();
  }

  private static boolean isNotPlatformModule(@NotNull PluginId pluginId) {
    if ("com.intellij".equals(pluginId.getIdString())) return false;

    return !PluginManagerCore.isModuleDependency(pluginId);
  }

  private void updateReviews(@NotNull PluginNode pluginNode) {
    PageContainer<PluginReviewComment> comments = pluginNode.getReviewComments();

    myReviewPanel.clear();
    if (comments != null) {
      myReviewPanel.addComments(comments.getItems());
    }

    myReviewNextPageButton.setIcon(null);
    myReviewNextPageButton.setEnabled(true);
    myReviewNextPageButton.setVisible(comments != null && comments.isNextPage());
  }

  private static void updateUrlComponent(@Nullable LinkPanel panel, @NotNull String messageKey, @Nullable String url) {
    if (panel != null) {
      if (StringUtil.isEmpty(url)) {
        panel.hide();
      }
      else {
        panel.showWithBrowseUrl(IdeBundle.message(messageKey), false, () -> url);
      }
    }
  }

  private @NotNull SelectionBasedPluginModelAction.UninstallAction<PluginDetailsPageComponent> createUninstallAction() {
    return new SelectionBasedPluginModelAction.UninstallAction<>(
      myPluginModel, false, this, List.of(this),
      PluginDetailsPageComponent::getDescriptorForActions, () -> {
      updateNotifications();
    });
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
    IdeaPluginDescriptor plugin = getDescriptorForActions();
    String productCode = plugin.getProductCode();
    if (plugin.isBundled() || LicensePanel.isEA2Product(productCode)) {
      myLicensePanel.hideWithChildren();
      return;
    }
    if (productCode == null) {
      if (myUpdateDescriptor != null && myUpdateDescriptor.getProductCode() != null &&
          !LicensePanel.isEA2Product(myUpdateDescriptor.getProductCode())) {
        myLicensePanel.setText(IdeBundle.message("label.next.plugin.version.is"), true, false);
        myLicensePanel.showBuyPlugin(() -> myUpdateDescriptor, true);
        myLicensePanel.setVisible(true);
      }
      else {
        myLicensePanel.hideWithChildren();
      }
    }
    else if (myMarketplace) {
      String message;
      if (plugin instanceof PluginNode && ((PluginNode)plugin).getTags().contains(Tags.Freemium.name())) {
        message = IdeBundle.message("label.install.a.limited.functionality.for.free");
      }
      else {
        message = IdeBundle.message("label.use.the.trial.for.up.to.30.days.or");
      }
      myLicensePanel.setText(message, false, false);
      myLicensePanel.showBuyPlugin(() -> plugin, false);

      // if the plugin requires commercial IDE, we do not show trial/price message
      boolean requiresCommercialIde = plugin instanceof PluginNode
                                      && ((PluginNode)plugin).getSuggestedCommercialIde() != null;
      myLicensePanel.setVisible(!requiresCommercialIde);
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
    if (!myIsPluginAvailable) {
      myRestartButton.setVisible(false);
      myInstallButton.setVisible(false);
      myUpdateButton.setVisible(false);
      myGearButton.setVisible(false);
      if (myMultiTabs) {
        myEnableDisableButton.setVisible(false);
      }
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
      if (myMultiTabs) {
        if (installed || myInstalledDescriptorForMarketplace == null) {
          myGearButton.setVisible(false);
          myEnableDisableButton.setVisible(false);
        }
        else {
          boolean[] state = getDeletedState(myInstalledDescriptorForMarketplace);
          boolean uninstalled = state[0];
          boolean uninstalledWithoutRestart = state[1];

          myInstallButton.setVisible(false);

          if (uninstalled) {
            if (uninstalledWithoutRestart) {
              myRestartButton.setVisible(false);
              myInstallButton.setVisible(true);
              myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
            }
            else {
              myRestartButton.setVisible(true);
            }
          }

          boolean bundled = myInstalledDescriptorForMarketplace.isBundled();
          myEnableDisableController.update();
          myGearButton.setVisible(!uninstalled && !bundled);
          myEnableDisableButton.setVisible(bundled);
          myUpdateButton.setVisible(!uninstalled && myUpdateDescriptor != null && !installedWithoutRestart);
          updateEnableForNameAndIcon();
          updateErrors();
        }
      }
      else {
        myGearButton.setVisible(false);
      }
    }
    else {
      myInstallButton.setVisible(false);

      boolean[] state = getDeletedState(myPlugin);
      boolean uninstalled = state[0];
      boolean uninstalledWithoutRestart = state[1];

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
      if (myEnableDisableController != null) {
        myEnableDisableController.update();
      }
      if (myMultiTabs) {
        boolean bundled = myPlugin.isBundled();
        myGearButton.setVisible(!uninstalled && !bundled);
        myEnableDisableButton.setVisible(bundled);
      }
      else {
        myGearButton.setVisible(!uninstalled);
      }

      updateEnableForNameAndIcon();
      updateErrors();
    }
  }

  private static boolean[] getDeletedState(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();

    boolean uninstalled = NewUiUtil.isDeleted(descriptor);
    boolean uninstalledWithoutRestart = InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId);
    if (!uninstalled) {
      InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
      uninstalled = pluginsState.wasInstalled(pluginId) || pluginsState.wasUpdated(pluginId);
    }

    return new boolean[]{uninstalled, uninstalledWithoutRestart};
  }

  private void updateIcon() {
    updateIcon(myPluginModel.getErrors(getDescriptorForActions()));
  }

  private void updateIcon(@NotNull List<? extends HtmlChunk> errors) {
    if (myIconLabel == null) {
      return;
    }

    boolean hasErrors = !myMarketplace && !errors.isEmpty();

    myIconLabel.setEnabled(myMarketplace || myPluginModel.isEnabled(myPlugin));
    myIconLabel.setIcon(myPluginModel.getIcon(myPlugin, true, hasErrors, false));
    myIconLabel.setDisabledIcon(myPluginModel.getIcon(myPlugin, true, hasErrors, true));
  }

  private void updateErrors() {
    @NotNull List<? extends HtmlChunk> errors = myPluginModel.getErrors(getDescriptorForActions());
    updateIcon(errors);
    myErrorComponent.setErrors(errors, this::handleErrors);
  }

  private void handleErrors() {
    myPluginModel.enableRequiredPlugins(getDescriptorForActions());

    updateIcon();
    updateEnabledState();
    fullRepaint();
  }

  public void showProgress() {
    myIndicator = new OneLineProgressIndicator(false);
    myIndicator.setCancelRunnable(() -> myPluginModel.finishInstall(getDescriptorForActions(), null, false, false, true));
    myNameAndButtons.setProgressComponent(null, myIndicator.createBaselineWrapper());

    MyPluginModel.addProgress(getDescriptorForActions(), myIndicator);

    fullRepaint();
  }

  private void fullRepaint() {
    doLayout();
    revalidate();
    repaint();
  }

  public void hideProgress(boolean success, boolean restartRequired) {
    myIndicator = null;
    myNameAndButtons.removeProgressComponent();

    if (success) {
      if (restartRequired) {
        updateAfterUninstall(true);
      }
      else {
        if (myInstallButton != null) {
          myInstallButton.setEnabled(false, IdeBundle.message("plugin.status.installed"));
          if (myMultiTabs && myInstallButton.isVisible()) {
            myInstalledDescriptorForMarketplace = PluginManagerCore.findPlugin(myPlugin.getPluginId());
            if (myInstalledDescriptorForMarketplace != null) {
              myInstallButton.setVisible(false);
              myVersion1.setText(myInstalledDescriptorForMarketplace.getVersion());
              myVersion1.setVisible(true);
              updateEnabledState();
              return;
            }
          }
        }
        if (myUpdateButton.isVisible()) {
          myUpdateButton.setEnabled(false);
          myUpdateButton.setText(IdeBundle.message("plugin.status.installed"));
        }
        myEnableDisableButton.setVisible(false);
      }
    }

    fullRepaint();
  }

  private void updateEnableForNameAndIcon() {
    boolean enabled = myPluginModel.isEnabled(getDescriptorForActions());
    myNameComponent.setForeground(enabled ? null : ListPluginComponent.DisabledColor);
    if (myIconLabel != null) {
      myIconLabel.setEnabled(enabled);
    }
  }

  public void updateEnabledState() {
    if ((myMarketplace && myInstalledDescriptorForMarketplace == null) || myPlugin == null) {
      return;
    }

    if (!myPluginModel.isUninstalled(getDescriptorForActions())) {
      if (myEnableDisableController != null) {
        myEnableDisableController.update();
      }
      if (myMultiTabs) {
        boolean bundled = getDescriptorForActions().isBundled();
        myGearButton.setVisible(!bundled);
        myEnableDisableButton.setVisible(bundled);
      }
      else {
        myGearButton.setVisible(true);
      }
    }

    updateEnableForNameAndIcon();
    updateErrors();
    updateEnabledForProject();

    myUpdateButton.setVisible(myUpdateDescriptor != null);

    fullRepaint();
  }

  public void updateAfterUninstall(boolean showRestart) {
    myInstallButton.setVisible(false);
    myUpdateButton.setVisible(false);
    myGearButton.setVisible(false);
    if (myEnableDisableButton != null) {
      myEnableDisableButton.setVisible(false);
    }
    myRestartButton.setVisible(myIsPluginAvailable && showRestart);

    if (!showRestart && InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(getDescriptorForActions().getPluginId())) {
      myInstallButton.setVisible(true);
      myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"));
    }
  }

  private void updateEnabledForProject() {
    if (myEnabledForProject != null) {
      PluginEnabledState state = myPluginModel.getState(requireNonNull(myPlugin));
      myEnabledForProject.setText(state.getPresentableText());
      myEnabledForProject.setIcon(AllIcons.General.ProjectConfigurable);
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

  private @Nullable @Nls String getDescription() {
    PluginNode node = getInstalledPluginMarketplaceNode();
    if (node != null) {
      String description = node.getDescription();
      if (!Strings.isEmptyOrSpaces(description)) {
        return description;
      }
    }
    String description = myPlugin.getDescription();
    return Strings.isEmptyOrSpaces(description) ? null : description;
  }

  private @Nullable @NlsSafe String getChangeNotes() {
    if (myUpdateDescriptor != null) {
      String notes = myUpdateDescriptor.getChangeNotes();
      if (!Strings.isEmptyOrSpaces(notes)) {
        return notes;
      }
    }
    String notes = myPlugin.getChangeNotes();
    if (!Strings.isEmptyOrSpaces(notes)) {
      return notes;
    }
    PluginNode node = getInstalledPluginMarketplaceNode();
    if (node != null) {
      String changeNotes = node.getChangeNotes();
      if (!Strings.isEmptyOrSpaces(changeNotes)) {
        return changeNotes;
      }
    }
    return null;
  }

  private static @NotNull BorderLayoutPanel createNotificationPanel(@NotNull Icon icon, @NotNull @Nls String message) {
    BorderLayoutPanel panel = createBaseNotificationPanel();

    JBLabel notificationLabel = new JBLabel();
    notificationLabel.setIcon(icon);
    notificationLabel.setVerticalTextPosition(SwingConstants.TOP);
    notificationLabel.setText(HtmlChunk.html().addText(message).toString());

    panel.addToCenter(notificationLabel);
    return panel;
  }

  private static @NotNull BorderLayoutPanel createBaseNotificationPanel() {
    BorderLayoutPanel panel = new BorderLayoutPanel();
    Border customLine = JBUI.Borders.customLine(JBUI.CurrentTheme.Banner.INFO_BACKGROUND, 1, 0, 1, 0);
    panel.setBorder(JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true));
    panel.setBackground(JBUI.CurrentTheme.Banner.INFO_BACKGROUND);
    return panel;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessiblePluginDetailsPageComponent();
    }
    return accessibleContext;
  }

  protected class AccessiblePluginDetailsPageComponent extends AccessibleJComponent {
    @Override
    public String getAccessibleName() {
      if (myPlugin == null) {
        return IdeBundle.message("plugins.configurable.plugin.details.page.accessible.name");
      }
      else {
        return IdeBundle.message("plugins.configurable.plugin.details.page.accessible.name.0", myPlugin.getName());
      }
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibilityUtils.GROUPED_ELEMENTS;
    }
  }
}
