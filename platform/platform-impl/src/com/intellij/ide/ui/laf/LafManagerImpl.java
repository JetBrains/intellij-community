// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.actions.QuickChangeLookAndFeel;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.*;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.DefaultLinkButtonUI;
import com.intellij.ui.popup.OurHeavyWeightPopup;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.util.*;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.ui.*;
import kotlin.Lazy;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.*;
import java.util.function.BooleanSupplier;

@State(
  name = "LafManager",
  storages = @Storage(value = "laf.xml", usePathMacroManager = false),
  category = SettingsCategory.UI,
  reportStatistic = false
)
public final class LafManagerImpl extends LafManager implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(LafManagerImpl.class);

  private static final @NonNls String ELEMENT_LAF = "laf";
  private static final @NonNls String ELEMENT_PREFERRED_LIGHT_LAF = "preferred-light-laf";
  private static final @NonNls String ELEMENT_PREFERRED_DARK_LAF = "preferred-dark-laf";
  private static final @NonNls String ATTRIBUTE_AUTODETECT = "autodetect";
  private static final @NonNls String ATTRIBUTE_CLASS_NAME = "class-name";
  private static final @NonNls String ATTRIBUTE_THEME_NAME = "themeId";

  private static final String HIGH_CONTRAST_THEME_ID = "JetBrainsHighContrastTheme";
  private static final String DARCULA_EDITOR_THEME_KEY = "Darcula.SavedEditorTheme";
  private static final String DEFAULT_EDITOR_THEME_KEY = "Default.SavedEditorTheme";

  private static final LafReference SEPARATOR = new LafReference("", null, null);

  private static final @PropertyKey(resourceBundle = IdeBundle.BUNDLE) @NonNls String[] fileChooserTextKeys = {
    "FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
    "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"
  };

  private final EventDispatcher<LafManagerListener> eventDispatcher = EventDispatcher.create(LafManagerListener.class);

  private final SynchronizedClearableLazy<List<UIManager.LookAndFeelInfo>> lafList = new SynchronizedClearableLazy<>(() -> {
    Activity activity = StartUpMeasurer.startActivity("compute LaF list", ActivityCategory.DEFAULT);
    List<UIManager.LookAndFeelInfo> infos = computeLafList();
    activity.end();
    return infos;
  });

  private final SynchronizedClearableLazy<UIManager.LookAndFeelInfo> defaultDarkLaf = new SynchronizedClearableLazy<>(() -> {
    String lafInfoFQN = ApplicationInfoEx.getInstanceEx().getDefaultDarkLaf();
    UIManager.LookAndFeelInfo lookAndFeelInfo = lafInfoFQN == null ? null : createLafInfo(lafInfoFQN);
    return lookAndFeelInfo != null ? lookAndFeelInfo : new DarculaLookAndFeelInfo();
  });

  private final Map<Object, Object> ourDefaults = (UIDefaults)UIManager.getDefaults().clone();

  private UIManager.LookAndFeelInfo myCurrentLaf;
  private @Nullable UIManager.LookAndFeelInfo preferredLightLaf;
  private @Nullable UIManager.LookAndFeelInfo preferredDarkLaf;

  private final Map<LafReference, Map<String, Object>> myStoredDefaults = new HashMap<>();

  // A constant from Mac OS X implementation. See CPlatformWindow.WINDOW_ALPHA
  private static final String WINDOW_ALPHA = "Window.alpha";

  private static final Map<String, String> ourLafClassesAliases = Map.of("idea.dark.laf.classname", DarculaLookAndFeelInfo.CLASS_NAME);

  // allowing other plugins to change the order of the LaFs (used by Rider)
  public static @NotNull Map<String, Integer> getLafNameOrder() {
    return UiThemeProviderListManager.Companion.getLafNameOrder();
  }

  public static void setLafNameOrder(@NotNull Map<String, Integer> value) {
    UiThemeProviderListManager.Companion.setLafNameOrder(value);
  }

  private final SynchronizedClearableLazy<CollectionComboBoxModel<LafReference>> myLafComboBoxModel =
    new SynchronizedClearableLazy<>(() -> new LafComboBoxModel());

  private final Lazy<ActionToolbar> settingsToolbar = new SynchronizedClearableLazy<>(() -> {
    DefaultActionGroup group = new DefaultActionGroup(new PreferredLafAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true);
    toolbar.setTargetComponent(toolbar.getComponent());
    toolbar.getComponent().setOpaque(false);
    return toolbar;
  });

  // SystemDarkThemeDetector must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
  private @Nullable SystemDarkThemeDetector lafDetector;

  private boolean isFirstSetup = true;
  private boolean isUpdatingPlugin = false;
  private @Nullable String themeIdBeforePluginUpdate = null;
  private boolean autodetect;

  public UIManager.LookAndFeelInfo getDefaultLightLaf() {
    return UiThemeProviderListManager.getInstance().findJetBrainsLightTheme();
  }

  public UIManager.LookAndFeelInfo getDefaultDarkLaf() {
    return defaultDarkLaf.getValue();
  }

  private static @Nullable UIManager.LookAndFeelInfo createLafInfo(@NotNull String fqn) {
    try {
      Class<?> lafInfoClass = Class.forName(fqn);
      return (UIManager.LookAndFeelInfo)lafInfoClass.getDeclaredConstructor().newInstance();
    }
    catch (Throwable e) {
      return null;
    }
  }

  private @NotNull List<UIManager.LookAndFeelInfo> computeLafList() {
    List<UIManager.LookAndFeelInfo> lafList = new ArrayList<>();
    lafList.add(defaultDarkLaf.getValue());

    if (!SystemInfoRt.isMac) {
      for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
        String name = laf.getName();
        if (!"Metal".equalsIgnoreCase(name)
            && !"CDE/Motif".equalsIgnoreCase(name)
            && !"Nimbus".equalsIgnoreCase(name)
            && !name.startsWith("Windows")
            && !"GTK+".equalsIgnoreCase(name)
            && !name.startsWith("JGoodies")) {
          lafList.add(laf);
        }
      }
    }

    LafProvider.EP_NAME.forEachExtensionSafe(provider -> {
      lafList.add(provider.getLookAndFeelInfo());
    });
    lafList.addAll(UiThemeProviderListManager.getInstance().getLaFs());
    UiThemeProviderListManager.Companion.sortThemes(lafList);
    return lafList;
  }

  @SuppressWarnings("removal")
  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener) {
    eventDispatcher.addListener(listener);
  }

  @SuppressWarnings("removal")
  @Override
  public void removeLafManagerListener(@NotNull LafManagerListener listener) {
    eventDispatcher.removeListener(listener);
  }

  @Override
  public void initializeComponent() {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
      UIManager.LookAndFeelInfo currentLaf = myCurrentLaf;
      assert currentLaf != null;
      if (currentLaf instanceof UIThemeBasedLookAndFeelInfo) {
        if (!((UIThemeBasedLookAndFeelInfo)currentLaf).isInitialised()) {
          doSetLaF(currentLaf, false);
        }
      }
      else {
        UIManager.LookAndFeelInfo laf = findLaf(currentLaf.getClassName());
        if (laf != null) {
          boolean needUninstall = StartupUiUtil.isUnderDarcula();
          // setup default LAF or one specified by readExternal
          doSetLaF(laf, false);
          updateWizardLAF(needUninstall);
        }
      }

      selectComboboxModel();

      updateUI();
      // must be after updateUI
      isFirstSetup = false;
      detectAndSyncLaf();

      Activity activity = StartUpMeasurer.startActivity("new ui configuration");
      ExperimentalUI.lookAndFeelChanged();
      activity.end();

      addThemeAndDynamicPluginListeners();
    });
  }

  private void addThemeAndDynamicPluginListeners() {
    UIThemeProvider.EP_NAME.addExtensionPointListener(new UiThemeEpListener(), this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        isUpdatingPlugin = isUpdate;
        if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo) {
          themeIdBeforePluginUpdate = ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().getId();
        }
        else {
          themeIdBeforePluginUpdate = null;
        }
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        isUpdatingPlugin = false;
        themeIdBeforePluginUpdate = null;
      }
    });
  }

  private void detectAndSyncLaf() {
    if (autodetect) {
      SystemDarkThemeDetector lafDetector = getOrCreateLafDetector();
      if (lafDetector.getDetectionSupported()) {
        lafDetector.check();
      }
    }
  }

  private void syncLaf(boolean systemIsDark) {
    if (!autodetect) {
      return;
    }

    boolean currentIsDark =
      myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().isDark() ||
      StartupUiUtil.isUnderDarcula();
    UIManager.LookAndFeelInfo expectedLaf;
    if (systemIsDark) {
      expectedLaf = preferredDarkLaf;
      if (expectedLaf == null) {
        expectedLaf = getDefaultDarkLaf();
      }
    }
    else {
      expectedLaf = preferredLightLaf;
      if (expectedLaf == null) {
        expectedLaf = getDefaultLightLaf();
      }
    }
    if (currentIsDark != systemIsDark || myCurrentLaf != expectedLaf) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(this, expectedLaf, true);
    }
  }

  public void updateWizardLAF(boolean wasUnderDarcula) {
    if (WelcomeWizardUtil.getWizardLAF() == null) {
      return;
    }

    if (StartupUiUtil.isUnderDarcula()) {
      DarculaInstaller.install();
    }
    else if (wasUnderDarcula) {
      DarculaInstaller.uninstall();
    }
    WelcomeWizardUtil.setWizardLAF(null);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void loadState(@NotNull Element element) {
    myCurrentLaf = loadLafState(element, ELEMENT_LAF);
    if (myCurrentLaf == null) {
      myCurrentLaf = loadDefaultLaf();
    }

    autodetect = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_AUTODETECT));
    preferredLightLaf = loadLafState(element, ELEMENT_PREFERRED_LIGHT_LAF);
    preferredDarkLaf = loadLafState(element, ELEMENT_PREFERRED_DARK_LAF);

    if (autodetect) {
      getOrCreateLafDetector();
    }
  }

  private @Nullable UIManager.LookAndFeelInfo loadLafState(@NotNull Element element, @NotNull @NonNls String attrName) {
    Element lafElement = element.getChild(attrName);
    if (lafElement == null) {
      return null;
    }
    return findLaf(lafElement.getAttributeValue(ATTRIBUTE_CLASS_NAME), lafElement.getAttributeValue(ATTRIBUTE_THEME_NAME));
  }

  private @Nullable UIManager.LookAndFeelInfo findLaf(@Nullable String lafClassName, @Nullable String themeId) {
    if ("JetBrainsLightTheme".equals(themeId)) {
      return getDefaultLightLaf();
    }

    if (lafClassName != null && ourLafClassesAliases.containsKey(lafClassName)) {
      lafClassName = ourLafClassesAliases.get(lafClassName);
    }

    if ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel".equals(lafClassName)) {
      return getDefaultLightLaf();
    }

    if (themeId != null) {
      for (UIManager.LookAndFeelInfo l : lafList.getValue()) {
        if (l instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)l).getTheme().getId().equals(themeId)) {
          return l;
        }
      }
    }

    UIManager.LookAndFeelInfo laf = null;
    if (lafClassName != null) {
      laf = findLaf(lafClassName);
    }

    if (laf == null && ("com.intellij.laf.win10.WinIntelliJLaf".equals(lafClassName) ||
                        "com.intellij.laf.macos.MacIntelliJLaf".equals(lafClassName))) {
      return getDefaultLightLaf();
    }

    return laf;
  }

  @Override
  public void noStateLoaded() {
    myCurrentLaf = loadDefaultLaf();
    preferredLightLaf = null;
    preferredDarkLaf = null;
    autodetect = false;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    element.setAttribute(ATTRIBUTE_AUTODETECT, Boolean.toString(autodetect));

    getLafState(element, ELEMENT_LAF, getCurrentLookAndFeel());

    if (preferredLightLaf != null && preferredLightLaf != getDefaultLightLaf()) {
      getLafState(element, ELEMENT_PREFERRED_LIGHT_LAF, preferredLightLaf);
    }

    if (preferredDarkLaf != null && preferredDarkLaf != defaultDarkLaf.getValue()) {
      getLafState(element, ELEMENT_PREFERRED_DARK_LAF, preferredDarkLaf);
    }

    return element;
  }

  private static void getLafState(@NotNull Element element, @NonNls String attrName, UIManager.LookAndFeelInfo laf) {
    if (laf instanceof TempUIThemeBasedLookAndFeelInfo) {
      laf = ((TempUIThemeBasedLookAndFeelInfo)laf).getPreviousLaf();
    }

    if (laf == null) {
      return;
    }

    String className = laf.getClassName();
    if (className != null) {
      Element child = new Element(attrName);
      child.setAttribute(ATTRIBUTE_CLASS_NAME, className);

      if (laf instanceof UIThemeBasedLookAndFeelInfo) {
        child.setAttribute(ATTRIBUTE_THEME_NAME, ((UIThemeBasedLookAndFeelInfo)laf).getTheme().getId());
      }
      element.addContent(child);
    }
  }

  @Override
  public UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels() {
    return lafList.getValue().toArray(new UIManager.LookAndFeelInfo[0]);
  }

  @Override
  public @NotNull CollectionComboBoxModel<LafReference> getLafComboBoxModel() {
    return myLafComboBoxModel.getValue();
  }

  private @NotNull List<LafReference> getAllReferences() {
    List<LafReference> result = new ArrayList<>();
    boolean addSeparator = false;
    Map<String, Integer> lafNameOrder = UiThemeProviderListManager.Companion.getLafNameOrder();
    int maxNameOrder = Collections.max(lafNameOrder.values());
    for (UIManager.LookAndFeelInfo info : lafList.getValue()) {
      if (addSeparator) {
        result.add(SEPARATOR);
        addSeparator = false;
      }
      result.add(createLafReference(info));
      if (Objects.equals(lafNameOrder.get(info.getName()), maxNameOrder)) {
        addSeparator = true;
      }
    }
    return result;
  }

  private void updateLafComboboxModel() {
    myLafComboBoxModel.drop();
  }

  private void selectComboboxModel() {
    if (myLafComboBoxModel.isInitialized()) {
      myLafComboBoxModel.getValue().setSelectedItem(createLafReference(myCurrentLaf));
    }
  }

  private static @NotNull LafReference createLafReference(UIManager.LookAndFeelInfo laf) {
    String themeId = null;
    if (laf instanceof UIThemeBasedLookAndFeelInfo) {
      themeId = ((UIThemeBasedLookAndFeelInfo) laf).getTheme().getId();
    }
    return new LafReference(laf.getName(), laf.getClassName(), themeId);
  }

  @Override
  public UIManager.LookAndFeelInfo findLaf(@NotNull LafReference reference) {
    return findLaf(reference.getClassName(), reference.getThemeId());
  }

  @Override
  public @Nullable UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return myCurrentLaf;
  }

  @Override
  public LafReference getLookAndFeelReference() {
    return createLafReference(getCurrentLookAndFeel());
  }

  @Override
  public ListCellRenderer<LafReference> getLookAndFeelCellRenderer() {
    return new LafCellRenderer();
  }

  @Override
  public @NotNull JComponent getSettingsToolbar() {
    return settingsToolbar.getValue().getComponent();
  }

  private @NotNull UIManager.LookAndFeelInfo loadDefaultLaf() {
    String wizardLafName = WelcomeWizardUtil.getWizardLAF();
    if (wizardLafName != null) {
      UIManager.LookAndFeelInfo laf = findLaf(wizardLafName);
      if (laf != null) {
        return laf;
      }
      LOG.error("Could not find wizard L&F: " + wizardLafName);
    }

    if (SystemInfoRt.isMac) {
      String className = DarculaLaf.class.getName();
      UIManager.LookAndFeelInfo laf = findLaf(className);
      if (laf != null) {
        return laf;
      }
      LOG.error("Could not find OS X L&F: " + className);
    }

    String appLafName = WelcomeWizardUtil.getDefaultLAF();
    if (appLafName != null) {
      UIManager.LookAndFeelInfo laf = findLaf(appLafName);
      if (laf != null) {
        return laf;
      }
      LOG.error("Could not find app L&F: " + appLafName);
    }

    // Use HighContrast theme for IDE in Windows if HighContrast desktop mode is set.
    if (SystemInfoRt.isWindows && Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == Boolean.TRUE) {
      for (UIManager.LookAndFeelInfo laf : lafList.getValue()) {
        if (laf instanceof UIThemeBasedLookAndFeelInfo &&
            HIGH_CONTRAST_THEME_ID.equals(((UIThemeBasedLookAndFeelInfo)laf).getTheme().getId())) {
          return laf;
        }
      }
    }

    String defaultLafName = DarculaLaf.class.getName();
    UIManager.LookAndFeelInfo laf = findLaf(defaultLafName);
    if (laf != null) {
      return laf;
    }

    throw new IllegalStateException("No default L&F found: " + defaultLafName);
  }

  private @Nullable UIManager.LookAndFeelInfo findLaf(@NotNull String className) {
    UIManager.LookAndFeelInfo defaultLightLaf = getDefaultLightLaf();
    if (defaultLightLaf.getClassName().equals(className)) {
      return defaultLightLaf;
    }
    else if (defaultDarkLaf.getValue().getClassName().equals(className)) {
      return defaultDarkLaf.getValue();
    }

    for (UIManager.LookAndFeelInfo l : lafList.getValue()) {
      if (!(l instanceof UIThemeBasedLookAndFeelInfo) && className.equals(l.getClassName())) {
        return l;
      }
    }
    return null;
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  @Override
  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme) {
    setLookAndFeelImpl(lookAndFeelInfo, !lockEditorScheme, true);
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  private void setLookAndFeelImpl(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo,
                                  boolean installEditorScheme,
                                  boolean processChangeSynchronously) {
    UIManager.LookAndFeelInfo oldLaf = myCurrentLaf;
    if (oldLaf != lookAndFeelInfo && oldLaf instanceof UIThemeBasedLookAndFeelInfo) {
      ((UIThemeBasedLookAndFeelInfo)oldLaf).dispose();
    }

    if (findLaf(lookAndFeelInfo.getClassName()) == null) {
      LOG.error("unknown LookAndFeel : " + lookAndFeelInfo);
      return;
    }

    if (doSetLaF(lookAndFeelInfo, installEditorScheme)) {
      return;
    }

    myCurrentLaf = lookAndFeelInfo;
    selectComboboxModel();

    if (!isFirstSetup && installEditorScheme) {
      if (processChangeSynchronously) {
        updateEditorSchemeIfNecessary(oldLaf, true);
      }
      else {
        ApplicationManager.getApplication().invokeLater(() -> updateEditorSchemeIfNecessary(oldLaf, false));
      }
    }
    isFirstSetup = false;
  }

  private boolean doSetLaF(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean installEditorScheme) {
    UIDefaults defaults = UIManager.getDefaults();
    defaults.clear();
    defaults.putAll(ourDefaults);
    if (!isFirstSetup) {
      SVGLoader.setContextColorPatcher(null);
      SVGLoader.setSelectionColorPatcherProvider(null);
    }

    // set L&F
    String lafClassName = lookAndFeelInfo.getClassName();
    if (DarculaLookAndFeelInfo.CLASS_NAME.equals(lafClassName)) {
      DarculaLaf laf = new DarculaLaf();
      try {
        UIManager.setLookAndFeel(laf);
        AppUIUtil.updateForDarcula(true);
        //if (lafNameOrder.containsKey(lookAndFeelInfo.getName())) {
        //  updateIconsUnderSelection(true);
        //}
      }
      catch (Exception e) {
        LOG.error(e);
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return true;
      }
    }
    else {
      // non default LAF
      try {
        LookAndFeel laf;
        if (lookAndFeelInfo instanceof PluggableLafInfo) {
          laf = ((PluggableLafInfo)lookAndFeelInfo).createLookAndFeel();
        }
        else {
          laf = (LookAndFeel)LafManagerImpl.class.getClassLoader().loadClass(lafClassName).getConstructor().newInstance();
          // avoid loading MetalLookAndFeel class here - check for UIThemeBasedLookAndFeelInfo first
          if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
            if (laf instanceof UserDataHolder) {
              UserDataHolder userDataHolder = (UserDataHolder)laf;
              userDataHolder.putUserData(UIUtil.LAF_WITH_THEME_KEY, Boolean.TRUE);
            }
            //if (lafNameOrder.containsKey(lookAndFeelInfo.getName()) && lookAndFeelInfo.getName().endsWith("Light")) {
            //  updateIconsUnderSelection(false);
            //}
          }
          else if (laf instanceof MetalLookAndFeel) {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
          }
        }

        UIManager.setLookAndFeel(laf);
      }
      catch (Exception e) {
        LOG.error(e);
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return true;
      }
    }

    if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
      try {
        UIThemeBasedLookAndFeelInfo themeInfo = (UIThemeBasedLookAndFeelInfo)lookAndFeelInfo;
        themeInfo.installTheme(UIManager.getLookAndFeelDefaults(), !installEditorScheme);

        //IntelliJ Light is the only theme which is, in fact, a LaF.
        if (!themeInfo.getName().equals("IntelliJ Light")) {
          defaults.put("Theme.name", themeInfo.getName());
        }
      }
      catch (Exception e) {
        LOG.error(e);
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return true;
      }
    }

    if (SystemInfoRt.isMac) {
      installMacOSXFonts(UIManager.getLookAndFeelDefaults());
    }
    else if (SystemInfoRt.isLinux) {
      installLinuxFonts(UIManager.getLookAndFeelDefaults());
    }
    return false;
  }

  private void updateEditorSchemeIfNecessary(UIManager.LookAndFeelInfo oldLaf, boolean processChangeSynchronously) {
    if (oldLaf instanceof TempUIThemeBasedLookAndFeelInfo || myCurrentLaf instanceof TempUIThemeBasedLookAndFeelInfo) {
      return;
    }
    if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo) {
      if (((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().getEditorSchemeName() != null) {
        return;
      }
    }

    boolean dark = StartupUiUtil.isUnderDarcula();
    EditorColorsManager editorColorManager = EditorColorsManager.getInstance();
    EditorColorsScheme current = editorColorManager.getGlobalScheme();
    boolean wasUITheme = oldLaf instanceof UIThemeBasedLookAndFeelInfo;
    if (dark != ColorUtil.isDark(current.getDefaultBackground()) || wasUITheme) {
      String targetScheme = dark ? DarculaLaf.NAME : EditorColorsScheme.DEFAULT_SCHEME_NAME;
      PropertiesComponent properties = PropertiesComponent.getInstance();
      String savedEditorThemeKey = dark ? DARCULA_EDITOR_THEME_KEY : DEFAULT_EDITOR_THEME_KEY;
      String toSavedEditorThemeKey = dark ? DEFAULT_EDITOR_THEME_KEY : DARCULA_EDITOR_THEME_KEY;
      String themeName =  properties.getValue(savedEditorThemeKey);
      if (themeName != null && editorColorManager.getScheme(themeName) != null) {
        targetScheme = themeName;
      }
      if (!wasUITheme) {
        properties.setValue(toSavedEditorThemeKey, current.getName(), dark ? EditorColorsScheme.DEFAULT_SCHEME_NAME : DarculaLaf.NAME);
      }

      EditorColorsScheme scheme = editorColorManager.getScheme(targetScheme);
      if (scheme != null) {
        ((EditorColorsManagerImpl)editorColorManager).setGlobalScheme(scheme, processChangeSynchronously);
      }
    }
    UISettings.getShadowInstance().fireUISettingsChanged();
    ActionToolbarImpl.updateAllToolbarsImmediately();
  }

  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in {@code UISettings}.
   */
  @Override
  public void updateUI() {
    UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();
    uiDefaults.put("LinkButtonUI", DefaultLinkButtonUI.class.getName());

    fixPopupWeight();
    fixMenuIssues(uiDefaults);

    StartupUiUtil.initInputMapDefaults(uiDefaults);

    uiDefaults.put("Button.defaultButtonFollowsFocus", Boolean.FALSE);
    uiDefaults.put("Balloon.error.textInsets", new JBInsets(3, 8, 3, 8).asUIResource());

    patchFileChooserStrings(uiDefaults);

    patchLafFonts(uiDefaults);

    patchTreeUI(uiDefaults);

    patchHiDPI(uiDefaults);

    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue());

    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS,
                   AppUIUtil.adjustFractionalMetrics(UISettings.Companion.getPreferredFractionalMetricsValue()));

    if (isFirstSetup) {
      ApplicationManager.getApplication().invokeLater(this::notifyLookAndFeelChanged);
    }
    else {
      ExperimentalUI.lookAndFeelChanged();
      notifyLookAndFeelChanged();

      for (Frame frame : Frame.getFrames()) {
        updateUI(frame);
      }
    }
  }

  private void notifyLookAndFeelChanged() {
    Activity activity = StartUpMeasurer.startActivity("lookAndFeelChanged event processing");
    ApplicationManager.getApplication().getMessageBus().syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(this);
    eventDispatcher.getMulticaster().lookAndFeelChanged(this);
    activity.end();
  }

  private static @NotNull FontUIResource getFont(String yosemite, int size, @JdkConstants.FontStyle int style) {
    if (SystemInfoRt.isMac) {
      // Text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      Font font = FontUtil.enableKerning(new Font(SystemInfo.isMacOSCatalina ? ".AppleSystemUIFont" : ".SF NS Text", style, size));
      if (!StartupUiUtil.isDialogFont(font)) {
        return new FontUIResource(font);
      }
    }
    return new FontUIResource(yosemite, style, size);
  }

  public static void installMacOSXFonts(UIDefaults defaults) {
    @SuppressWarnings("SpellCheckingInspection")
    final String face = "Helvetica Neue";
    // ui font
    StartupUiUtil.initFontDefaults(defaults, getFont(face, 13, Font.PLAIN));
    for (Object key : List.copyOf(defaults.keySet())) {
      if (!(key instanceof String) || !Strings.endsWithIgnoreCase(((String)key), "font")) {
        continue;
      }
      Object value = defaults.get(key);
      if (value instanceof FontUIResource) {
        FontUIResource font = (FontUIResource)value;
        if (font.getFamily().equals("Lucida Grande") || font.getFamily().equals("Serif")) {
          if (!key.toString().contains("Menu")) {
            defaults.put(key, getFont(face, font.getSize(), font.getStyle()));
          }
        }
      }
    }

    defaults.put("TableHeader.font", getFont(face, 11, Font.PLAIN));

    FontUIResource buttonFont = getFont("Helvetica Neue", 13, Font.PLAIN);
    defaults.put("Button.font", buttonFont);
    Font menuFont = getFont("Lucida Grande", 13, Font.PLAIN);
    defaults.put("Menu.font", menuFont);
    defaults.put("MenuItem.font", menuFont);
    defaults.put("MenuItem.acceleratorFont", menuFont);
    defaults.put("PasswordField.font", defaults.getFont("TextField.font"));
  }

  private static void installLinuxFonts(UIDefaults defaults) {
    defaults.put("MenuItem.acceleratorFont", defaults.get("MenuItem.font"));
  }

  private static void patchTreeUI(UIDefaults defaults) {
    defaults.put("TreeUI", DefaultTreeUI.class.getName());
    defaults.put("Tree.repaintWholeRow", true);
    if (isUnsupported(defaults.getIcon("Tree.collapsedIcon"))) {
      defaults.put("Tree.collapsedIcon", LafIconLookup.getIcon("treeCollapsed"));
      defaults.put("Tree.collapsedSelectedIcon", LafIconLookup.getSelectedIcon("treeCollapsed"));
    }
    if (isUnsupported(defaults.getIcon("Tree.expandedIcon"))) {
      defaults.put("Tree.expandedIcon", LafIconLookup.getIcon("treeExpanded"));
      defaults.put("Tree.expandedSelectedIcon", LafIconLookup.getSelectedIcon("treeExpanded"));
    }
  }

  /**
   * @param icon an icon retrieved from L&F
   * @return {@code true} if an icon is not specified or if it is declared in some Swing L&F
   * (such icons do not have a variant to paint in selected row)
   */
  private static boolean isUnsupported(@Nullable Icon icon) {
    String name = icon == null ? null : icon.getClass().getName();
    return name == null || name.startsWith("javax.swing.plaf.") || name.startsWith("com.sun.java.swing.plaf.");
  }

  private static void patchHiDPI(@NotNull UIDefaults defaults) {
    Object prevScaleVal = defaults.get("hidpi.scaleFactor");
    // used to normalize previously patched values
    float prevScale = prevScaleVal != null ? (Float)prevScaleVal : 1f;

    // fix predefined row height if default system font size is not expected
    float prevRowHeightScale = prevScaleVal != null || SystemInfoRt.isMac || SystemInfoRt.isWindows
                               ? prevScale
                               : JBUIScale.getFontScale(12f);
    patchRowHeight(defaults, "List.rowHeight", prevRowHeightScale);
    patchRowHeight(defaults, "Table.rowHeight", prevRowHeightScale);
    patchRowHeight(defaults, "Tree.rowHeight", prevRowHeightScale);

    if (prevScale == JBUIScale.scale(1f) && prevScaleVal != null) {
      return;
    }

    Set<String> intKeys = Set.of("Tree.leftChildIndent", "Tree.rightChildIndent", "SettingsTree.rowHeight");

    Set<String> dimensionKeys = Set.of("Slider.horizontalSize",
                                       "Slider.verticalSize",
                                       "Slider.minimumHorizontalSize",
                                       "Slider.minimumVerticalSize");

    for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
      Object value = entry.getValue();
      String key = entry.getKey().toString();
      if (value instanceof Dimension) {
        if (value instanceof UIResource || dimensionKeys.contains(key)) {
          entry.setValue(JBDimension.size((Dimension)value).asUIResource());
        }
      }
      else if (value instanceof Insets) {
        if (value instanceof UIResource) {
          entry.setValue(JBInsets.create(((Insets)value)).asUIResource());
        }
      }
      else if (value instanceof Integer) {
        if (key.endsWith(".maxGutterIconWidth") || intKeys.contains(key)) {
          int normValue = (int)((Integer)value / prevScale);
          entry.setValue(Integer.valueOf(JBUIScale.scale(normValue)));
        }
      }
    }
    defaults.put("hidpi.scaleFactor", JBUIScale.scale(1f));
  }

  private static void patchRowHeight(UIDefaults defaults, String key, float prevScale) {
    Object value = defaults.get(key);
    int rowHeight = value instanceof Integer ? (Integer)value : 0;
    if (!SystemInfoRt.isMac && !SystemInfoRt.isWindows &&
        (!LoadingState.APP_STARTED.isOccurred() || Registry.is("linux.row.height.disabled", true))) {
      rowHeight = 0;
    }
    else if (rowHeight <= 0) {
      LOG.warn(key + " = " + value + " in " + UIManager.getLookAndFeel().getName() + "; it may lead to performance degradation");
    }
    int custom = LoadingState.APP_STARTED.isOccurred() ? Registry.intValue("ide.override." + key, -1) : -1;
    defaults.put(key, custom >= 0 ? custom : rowHeight <= 0 ? 0 : JBUIScale.scale((int)(rowHeight / prevScale)));
  }

  private static void fixMenuIssues(@NotNull UIDefaults uiDefaults) {
    uiDefaults.put("Menu.arrowIcon", new DefaultMenuArrowIcon());
    uiDefaults.put("MenuItem.background", UIManager.getColor("Menu.background"));
  }

  /**
   * The following code is a trick! By default, Swing uses lightweight and "medium" weight
   * popups to show JPopupMenu. The code below force the creation of real heavyweight menus -
   * this increases speed of popups and allows getting rid of some drawing artifacts.
   */
  private static void fixPopupWeight() {
    int popupWeight = OurPopupFactory.WEIGHT_MEDIUM;
    String property = System.getProperty("idea.popup.weight");
    if (property != null) property = Strings.toLowerCase(property).trim();
    if (SystemInfoRt.isMac) {
      // force heavy weight popups under Leopard, otherwise they don't have shadow or any kind of border.
      popupWeight = OurPopupFactory.WEIGHT_HEAVY;
    }
    else if (property == null) {
      // use defaults if popup weight isn't specified
      if (SystemInfoRt.isWindows) {
        popupWeight = OurPopupFactory.WEIGHT_HEAVY;
      }
    }
    else {
      if ("light".equals(property)) {
        popupWeight = OurPopupFactory.WEIGHT_LIGHT;
      }
      else if ("heavy".equals(property)) {
        popupWeight = OurPopupFactory.WEIGHT_HEAVY;
      }
      else if (!"medium".equals(property)) {
        LOG.error("Illegal value of property \"idea.popup.weight\": " + property);
      }
    }

    PopupFactory factory = PopupFactory.getSharedInstance();
    if (!(factory instanceof OurPopupFactory)) {
      factory = new OurPopupFactory(factory);
      PopupFactory.setSharedInstance(factory);
    }
    PopupUtil.setPopupType(factory, popupWeight);
  }

  private static void patchFileChooserStrings(@NotNull UIDefaults defaults) {
    if (!defaults.containsKey(fileChooserTextKeys[0])) {
      // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
      for (String key : fileChooserTextKeys) {
        defaults.put(key, IdeBundle.message(key));
      }
    }
  }

  private void patchLafFonts(UIDefaults uiDefaults) {
    //if (JBUI.isHiDPI()) {
    //  HashMap<Object, Font> newFonts = new HashMap<Object, Font>();
    //  for (Object key : uiDefaults.keySet().toArray()) {
    //    Object val = uiDefaults.get(key);
    //    if (val instanceof Font) {
    //      newFonts.put(key, JBFont.create((Font)val));
    //    }
    //  }
    //  for (Map.Entry<Object, Font> entry : newFonts.entrySet()) {
    //    uiDefaults.put(entry.getKey(), entry.getValue());
    //  }
    //} else
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideLafFonts()) {
      storeOriginalFontDefaults(uiDefaults);
      StartupUiUtil.initFontDefaults(uiDefaults, StartupUiUtil.getFontWithFallback(uiSettings.getFontFace(), Font.PLAIN, uiSettings.getFontSize()));
      JBUIScale.setUserScaleFactor(JBUIScale.getFontScale(uiSettings.getFontSize()));
    }
    else {
      restoreOriginalFontDefaults(uiDefaults);
    }
  }

  private void restoreOriginalFontDefaults(UIDefaults defaults) {
    LafReference lf = myCurrentLaf == null ? null : getLookAndFeelReference();
    Map<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults != null) {
      for (String resource : StartupUiUtil.ourPatchableFontResources) {
        defaults.put(resource, lfDefaults.get(resource));
      }
    }
    JBUIScale.setUserScaleFactor(JBUIScale.getFontScale(JBFont.label().getSize()));
  }

  private void storeOriginalFontDefaults(UIDefaults defaults) {
    LafReference lf = myCurrentLaf == null ? null : getLookAndFeelReference();
    Map<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults == null) {
      lfDefaults = new HashMap<>();
      for (String resource : StartupUiUtil.ourPatchableFontResources) {
        lfDefaults.put(resource, defaults.get(resource));
      }
      myStoredDefaults.put(lf, lfDefaults);
    }
  }

  private static void updateUI(@NotNull Window window) {
    IJSwingUtilities.updateComponentTreeUI(window);
    for (Window w : window.getOwnedWindows()) {
      IJSwingUtilities.updateComponentTreeUI(w);
    }
  }

  /**
   * Repaints all displayable window.
   */
  @Override
  public void repaintUI() {
    Frame[] frames = Frame.getFrames();
    for (Frame frame : frames) {
      repaintUI(frame);
    }
  }

  @Override
  public boolean getAutodetect() {
    return autodetect;
  }

  @Override
  public void setAutodetect(boolean value) {
    autodetect = value;
    if (autodetect) {
      detectAndSyncLaf();
    }
  }

  @Override
  public boolean getAutodetectSupported() {
    return getOrCreateLafDetector().getDetectionSupported();
  }

  private @NotNull SystemDarkThemeDetector getOrCreateLafDetector() {
    SystemDarkThemeDetector result = lafDetector;
    if (result == null) {
      result = SystemDarkThemeDetector.createDetector(this::syncLaf);
      lafDetector = result;
    }
    return result;
  }

  @Override
  public void setPreferredDarkLaf(UIManager.@NotNull LookAndFeelInfo value) {
    preferredDarkLaf = value;
  }

  @Override
  public void setPreferredLightLaf(UIManager.@NotNull LookAndFeelInfo value) {
    preferredLightLaf = value;
  }

  private static void repaintUI(Window window) {
    if (!window.isDisplayable()) {
      return;
    }
    window.repaint();
    Window[] children = window.getOwnedWindows();
    for (Window aChildren : children) {
      repaintUI(aChildren);
    }
  }

  private static final class OurPopupFactory extends PopupFactory {
    public static final int WEIGHT_LIGHT = 0;
    public static final int WEIGHT_MEDIUM = 1;
    public static final int WEIGHT_HEAVY = 2;

    private final PopupFactory myDelegate;

    OurPopupFactory(final PopupFactory delegate) {
      myDelegate = delegate;
    }

    @Override
    public Popup getPopup(final Component owner, final Component contents, final int x, final int y) {
      final Point point = fixPopupLocation(contents, x, y);

      final int popupType = PopupUtil.getPopupType(this);
      if (popupType == WEIGHT_HEAVY && OurHeavyWeightPopup.isEnabled()) {
        return new OurHeavyWeightPopup(owner, contents, point.x, point.y);
      }
      if (popupType >= 0) {
        PopupUtil.setPopupType(myDelegate, popupType);
      }

      Popup popup = myDelegate.getPopup(owner, contents, point.x, point.y);
      Window window = ComponentUtil.getWindow(contents);
      String cleanupKey = "LafManagerImpl.rootPaneCleanup";
      boolean isHeavyWeightPopup = window instanceof RootPaneContainer && window != ComponentUtil.getWindow(owner);
      if (isHeavyWeightPopup) {
        window.setMinimumSize(null); // clear min-size from prev invocations on JBR11
      }
      if (isHeavyWeightPopup && ((RootPaneContainer)window).getRootPane().getClientProperty(cleanupKey) == null) {
        final JRootPane rootPane = ((RootPaneContainer)window).getRootPane();
        rootPane.setGlassPane(new IdeGlassPaneImpl(rootPane, false));
        rootPane.putClientProperty(WINDOW_ALPHA, 1.0f);
        rootPane.putClientProperty(cleanupKey, cleanupKey);
        window.addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            // cleanup will be handled by AbstractPopup wrapper
            if (PopupUtil.getPopupContainerFor(rootPane) != null) {
              window.removeWindowListener(this);
              rootPane.putClientProperty(cleanupKey, null);
            }
          }

          @Override
          public void windowClosed(WindowEvent e) {
            window.removeWindowListener(this);
            rootPane.putClientProperty(cleanupKey, null);
            DialogWrapper.cleanupRootPane(rootPane);
            DialogWrapper.cleanupWindowListeners(window);
          }
        });
        if (IdeaPopupMenuUI.isUnderPopup(contents) && IdeaPopupMenuUI.isRoundBorder()) {
          window.setBackground(Gray.TRANSPARENT);
          window.setOpacity(1);
        }
      }
      return popup;
    }

    private static Point fixPopupLocation(final Component contents, final int x, int y) {
      if (!(contents instanceof JToolTip)) {
        if (IdeaPopupMenuUI.isUnderPopup(contents)) {
          int topBorder = JBUI.insets("PopupMenu.borderInsets", JBInsets.emptyInsets()).top;
          Component invoker = ((JPopupMenu)contents).getInvoker();
          if (invoker instanceof ActionMenu) {
            y -= topBorder / 2;
            if (SystemInfoRt.isMac) {
              y += JBUI.scale(1);
            }
          }
          else {
            y -= topBorder;
            y -= JBUI.scale(1);
          }
        }

        return new Point(x, y);
      }

      final PointerInfo info;
      try {
        info = MouseInfo.getPointerInfo();
      }
      catch (InternalError e) {
        // http://www.jetbrains.net/jira/browse/IDEADEV-21390
        // may happen under Mac OSX 10.5
        return new Point(x, y);
      }
      int deltaY = 0;

      if (info != null) {
        final Point mouse = info.getLocation();
        deltaY = mouse.y - y;
      }

      final Dimension size = contents.getPreferredSize();
      final Rectangle rec = new Rectangle(new Point(x, y), size);
      ScreenUtil.moveRectangleToFitTheScreen(rec);

      if (rec.y < y) {
        rec.y += deltaY;
      }

      return rec.getLocation();
    }
  }

  private static final class DefaultMenuArrowIcon extends MenuArrowIcon {
    private static final BooleanSupplier dark = () -> ColorUtil.isDark(UIManager.getColor("MenuItem.selectionBackground"));

    private DefaultMenuArrowIcon() {
      super(() -> AllIcons.Icons.Ide.MenuArrow,
            () -> dark.getAsBoolean() ? AllIcons.Icons.Ide.MenuArrowSelected : AllIcons.Icons.Ide.MenuArrow,
            () -> IconLoader.getDisabledIcon(AllIcons.Icons.Ide.MenuArrow));
    }
  }

  private static LafManagerImpl ourTestInstance;

  @TestOnly
  public static LafManagerImpl getTestInstance() {
    if (ourTestInstance == null) {
      ourTestInstance = new LafManagerImpl();
    }
    return ourTestInstance;
  }

  private final class UiThemeEpListener implements ExtensionPointListener<UIThemeProvider> {
    @Override
    public void extensionAdded(@NotNull UIThemeProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
      UIThemeBasedLookAndFeelInfo newTheme = UiThemeProviderListManager.getInstance().themeAdded(provider);
      if (newTheme == null) {
        return;
      }

      List<UIManager.LookAndFeelInfo> lafList = LafManagerImpl.this.lafList.getValue();
      List<UIManager.LookAndFeelInfo> newLaFs = new ArrayList<>(lafList.size() + 1);
      newLaFs.addAll(lafList);
      newLaFs.add(newTheme);
      UiThemeProviderListManager.Companion.sortThemes(newLaFs);
      LafManagerImpl.this.lafList.setValue(newLaFs);

      updateLafComboboxModel();

      // when updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
      if (!autodetect && (!isUpdatingPlugin || newTheme.getTheme().getId().equals(themeIdBeforePluginUpdate))) {
        setLookAndFeelImpl(newTheme, true, false);
        JBColor.setDark(newTheme.getTheme().isDark());
        updateUI();
      }
    }

    @Override
    public void extensionRemoved(@NotNull UIThemeProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
      UIManager.LookAndFeelInfo switchLafTo = null;
      List<UIManager.LookAndFeelInfo> list = new ArrayList<>();
      for (UIManager.LookAndFeelInfo lookAndFeel : getInstalledLookAndFeels()) {
        if (lookAndFeel instanceof UIThemeBasedLookAndFeelInfo) {
          UITheme theme = ((UIThemeBasedLookAndFeelInfo)lookAndFeel).getTheme();
          if (theme.getId().equals(provider.id)) {
            if (lookAndFeel == getCurrentLookAndFeel()) {
              switchLafTo = theme.isDark() ? defaultDarkLaf.getValue() : getDefaultLightLaf();
            }
            ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).handleThemeRemoved(theme);
            continue;
          }
        }
        list.add(lookAndFeel);
      }
      lafList.setValue(list);
      updateLafComboboxModel();

      if (switchLafTo != null) {
        setLookAndFeelImpl(switchLafTo, true, true);
        JBColor.setDark(defaultDarkLaf.isInitialized() && switchLafTo == defaultDarkLaf.getValue());
        updateUI();
      }
    }
  }

  private static final class LafCellRenderer extends SimpleListCellRenderer<LafReference> {
    private static final SeparatorWithText separator = new SeparatorWithText() {
      @Override
      protected void paintComponent(Graphics g) {
        g.setColor(getForeground());

        Rectangle bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());

        paintLine(g, bounds.x, bounds.y + bounds.height/2, bounds.width);
      }
    };

    @Override
    public Component getListCellRendererComponent(JList<? extends LafReference> list,
                                                  LafReference value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      return value == SEPARATOR ? separator : super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }

    @Override
    public void customize(@NotNull JList<? extends LafReference> list, LafReference value, int index, boolean selected, boolean hasFocus) {
      setText(value.toString());
    }
  }

  private final class LafComboBoxModel extends CollectionComboBoxModel<LafReference> {
    private LafComboBoxModel() {
      super(getAllReferences());
    }

    @Override
    public void setSelectedItem(@Nullable Object item) {
      if (item != SEPARATOR) {
        super.setSelectedItem(item);
      }
    }
  }

  private final class PreferredLafAction extends DefaultActionGroup implements DumbAware {
    private PreferredLafAction() {
      setPopup(true);
      getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
      getTemplatePresentation().setText(IdeBundle.message("preferred.theme.text"));
      getTemplatePresentation().setDescription(IdeBundle.message("preferred.theme.description"));
      getTemplatePresentation().setPerformGroup(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListPopup popup = JBPopupFactory.getInstance().
        createActionGroupPopup(IdeBundle.message("preferred.theme.text"), getLafGroups(), e.getDataContext(), true, null, Integer.MAX_VALUE);

      HelpTooltip.setMasterPopup(e.getInputEvent().getComponent(), popup);
      Component component = e.getInputEvent().getComponent();
      if (component instanceof ActionButtonComponent) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInCenterOf(component);
      }
    }

    private @NotNull ActionGroup getLafGroups() {
      List<UIManager.LookAndFeelInfo> lightLafs = new ArrayList<>();
      List<UIManager.LookAndFeelInfo> darkLafs = new ArrayList<>();

      for (UIManager.LookAndFeelInfo lafInfo : lafList.getValue()) {
        if (lafInfo instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)lafInfo).getTheme().isDark() ||
            lafInfo.getName().equals(DarculaLaf.NAME)) {
          darkLafs.add(lafInfo);
        }
        else {
          lightLafs.add(lafInfo);
        }
      }

      DefaultActionGroup group = new DefaultActionGroup();
      group.addAll(createThemeActions(IdeBundle.message("preferred.theme.light.header"), lightLafs, false));
      group.addAll(createThemeActions(IdeBundle.message("preferred.theme.dark.header"), darkLafs, true));
      return group;
    }

    private @NotNull Collection<AnAction> createThemeActions(@NotNull @NlsContexts.Separator String separatorText,
                                                             @NotNull List<? extends UIManager.LookAndFeelInfo> lafs,
                                                             boolean isDark) {
      List<AnAction> result = new ArrayList<>();
      if (!lafs.isEmpty()) {
        result.add(Separator.create(separatorText));
        for (UIManager.LookAndFeelInfo l : lafs) {
          result.add(new LafToggleAction(l.getName(), l, isDark));
        }
      }
      return result;
    }
  }

  private final class LafToggleAction extends ToggleAction {
    private final UIManager.LookAndFeelInfo lafInfo;
    private final boolean isDark;

    private LafToggleAction(@Nls String name, UIManager.LookAndFeelInfo lafInfo, boolean isDark) {
      super(name);
      this.lafInfo = lafInfo;
      this.isDark = isDark;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isDark ? lafInfo == preferredDarkLaf : lafInfo == preferredLightLaf;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (isDark) {
        if (preferredDarkLaf != lafInfo) {
          preferredDarkLaf = lafInfo;
          detectAndSyncLaf();
        }
      }
      else {
        if (preferredLightLaf != lafInfo) {
          preferredLightLaf = lafInfo;
          detectAndSyncLaf();
        }
      }
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }
  }
}
