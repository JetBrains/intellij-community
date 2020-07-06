// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.CommonBundle;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.DefaultLinkButtonUI;
import com.intellij.ui.popup.OurHeavyWeightPopup;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.*;
import kotlin.Unit;
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

import static com.intellij.util.FontUtil.enableKerning;

@State(name = "LafManager", storages = @Storage(value = "laf.xml", roamingType = RoamingType.PER_OS))
public final class LafManagerImpl extends LafManager implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(LafManager.class);

  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ELEMENT_PREFERRED_LIGHT_LAF = "preferred-light-laf";
  @NonNls private static final String ELEMENT_PREFERRED_DARK_LAF = "preferred-dark-laf";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String ATTRIBUTE_THEME_NAME = "themeId";

  private static final String DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme";
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

  private final UIManager.LookAndFeelInfo myDefaultLightLaf = getDefaultLightTheme();
  private final UIManager.LookAndFeelInfo myDefaultDarkLaf = new DarculaLookAndFeelInfo();
  private final UIDefaults ourDefaults = (UIDefaults)UIManager.getDefaults().clone();

  private UIManager.LookAndFeelInfo myCurrentLaf;
  private UIManager.LookAndFeelInfo myPreferredLightLaf;
  private UIManager.LookAndFeelInfo myPreferredDarkLaf;

  private final Map<LafReference, HashMap<String, Object>> myStoredDefaults = new HashMap<>();

  // A constant from Mac OS X implementation. See CPlatformWindow.WINDOW_ALPHA
  public static final String WINDOW_ALPHA = "Window.alpha";

  private static final Map<String, String> ourLafClassesAliases = new HashMap<>();
  private static final Map<String, Integer> lafNameOrder = new HashMap<>();

  private final CollectionComboBoxModel<LafReference> myLafComboBoxModel = new CollectionComboBoxModel<>();

  static {
    ourLafClassesAliases.put("idea.dark.laf.classname", DarculaLookAndFeelInfo.CLASS_NAME);

    lafNameOrder.put("IntelliJ Light", 0);
    lafNameOrder.put("macOS Light", 1);
    lafNameOrder.put("Windows 10 Light", 1);
    lafNameOrder.put("Darcula", 2);
    lafNameOrder.put("High contrast", 3);
  }

  private boolean myFirstSetup = true;
  private boolean myUpdatingPlugin = false;
  private final Set<String> myThemesInUpdatedPlugin = new HashSet<>();
  private boolean autodetect;

  private static UIManager.LookAndFeelInfo getDefaultLightTheme() {
    for(UIThemeProvider provider: UIThemeProvider.EP_NAME.getExtensionList()) {
      if (DEFAULT_LIGHT_THEME_ID.equals(provider.id)) {
        UITheme theme = provider.createTheme();
        if (theme != null) {
          return new UIThemeBasedLookAndFeelInfo(theme);
        }
      }
    }
    LOG.error("Can't load " + DEFAULT_LIGHT_THEME_ID);
    return new IntelliJLookAndFeelInfo();
  }

  @NotNull
  private List<UIManager.LookAndFeelInfo> computeLafList() {
    List<UIManager.LookAndFeelInfo> lafList = new ArrayList<>();
    lafList.add(myDefaultLightLaf);
    lafList.add(myDefaultDarkLaf);

    if (!SystemInfo.isMac) {
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
    updateLafComboboxModel();
    return lafList;
  }

  private static void sortThemes(@NotNull List<UIManager.LookAndFeelInfo> list) {
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
    autodetect = Registry.is("ide.laf.autodetect");
    Registry.get("ide.laf.autodetect").addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        autodetect = value.asBoolean();
        syncLaf();
      }
    }, this);

    if (myCurrentLaf != null && !(myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo)) {
      final UIManager.LookAndFeelInfo laf = findLaf(myCurrentLaf.getClassName());
      if (laf != null) {
        boolean needUninstall = StartupUiUtil.isUnderDarcula();
        setCurrentLookAndFeel(laf); // setup default LAF or one specified by readExternal.
        updateWizardLAF(needUninstall);
      }
    }

    if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo && !((UIThemeBasedLookAndFeelInfo)myCurrentLaf).isInitialised()) {
      setCurrentLookAndFeel(myCurrentLaf);
    }

    updateUI();

    UIThemeProvider.EP_NAME.addExtensionPointListener(new UIThemeEPListener(), this);

    MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    bus.connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myUpdatingPlugin = isUpdate;
      }

      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        myThemesInUpdatedPlugin.clear();
      }
    });

    bus.connect(this).subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
        syncLaf();
      }
    });
  }

  private void syncLaf() {
    if (autodetect) {
      boolean currentDark = myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().isDark() ||
                           UIUtil.isUnderDarcula();

      SystemDarkThemeDetector.getInstance().check(systemDark -> {
        if (currentDark != systemDark) {
          QuickChangeLookAndFeel.switchLafAndUpdateUI(LafManager.getInstance(), systemDark ? myPreferredDarkLaf : myPreferredLightLaf, true);
        }
        return Unit.INSTANCE;
      });
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
    myCurrentLaf = loadLafState(element, ELEMENT_LAF, getDefaultLaf());
    myPreferredLightLaf = loadLafState(element, ELEMENT_PREFERRED_LIGHT_LAF, myDefaultLightLaf);
    myPreferredDarkLaf = loadLafState(element, ELEMENT_PREFERRED_DARK_LAF, myDefaultDarkLaf);
  }

  private UIManager.LookAndFeelInfo loadLafState(@NotNull Element element, @NonNls String attrName, UIManager.LookAndFeelInfo defaultValue) {
    UIManager.LookAndFeelInfo laf = null;
    Element lafElement = element.getChild(attrName);
    if (lafElement != null) {
      laf = findLaf(lafElement.getAttributeValue(ATTRIBUTE_CLASS_NAME), lafElement.getAttributeValue(ATTRIBUTE_THEME_NAME));
    }

    // If LAF is undefined (wrong class name or something else) we have set default LAF anyway.
    if (laf == null) {
      laf = defaultValue;
    }

    return laf;
  }

  @Nullable
  private UIManager.LookAndFeelInfo findLaf(String lafClassName, String themeId) {
    if (lafClassName != null && ourLafClassesAliases.containsKey(lafClassName)) {
      lafClassName = ourLafClassesAliases.get(lafClassName);
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
    myCurrentLaf = getDefaultLaf();
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    return element;
  }

  private void getLafState(@NotNull Element element, @NonNls String attrName, UIManager.LookAndFeelInfo laf) {
    if (laf instanceof TempUIThemeBasedLookAndFeelInfo) {
      laf = ((TempUIThemeBasedLookAndFeelInfo)laf).getPreviousLaf();
    }
    if (laf != null) {
      String className = laf.getClassName();
      if (className != null) {
        Element child = new Element(ELEMENT_LAF);
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
  public CollectionComboBoxModel<LafReference> getLafComboBoxModel() {
    return myLafComboBoxModel;
  }

  private void updateLafComboboxModel() {
    myLafComboBoxModel.replaceAll(getLafReferences(ModelType.ALL));
  }

  private void selectComboboxModel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) {
    myLafComboBoxModel.setSelectedItem(createLafReference(lookAndFeelInfo));
  }

  private List<LafReference> getLafReferences(ModelType type) {
    return ContainerUtil.map(ContainerUtil.filter(myLaFs.getValue(), info -> {
      if (type == ModelType.ALL) return true;
      if (info instanceof UIThemeBasedLookAndFeelInfo) {
        boolean isDark = ((UIThemeBasedLookAndFeelInfo)info).getTheme().isDark();
        return type == ModelType.DARK && isDark ||
               type == ModelType.LIGHT && !isDark;
      }
      return type == ModelType.LIGHT && info.getName().equals(IntelliJLaf.NAME) ||
             type == ModelType.DARK && info.getName().equals(DarculaLaf.NAME);
    }), LafManagerImpl::createLafReference);
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
  public UIManager.LookAndFeelInfo findLaf(LafReference reference) {
    return findLaf(reference.getClassName(), reference.getThemeId());
  }

  @Override
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return myCurrentLaf;
  }

  @Override
  public LafReference getLookAndFeelReference(@NotNull LafType type) {
    switch (type) {
      case PREFERRED_LIGHT:
        return createLafReference(myPreferredLightLaf);

      case PREFERRED_DARK:
        return createLafReference(myPreferredDarkLaf);

      case CURRENT:
      default:
        return createLafReference(myCurrentLaf);
    }
  }

  public UIManager.LookAndFeelInfo getDefaultLaf() {
    String wizardLafName = WelcomeWizardUtil.getWizardLAF();
    if (wizardLafName != null) {
      UIManager.LookAndFeelInfo laf = findLaf(wizardLafName);
      if (laf != null) return laf;
      LOG.error("Could not find wizard L&F: " + wizardLafName);
    }

    if (SystemInfo.isMac) {
      String className = IntelliJLaf.class.getName();
      UIManager.LookAndFeelInfo laf = findLaf(className);
      if (laf != null) return laf;
      LOG.error("Could not find OS X L&F: " + className);
    }

    String appLafName = WelcomeWizardUtil.getDefaultLAF();
    if (appLafName != null) {
      UIManager.LookAndFeelInfo laf = findLaf(appLafName);
      if (laf != null) return laf;
      LOG.error("Could not find app L&F: " + appLafName);
    }

    String defaultLafName = IntelliJLaf.class.getName();
    UIManager.LookAndFeelInfo laf = findLaf(defaultLafName);
    if (laf != null) return laf;
    throw new IllegalStateException("No default L&F found: " + defaultLafName);
  }

  @Nullable
  private UIManager.LookAndFeelInfo findLaf(@NotNull String className) {
    if (myDefaultLightLaf.getClassName().equals(className)) {
      return myDefaultLightLaf;
    }
    if (myDefaultDarkLaf.getClassName().equals(className)) {
      return myDefaultDarkLaf;
    }

    for (UIManager.LookAndFeelInfo l : myLaFs.getValue()) {
      if (!(l instanceof UIThemeBasedLookAndFeelInfo) && Objects.equals(l.getClassName(), className)) {
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
    setLookAndFeelImpl(lookAndFeelInfo, lockEditorScheme, false);
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  private void setLookAndFeelImpl(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme, boolean processChangeSynchronously) {
    UIManager.LookAndFeelInfo oldLaf = myCurrentLaf;

    if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo) {
      ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).dispose();
    }

    if (findLaf(lookAndFeelInfo.getClassName()) == null) {
      LOG.error("unknown LookAndFeel : " + lookAndFeelInfo);
      return;
    }

    UIManager.getDefaults().clear();
    UIManager.getDefaults().putAll(ourDefaults);

    // Set L&F
    if (IdeaLookAndFeelInfo.CLASS_NAME.equals(lookAndFeelInfo.getClassName())) { // that is IDEA default LAF
      IdeaLaf laf = new IdeaLaf();
      MetalLookAndFeel.setCurrentTheme(new IdeaBlueMetalTheme());
      try {
        UIManager.setLookAndFeel(laf);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        LOG.error(e);
        return;
      }
    }
    else if (DarculaLookAndFeelInfo.CLASS_NAME.equals(lookAndFeelInfo.getClassName())) {
      DarculaLaf laf = new DarculaLaf();
      try {
        UIManager.setLookAndFeel(laf);
        AppUIUtil.updateForDarcula(true);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        LOG.error(e);
        return;
      }
    }
    else { // non default LAF
      try {
        LookAndFeel laf;
        if (lookAndFeelInfo instanceof PluggableLafInfo) {
          laf = ((PluggableLafInfo)lookAndFeelInfo).createLookAndFeel();
        }
        else {
          laf = ((LookAndFeel)Class.forName(lookAndFeelInfo.getClassName()).newInstance());

          if (laf instanceof MetalLookAndFeel) {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
          }
          else if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
            if (laf instanceof UserDataHolder) {
              UserDataHolder userDataHolder = (UserDataHolder)laf;
              userDataHolder.putUserData(UIUtil.LAF_WITH_THEME_KEY, Boolean.TRUE);
            }
          }
        }

        UIManager.setLookAndFeel(laf);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        LOG.error(e);
        return;
      }
    }

    if (lookAndFeelInfo instanceof UIThemeBasedLookAndFeelInfo) {
      try {
        ((UIThemeBasedLookAndFeelInfo)lookAndFeelInfo).installTheme(UIManager.getLookAndFeelDefaults(), lockEditorScheme);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        LOG.error(e);
        return;
      }
    }

    if (SystemInfo.isMacOSYosemite) {
      installMacOSXFonts(UIManager.getLookAndFeelDefaults());
    }

    myCurrentLaf = ObjectUtils.chooseNotNull(lookAndFeelInfo, findLaf(lookAndFeelInfo.getClassName()));
    selectComboboxModel(myCurrentLaf);

    if (!myFirstSetup) {
      if (!lockEditorScheme) {
        if (processChangeSynchronously) {
          updateEditorSchemeIfNecessary(oldLaf, true);
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> updateEditorSchemeIfNecessary(oldLaf, false));
        }
      }
    }
    myFirstSetup = false;
  }

  private void updateEditorSchemeIfNecessary(UIManager.LookAndFeelInfo oldLaf, boolean processChangeSynchronously) {
    if (oldLaf instanceof TempUIThemeBasedLookAndFeelInfo || myCurrentLaf instanceof TempUIThemeBasedLookAndFeelInfo) return;
    if (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo) {
      if (((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().getEditorSchemeName() != null) {
        return;
      }
    }

    boolean dark = StartupUiUtil.isUnderDarcula();
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme current = colorsManager.getGlobalScheme();
    boolean wasUITheme = oldLaf instanceof UIThemeBasedLookAndFeelInfo;
    if (dark != ColorUtil.isDark(current.getDefaultBackground()) || wasUITheme) {
      String targetScheme = dark ? DarculaLaf.NAME : EditorColorsScheme.DEFAULT_SCHEME_NAME;
      PropertiesComponent properties = PropertiesComponent.getInstance();
      String savedEditorThemeKey = dark ? DARCULA_EDITOR_THEME_KEY : DEFAULT_EDITOR_THEME_KEY;
      String toSavedEditorThemeKey = dark ? DEFAULT_EDITOR_THEME_KEY : DARCULA_EDITOR_THEME_KEY;
      String themeName =  properties.getValue(savedEditorThemeKey);
      if (themeName != null && colorsManager.getScheme(themeName) != null) {
        targetScheme = themeName;
      }
      if (!wasUITheme) {
        properties.setValue(toSavedEditorThemeKey, current.getName(), dark ? EditorColorsScheme.DEFAULT_SCHEME_NAME : DarculaLaf.NAME);
      }

      EditorColorsScheme scheme = colorsManager.getScheme(targetScheme);
      if (scheme != null) {
        ((EditorColorsManagerImpl) colorsManager).setGlobalScheme(scheme, processChangeSynchronously);
      }
    }
    UISettings.getShadowInstance().fireUISettingsChanged();
    ActionToolbarImpl.updateAllToolbarsImmediately();
  }

  /**
   * @deprecated Use {@link AppUIUtil#updateForDarcula(boolean)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void updateForDarcula(boolean isDarcula) {
    AppUIUtil.updateForDarcula(isDarcula);
  }

  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in {@code UISettings}.
   */
  @Override
  public void updateUI() {
    final UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();
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

    fixMacOSDarkThemeDecorations();

    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue());
    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getPREFERRED_FRACTIONAL_METRICS_VALUE());

    for (Frame frame : Frame.getFrames()) {
      updateUI(frame);
    }

    ApplicationManager.getApplication().getMessageBus().syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(this);
    myEventDispatcher.getMulticaster().lookAndFeelChanged(this);
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnnecessaryReturnStatement"})
  private void fixMacOSDarkThemeDecorations() {
    if (!SystemInfo.isMacOSMojave) return;

    //if (myCurrentLaf == myDefaultDarkTheme
    //    || (myCurrentLaf instanceof UIThemeBasedLookAndFeelInfo && ((UIThemeBasedLookAndFeelInfo)myCurrentLaf).getTheme().isDark())) {
    //  todo[fokin]: apply dark decorators and dark file choosers if macOS Dark theme is enabled
    //}
  }

  @NotNull
  private static FontUIResource getFont(String yosemite, int size, @JdkConstants.FontStyle int style) {
    if (SystemInfo.isMacOSElCapitan) {
      // Text family should be used for relatively small sizes (<20pt), don't change to Display
      // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
      Font font = enableKerning(new Font(SystemInfo.isMacOSCatalina ? ".AppleSystemUIFont" : ".SF NS Text", style, size));
      if (!StartupUiUtil.isDialogFont(font)) {
        return new FontUIResource(font);
      }
    }
    return new FontUIResource(yosemite, style, size);
  }


  public static void installMacOSXFonts(UIDefaults defaults) {
    final String face = "Helvetica Neue";
    final FontUIResource uiFont = getFont(face, 13, Font.PLAIN);
    initFontDefaults(defaults, uiFont);
    for (Object key : new HashSet<>(defaults.keySet())) {
      if (!(key instanceof String)) continue;
      if (!StringUtil.endsWithIgnoreCase(((String)key), "font")) continue;
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

    FontUIResource uiFont11 = getFont(face, 11, Font.PLAIN);
    defaults.put("TableHeader.font", uiFont11);

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
    if (!SystemInfo.isMac && !SystemInfo.isWindows && Registry.is("linux.row.height.disabled")) {
      rowHeight = 0;
    }
    else if (rowHeight <= 0) {
      LOG.warn(key + " = " + value + " in " + UIManager.getLookAndFeel().getName() + "; it may lead to performance degradation");
    }
    defaults.put(key, rowHeight <= 0 ? 0 : JBUIScale.scale((int)(rowHeight / prevScale)));
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
    if (property != null) property = StringUtil.toLowerCase(property).trim();
    if (SystemInfo.isMacOSLeopard) {
      // force heavy weight popups under Leopard, otherwise they don't have shadow or any kind of border.
      popupWeight = OurPopupFactory.WEIGHT_HEAVY;
    }
    else if (property == null) {
      // use defaults if popup weight isn't specified
      if (SystemInfo.isWindows) {
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
    LafReference lf = myCurrentLaf == null ? null : getLookAndFeelReference(LafType.CURRENT);
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults != null) {
      for (String resource : ourPatchableFontResources) {
        defaults.put(resource, lfDefaults.get(resource));
      }
    }
    JBUIScale.setUserScaleFactor(JBUIScale.getFontScale(JBFont.label().getSize()));
  }

  private void storeOriginalFontDefaults(UIDefaults defaults) {
    LafReference lf = myCurrentLaf == null ? null : getLookAndFeelReference(LafType.CURRENT);
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults == null) {
      lfDefaults = new HashMap<>();
      for (String resource : ourPatchableFontResources) {
        lfDefaults.put(resource, defaults.get(resource));
      }
      myStoredDefaults.put(lf, lfDefaults);
    }
  }

  private static void updateUI(Window window) {
    IJSwingUtilities.updateComponentTreeUI(window);
    Window[] children = window.getOwnedWindows();
    for (Window w : children) {
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
  public boolean isAutoDetect() {
    return autodetect;
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
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK), copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_MASK | InputEvent.SHIFT_DOWN_MASK), pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_MASK | InputEvent.SHIFT_DOWN_MASK), cutActionKey);
    // Ctrl+C, Ctrl+V, Ctrl+X
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK), copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK), pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK), DefaultEditorKit.cutAction);
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

    if (!SystemInfo.isMac) {
      defaults.put("PasswordField.font", monoFont);
    }
    defaults.put("TextArea.font", monoFont);
    defaults.put("TextPane.font", textFont);
    defaults.put("EditorPane.font", textFont);
  }

  private static class OurPopupFactory extends PopupFactory {
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
      super(AllIcons.Icons.Ide.NextStep,
            dark.getAsBoolean() ? AllIcons.Icons.Ide.NextStepInverted : AllIcons.Icons.Ide.NextStep,
            IconLoader.getDisabledIcon(AllIcons.Icons.Ide.NextStep));
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

  private class UIThemeEPListener implements ExtensionPointListener<UIThemeProvider> {
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

      // When updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
      if (!myThemesInUpdatedPlugin.contains(theme.getId())) {
        setCurrentLookAndFeel(newTheme);
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
            else if (myUpdatingPlugin) {
              myThemesInUpdatedPlugin.add(theme.getId());
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
        setLookAndFeelImpl(switchLafTo, false, true);
        JBColor.setDark(switchLafTo == myDefaultDarkLaf);
        updateUI();
      }
    }
  }
}
