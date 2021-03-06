// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.Activity;
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.DefaultLinkButtonUI;
import com.intellij.ui.popup.OurHeavyWeightPopup;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.util.EventDispatcher;
import com.intellij.util.FontUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.SVGLoader;
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
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.*;
import java.util.function.BooleanSupplier;

@State(name = "LafManager", storages = @Storage(value = "laf.xml", roamingType = RoamingType.PER_OS))
public final class LafManagerImpl extends LafManager implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(LafManager.class);

  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ELEMENT_PREFERRED_LIGHT_LAF = "preferred-light-laf";
  @NonNls private static final String ELEMENT_PREFERRED_DARK_LAF = "preferred-dark-laf";
  @NonNls private static final String ATTRIBUTE_AUTODETECT = "autodetect";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String ATTRIBUTE_THEME_NAME = "themeId";

  private static final String DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme";
  private static final String HIGH_CONTRAST_THEME_ID = "JetBrainsHighContrastTheme";
  private static final String DARCULA_EDITOR_THEME_KEY = "Darcula.SavedEditorTheme";
  private static final String DEFAULT_EDITOR_THEME_KEY = "Default.SavedEditorTheme";

  @NonNls private static final String[] ourPatchableFontResources = {"Button.font", "ToggleButton.font", "RadioButton.font",
    "CheckBox.font", "ColorChooser.font", "ComboBox.font", "Label.font", "List.font", "MenuBar.font", "MenuItem.font",
    "MenuItem.acceleratorFont", "RadioButtonMenuItem.font", "CheckBoxMenuItem.font", "Menu.font", "PopupMenu.font", "OptionPane.font",
    "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font", "TabbedPane.font", "Table.font", "TableHeader.font",
    "TextField.font", "FormattedTextField.font", "Spinner.font", "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font",
    "TitledBorder.font", "ToolBar.font", "ToolTip.font", "Tree.font"};

  @PropertyKey(resourceBundle = IdeBundle.BUNDLE)
  @NonNls private static final String[] ourFileChooserTextKeys = {"FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
    "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"};

  private final EventDispatcher<LafManagerListener> myEventDispatcher = EventDispatcher.create(LafManagerListener.class);

  private final SynchronizedClearableLazy<List<UIManager.LookAndFeelInfo>> myLaFs = new SynchronizedClearableLazy<>(() -> {
    Activity activity = StartUpMeasurer.startActivity("compute LaF list");
    List<UIManager.LookAndFeelInfo> infos = computeLafList();
    activity.end();
    return infos;
  });

  private final UIManager.LookAndFeelInfo myDefaultLightLaf = loadDefaultLightTheme();
  private final UIManager.LookAndFeelInfo myDefaultDarkLaf = loadDefaultDarkTheme();
  private final Map<Object, Object> ourDefaults = (UIDefaults)UIManager.getDefaults().clone();

  private UIManager.LookAndFeelInfo myCurrentLaf;
  private UIManager.LookAndFeelInfo myPreferredLightLaf;
  private UIManager.LookAndFeelInfo myPreferredDarkLaf;

  private final Map<LafReference, Map<String, Object>> myStoredDefaults = new HashMap<>();

  // A constant from Mac OS X implementation. See CPlatformWindow.WINDOW_ALPHA
  private static final String WINDOW_ALPHA = "Window.alpha";

  private static final Map<String, String> ourLafClassesAliases = Map.of("idea.dark.laf.classname", DarculaLookAndFeelInfo.CLASS_NAME);
  private static Map<String, Integer> lafNameOrder = Map.of(
    "IntelliJ Light", 0,
    "macOS Light", 1,
    "Windows 10 Light", 1,
    "Darcula", 2,
    "High contrast", 3
  );

  // allowing other plugins to change the order of the LaFs (used by Rider)
  public static @NotNull Map<String, Integer> getLafNameOrder() {
    return lafNameOrder;
  }

  public static void setLafNameOrder(@NotNull Map<String, Integer> value) {
    lafNameOrder = value;
  }

  private final SynchronizedClearableLazy<CollectionComboBoxModel<LafReference>> myLafComboBoxModel =
    new SynchronizedClearableLazy<>(() -> new LafComboBoxModel());

  private final Lazy<ActionToolbar> settingsToolbar = new SynchronizedClearableLazy<>(() -> {
    DefaultActionGroup group = new DefaultActionGroup(new PreferredLafAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true);
    toolbar.getComponent().setOpaque(false);
    return toolbar;
  });

  // SystemDarkThemeDetector must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
  private @Nullable SystemDarkThemeDetector lafDetector;
  private static final LafReference SEPARATOR = new LafReference("", null, null);

  private boolean myFirstSetup = true;
  private boolean myUpdatingPlugin = false;
  private @Nullable String myThemeIdBeforePluginUpdate = null;
  private boolean autodetect;

  private static UIManager.LookAndFeelInfo loadDefaultLightTheme() {
    for (UIThemeProvider provider : UIThemeProvider.EP_NAME.getIterable()) {
      if (DEFAULT_LIGHT_THEME_ID.equals(provider.id)) {
        UITheme theme = provider.createTheme();
        if (theme != null) {
          return new UIThemeBasedLookAndFeelInfo(theme);
        }
      }
    }
    LOG.error("Can't load " + DEFAULT_LIGHT_THEME_ID);

    String lafInfoFQN = ApplicationInfoEx.getInstanceEx().getDefaultLightLaf();
    UIManager.LookAndFeelInfo lookAndFeelInfo = StringUtil.isNotEmpty(lafInfoFQN) ? createLafInfo(lafInfoFQN) : null;
    return lookAndFeelInfo != null ? lookAndFeelInfo : new IntelliJLookAndFeelInfo();
  }

  private static UIManager.LookAndFeelInfo loadDefaultDarkTheme() {
    String lafInfoFQN = ApplicationInfoEx.getInstanceEx().getDefaultDarkLaf();
    UIManager.LookAndFeelInfo lookAndFeelInfo = StringUtil.isNotEmpty(lafInfoFQN) ? createLafInfo(lafInfoFQN) : null;
    return lookAndFeelInfo != null ? lookAndFeelInfo : new DarculaLookAndFeelInfo();
  }

  public UIManager.LookAndFeelInfo getDefaultLightLaf() {
    return myDefaultLightLaf;
  }

  public UIManager.LookAndFeelInfo getDefaultDarkLaf() {
    return myDefaultDarkLaf;
  }

  @Nullable
  private static UIManager.LookAndFeelInfo createLafInfo(@NotNull String fqn) {
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
    lafList.add(myDefaultLightLaf);
    lafList.add(myDefaultDarkLaf);

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

    UIThemeProvider.EP_NAME.forEachExtensionSafe(provider -> {
      if (!DEFAULT_LIGHT_THEME_ID.equals(provider.id)) {
        UITheme theme = provider.createTheme();
        if (theme != null) {
          lafList.add(new UIThemeBasedLookAndFeelInfo(theme));
        }
      }
    });
    sortThemes(lafList);
    return lafList;
  }

  private static void sortThemes(@NotNull List<? extends UIManager.LookAndFeelInfo> list) {
    list.sort((t1, t2) -> {
      String n1 = t1.getName();
      String n2 = t2.getName();
      if (Objects.equals(n1, n2)) return 0;

      Integer o1 = lafNameOrder.get(n1);
      Integer o2 = lafNameOrder.get(n2);
      if (o1 != null && o2 != null) return o1 - o2;
      else if (o1 != null) return -1;
      else if (o2 != null) return 1;
      else return n1.compareToIgnoreCase(n2);
    });
  }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener, @NotNull Disposable disposable) {
    ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(LafManagerListener.TOPIC, listener);
  }

  @Override
  public void removeLafManagerListener(@NotNull LafManagerListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  @Override
  public void initializeComponent() {
    ApplicationManager.getApplication().invokeLater(() -> {
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
      myFirstSetup = false;

      updateUI();
      detectAndSyncLaf();

      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).initScheme(myCurrentLaf);

      addThemeAndDynamicPluginListeners();
    }, ModalityState.any());
  }

  private void addThemeAndDynamicPluginListeners() {
    UIThemeProvider.EP_NAME.addExtensionPointListener(new UIThemeEPListener(), this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myUpdatingPlugin = isUpdate;
        if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo) {
          myThemeIdBeforePluginUpdate = ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().getId();
        }
        else {
          myThemeIdBeforePluginUpdate = null;
        }
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        myUpdatingPlugin = false;
        myThemeIdBeforePluginUpdate = null;
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

  private void syncLaf(boolean systemDark) {
    if (autodetect) {
      boolean currentDark =
        myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().isDark() ||
        StartupUiUtil.isUnderDarcula();
      UIManager.LookAndFeelInfo expectedLaf = systemDark ? myPreferredDarkLaf : myPreferredLightLaf;
      if (currentDark != systemDark || myCurrentLaf != expectedLaf) {
        QuickChangeLookAndFeel.switchLafAndUpdateUI(this, expectedLaf, true);
      }
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
    myPreferredLightLaf = Objects.requireNonNullElse(loadLafState(element, ELEMENT_PREFERRED_LIGHT_LAF), myDefaultLightLaf);
    myPreferredDarkLaf = Objects.requireNonNullElse(loadLafState(element, ELEMENT_PREFERRED_DARK_LAF), myDefaultDarkLaf);

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
    if (lafClassName != null && ourLafClassesAliases.containsKey(lafClassName)) {
      lafClassName = ourLafClassesAliases.get(lafClassName);
    }

    if ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel".equals(lafClassName)) {
      return myDefaultLightLaf;
    }

    if (themeId != null) {
      for (UIManager.LookAndFeelInfo l : myLaFs.getValue()) {
        if (l instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)l).getTheme().getId().equals(themeId)) {
          return l;
        }
      }
    }
    if (lafClassName != null) {
      return findLaf(lafClassName);
    }
    return null;
  }

  @Override
  public void noStateLoaded() {
    myCurrentLaf = loadDefaultLaf();
    myPreferredLightLaf = myDefaultLightLaf;
    myPreferredDarkLaf = myDefaultDarkLaf;
    autodetect = false;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    element.setAttribute(ATTRIBUTE_AUTODETECT, Boolean.toString(autodetect));

    getLafState(element, ELEMENT_LAF, getCurrentLookAndFeel());

    if (myPreferredLightLaf != myDefaultLightLaf) {
      getLafState(element, ELEMENT_PREFERRED_LIGHT_LAF, myPreferredLightLaf);
    }

    if (myPreferredDarkLaf != myDefaultDarkLaf) {
      getLafState(element, ELEMENT_PREFERRED_DARK_LAF, myPreferredDarkLaf);
    }

    return element;
  }

  private static void getLafState(@NotNull Element element, @NonNls String attrName, UIManager.LookAndFeelInfo laf) {
    if (laf instanceof TempUIThemeBasedLookAndFeelInfo) {
      laf = ((TempUIThemeBasedLookAndFeelInfo)laf).getPreviousLaf();
    }
    if (laf != null) {
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
  }

  @Override
  public UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels() {
    return myLaFs.getValue().toArray(new UIManager.LookAndFeelInfo[0]);
  }

  @Override
  public @NotNull CollectionComboBoxModel<LafReference> getLafComboBoxModel() {
    return myLafComboBoxModel.getValue();
  }

  private @NotNull List<LafReference> getAllReferences() {
    List<LafReference> result = new ArrayList<>();
    boolean addSeparator = false;
    int maxNameOrder = Collections.max(lafNameOrder.values());
    for (UIManager.LookAndFeelInfo info : myLaFs.getValue()) {
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

  @NotNull
  private static LafReference createLafReference(UIManager.LookAndFeelInfo laf) {
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
  @Nullable
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
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
  @NotNull
  public JComponent getSettingsToolbar() {
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
      for (UIManager.LookAndFeelInfo laf : myLaFs.getValue()) {
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
    if (myDefaultLightLaf.getClassName().equals(className)) {
      return myDefaultLightLaf;
    }
    if (myDefaultDarkLaf.getClassName().equals(className)) {
      return myDefaultDarkLaf;
    }

    for (UIManager.LookAndFeelInfo l : myLaFs.getValue()) {
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

    if (!myFirstSetup && installEditorScheme) {
      if (processChangeSynchronously) {
        updateEditorSchemeIfNecessary(oldLaf, true);
      }
      else {
        ApplicationManager.getApplication().invokeLater(() -> updateEditorSchemeIfNecessary(oldLaf, false));
      }
    }
    myFirstSetup = false;
  }

  private boolean doSetLaF(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean installEditorScheme) {
    UIDefaults defaults = UIManager.getDefaults();
    defaults.clear();
    defaults.putAll(ourDefaults);
    if (!myFirstSetup) {
      SVGLoader.setColorPatcherForSelection(null);
    }

    // set L&F
    // that is IDEA default LAF
    if (IdeaLookAndFeelInfo.CLASS_NAME.equals(lookAndFeelInfo.getClassName())) {
      IdeaLaf laf = new IdeaLaf();
      MetalLookAndFeel.setCurrentTheme(new IdeaBlueMetalTheme());
      try {
        UIManager.setLookAndFeel(laf);
        updateIconsUnderSelection(false);
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
    else if (DarculaLookAndFeelInfo.CLASS_NAME.equals(lookAndFeelInfo.getClassName())) {
      DarculaLaf laf = new DarculaLaf();
      try {
        UIManager.setLookAndFeel(laf);
        AppUIUtil.updateForDarcula(true);
        if (lafNameOrder.containsKey(lookAndFeelInfo.getName())) {
          updateIconsUnderSelection(true);
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
    else {
      // non default LAF
      try {
        LookAndFeel laf;
        if (lookAndFeelInfo instanceof PluggableLafInfo) {
          laf = ((PluggableLafInfo)lookAndFeelInfo).createLookAndFeel();
        }
        else {
          laf = (LookAndFeel)Class.forName(lookAndFeelInfo.getClassName()).getConstructor().newInstance();
          if (laf instanceof MetalLookAndFeel) {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
          }
          else if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
            if (laf instanceof UserDataHolder) {
              UserDataHolder userDataHolder = (UserDataHolder)laf;
              userDataHolder.putUserData(UIUtil.LAF_WITH_THEME_KEY, Boolean.TRUE);
            }
            if (lafNameOrder.containsKey(lookAndFeelInfo.getName()) && lookAndFeelInfo.getName().endsWith("Light")) {
              updateIconsUnderSelection(false);
            }
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
        ((UIThemeBasedLookAndFeelInfo)lookAndFeelInfo).installTheme(UIManager.getLookAndFeelDefaults(), !installEditorScheme);
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
    return false;
  }

  private static void updateIconsUnderSelection(boolean darcula) {
    Map<String, String> map = new HashMap<>();
    if (darcula) {
      map.put("#5e5e5e", "#5778ad");
      map.put("#c75450", "#a95768");
      map.put("#6e6e6e", "#afb1b3");
      //map.put("#f26522", "#b76554"); //red
      map.put("#f26522b3", "#bc6b43"); //red 70%
      map.put("#f2652299", "#bc6b43"); //red 60% (same as 70%)
      map.put("#62b54399", "#579b41"); //green 60%
      map.put("#f98b9e99", "#ba7481"); //pink 60%
      map.put("#f4af3d99", "#aa823f"); //yellow 60%
      map.put("#b99bf899", "#977fca"); //purple 60%
      map.put("#9aa7b0cc", "#97acc6"); //noun gray 80%
      map.put("#9aa7b099", "#97acc6"); //noun gray 60% (same as 80%)
    }
    else {
      map.put("#6e6e6e", "#afb1b3");
      map.put("#db5860", "#b75e73");
      //map.put("#f26522", "#b56a51"); //red
      map.put("#f26522b3", "#d38369"); //red 70%
      map.put("#f2652299", "#d38369"); //red 60% (same as 70%)
      map.put("#40b6e099", "#5eb6d4"); //blue 60%
      map.put("#62b54399", "#7ebe65"); //green 60%
      map.put("#f98b9e99", "#f1a4b2"); //pink 60%
      map.put("#f4af3d99", "#ecc27d"); //yellow 60%
      map.put("#b99bf899", "#b49ee2"); //purple 60%
      map.put("#9aa7b0cc", "#aebdc6"); //noun gray 80%
      map.put("#9aa7b099", "#aebdc6"); //noun gray 60% (same as 80%)
      map.put("#40b6e0b3", "#5eb6d4"); //blue 70%
      map.put("#62b543b3", "#7ebe65"); //green 70%
      map.put("#f98b9eb3", "#f1a4b2"); //pink 70%
      map.put("#f4af3db3", "#ecc27d"); //yellow 70%
      map.put("#b99bf8b3", "#b49ee2"); //purple 70%
    }

    Map<String, Integer> alpha = new HashMap<>(map.size());
    map.forEach((key, value) -> alpha.put(value, 255));

     SVGLoader.setColorPatcherForSelection(new SVGLoader.SvgElementColorPatcherProvider() {
       @Override
       public SVGLoader.@Nullable SvgElementColorPatcher forPath(@Nullable String path) {
         return SVGLoader.newPatcher(null, map, alpha);
       }
     });
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

    initInputMapDefaults(uiDefaults);

    uiDefaults.put("Button.defaultButtonFollowsFocus", Boolean.FALSE);
    uiDefaults.put("Balloon.error.textInsets", new JBInsets(3, 8, 3, 8).asUIResource());

    patchFileChooserStrings(uiDefaults);

    patchLafFonts(uiDefaults);

    patchListUI(uiDefaults);
    patchTreeUI(uiDefaults);

    patchHiDPI(uiDefaults);

    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue());

    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS,
                   AppUIUtil.adjustFractionalMetrics(UISettings.getPREFERRED_FRACTIONAL_METRICS_VALUE()));

    for (Frame frame : Frame.getFrames()) {
      updateUI(frame);
    }

    ApplicationManager.getApplication().getMessageBus().syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(this);
    myEventDispatcher.getMulticaster().lookAndFeelChanged(this);
  }

  @NotNull
  private static FontUIResource getFont(String yosemite, int size, @JdkConstants.FontStyle int style) {
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
    initFontDefaults(defaults, getFont(face, 13, Font.PLAIN));
    for (Object key : new ArrayList<>(defaults.keySet())) {
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
    Font menuFont = getFont("Lucida Grande", 14, Font.PLAIN);
    defaults.put("Menu.font", menuFont);
    defaults.put("MenuItem.font", menuFont);
    defaults.put("MenuItem.acceleratorFont", menuFont);
    defaults.put("PasswordField.font", defaults.getFont("TextField.font"));
  }

  private static void patchBorder(UIDefaults defaults, String key) {
    if (defaults.getBorder(key) == null) {
      defaults.put(key, JBUI.Borders.empty(1, 0).asUIResource());
    }
  }

  private static void patchListUI(UIDefaults defaults) {
    patchBorder(defaults, "List.border");
  }

  private static void patchTreeUI(UIDefaults defaults) {
    patchBorder(defaults, "Tree.border");
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

  private static void patchHiDPI(UIDefaults defaults) {
    Object prevScaleVal = defaults.get("hidpi.scaleFactor");
    // used to normalize previously patched values
    float prevScale = prevScaleVal != null ? (Float)prevScaleVal : 1f;

    // fix predefined row height if default system font size is not expected
    float prevRowHeightScale = prevScaleVal != null || SystemInfo.isMac || SystemInfo.isWindows
                               ? prevScale
                               : JBUIScale.getFontScale(12f);
    patchRowHeight(defaults, "List.rowHeight", prevRowHeightScale);
    patchRowHeight(defaults, "Table.rowHeight", prevRowHeightScale);
    patchRowHeight(defaults, "Tree.rowHeight", prevRowHeightScale);

    if (prevScale == JBUIScale.scale(1f) && prevScaleVal != null) return;

    List<String> myIntKeys = Arrays.asList("Tree.leftChildIndent",
                                           "Tree.rightChildIndent",
                                           "SettingsTree.rowHeight");

    List<String> myDimensionKeys = Arrays.asList("Slider.horizontalSize",
                                                 "Slider.verticalSize",
                                                 "Slider.minimumHorizontalSize",
                                                 "Slider.minimumVerticalSize");

    for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
      Object value = entry.getValue();
      String key = entry.getKey().toString();
      if (value instanceof Dimension) {
        if (value instanceof UIResource || myDimensionKeys.contains(key)) {
          entry.setValue(JBUI.size((Dimension)value).asUIResource());
        }
      }
      else if (value instanceof Insets) {
        if (value instanceof UIResource) {
          entry.setValue(JBUI.insets(((Insets)value)).asUIResource());
        }
      }
      else if (value instanceof Integer) {
        if (key.endsWith(".maxGutterIconWidth") || myIntKeys.contains(key)) {
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
    if (!SystemInfoRt.isMac && !SystemInfoRt.isWindows && Registry.is("linux.row.height.disabled", true)) {
      rowHeight = 0;
    }
    else if (rowHeight <= 0) {
      LOG.warn(key + " = " + value + " in " + UIManager.getLookAndFeel().getName() + "; it may lead to performance degradation");
    }
    int custom = Registry.intValue("ide.override." + key, -1);
    defaults.put(key, custom >= 0 ? custom : rowHeight <= 0 ? 0 : JBUIScale.scale((int)(rowHeight / prevScale)));
  }

  private static void fixMenuIssues(@NotNull UIDefaults uiDefaults) {
    uiDefaults.put("Menu.arrowIcon", new DefaultMenuArrowIcon());
    uiDefaults.put("MenuItem.background", UIManager.getColor("Menu.background"));
  }

  /**
   * The following code is a trick! By default Swing uses lightweight and "medium" weight
   * popups to show JPopupMenu. The code below force the creation of real heavyweight menus -
   * this increases speed of popups and allows to get rid of some drawing artifacts.
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

  private static void patchFileChooserStrings(final UIDefaults defaults) {
    if (!defaults.containsKey(ourFileChooserTextKeys[0])) {
      // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
      for (String key : ourFileChooserTextKeys) {
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
      initFontDefaults(uiDefaults, UIUtil.getFontWithFallback(uiSettings.getFontFace(), Font.PLAIN, uiSettings.getFontSize()));
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
      for (String resource : ourPatchableFontResources) {
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
      for (String resource : ourPatchableFontResources) {
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
    myPreferredDarkLaf = value;
  }

  @Override
  public void setPreferredLightLaf(UIManager.@NotNull LookAndFeelInfo value) {
    myPreferredLightLaf = value;
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

  private static void installCutCopyPasteShortcuts(InputMap inputMap, boolean useSimpleActionKeys) {
    String copyActionKey = useSimpleActionKeys ? "copy" : DefaultEditorKit.copyAction;
    String pasteActionKey = useSimpleActionKeys ? "paste" : DefaultEditorKit.pasteAction;
    String cutActionKey = useSimpleActionKeys ? "cut" : DefaultEditorKit.cutAction;
    // Ctrl+Ins, Shift+Ins, Shift+Del
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK), copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK), pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK), cutActionKey);
    // Ctrl+C, Ctrl+V, Ctrl+X
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), DefaultEditorKit.cutAction);
  }

  public static void initInputMapDefaults(UIDefaults defaults) {
    // Make ENTER work in JTrees
    InputMap treeInputMap = (InputMap)defaults.get("Tree.focusInputMap");
    if (treeInputMap != null) { // it's really possible. For example,  GTK+ doesn't have such map
      treeInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle");
    }
    // Cut/Copy/Paste in JTextAreas
    InputMap textAreaInputMap = (InputMap)defaults.get("TextArea.focusInputMap");
    if (textAreaInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textAreaInputMap, false);
    }
    // Cut/Copy/Paste in JTextFields
    InputMap textFieldInputMap = (InputMap)defaults.get("TextField.focusInputMap");
    if (textFieldInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textFieldInputMap, false);
    }
    // Cut/Copy/Paste in JPasswordField
    InputMap passwordFieldInputMap = (InputMap)defaults.get("PasswordField.focusInputMap");
    if (passwordFieldInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(passwordFieldInputMap, false);
    }
    // Cut/Copy/Paste in JTables
    InputMap tableInputMap = (InputMap)defaults.get("Table.ancestorInputMap");
    if (tableInputMap != null) { // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(tableInputMap, true);
    }
  }

  public static void initFontDefaults(@NotNull UIDefaults defaults, @NotNull FontUIResource uiFont) {
    defaults.put("Tree.ancestorInputMap", null);
    FontUIResource textFont = new FontUIResource(uiFont);
    FontUIResource monoFont = new FontUIResource("Monospaced", Font.PLAIN, uiFont.getSize());

    for (String fontResource : ourPatchableFontResources) {
      defaults.put(fontResource, uiFont);
    }

    if (!SystemInfoRt.isMac) {
      defaults.put("PasswordField.font", monoFont);
    }
    defaults.put("TextArea.font", monoFont);
    defaults.put("TextPane.font", textFont);
    defaults.put("EditorPane.font", textFont);
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
        UIUtil.markAsTypeAheadAware(window);
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
      }
      return popup;
    }

    private static Point fixPopupLocation(final Component contents, final int x, final int y) {
      if (!(contents instanceof JToolTip)) return new Point(x, y);

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

  private final class UIThemeEPListener implements ExtensionPointListener<UIThemeProvider> {
    @Override
    public void extensionAdded(@NotNull UIThemeProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
      for (UIManager.LookAndFeelInfo feel : getInstalledLookAndFeels()) {
        if (feel instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)feel).getTheme().getId().equals(provider.id)) {
          //provider is already registered
          return;
        }
      }

      UITheme theme = provider.createTheme();
      if (theme == null) {
        return;
      }

      ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).handleThemeAdded(theme);

      List<UIManager.LookAndFeelInfo> lafList = myLaFs.getValue();
      List<UIManager.LookAndFeelInfo> newLaFs = new ArrayList<>(lafList.size() + 1);
      newLaFs.addAll(lafList);
      UIThemeBasedLookAndFeelInfo newTheme = new UIThemeBasedLookAndFeelInfo(theme);
      newLaFs.add(newTheme);
      sortThemes(newLaFs);
      myLaFs.setValue(newLaFs);

      updateLafComboboxModel();

      // when updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
      if (!autodetect && (!myUpdatingPlugin || newTheme.getTheme().getId().equals(myThemeIdBeforePluginUpdate))) {
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
              switchLafTo = theme.isDark() ? myDefaultDarkLaf : myDefaultLightLaf;
            }
            ((EditorColorsManagerImpl) EditorColorsManager.getInstance()).handleThemeRemoved(theme);
            continue;
          }
        }
        list.add(lookAndFeel);
      }
      myLaFs.setValue(list);
      updateLafComboboxModel();

      if (switchLafTo != null) {
        setLookAndFeelImpl(switchLafTo, true, true);
        JBColor.setDark(switchLafTo == myDefaultDarkLaf);
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

  private final class PreferredLafAction extends DefaultActionGroup {
    private PreferredLafAction() {
      setPopup(true);
      getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
      getTemplatePresentation().setText(IdeBundle.message("preferred.theme.text"));
      getTemplatePresentation().setDescription(IdeBundle.message("preferred.theme.description"));
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }

    @Override
    public boolean canBePerformed(@NotNull DataContext context) {
      return true;
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

      for (UIManager.LookAndFeelInfo lafInfo : myLaFs.getValue()) {
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
      return isDark ? lafInfo == myPreferredDarkLaf : lafInfo == myPreferredLightLaf;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (isDark) {
        if (myPreferredDarkLaf != lafInfo) {
          myPreferredDarkLaf = lafInfo;
          detectAndSyncLaf();
        }
      }
      else {
        if (myPreferredLightLaf != lafInfo) {
          myPreferredLightLaf = lafInfo;
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
