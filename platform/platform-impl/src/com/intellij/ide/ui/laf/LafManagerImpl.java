/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.ide.ui.laf.intellij.MacIntelliJIconCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacPopupMenuUI;
import com.intellij.ui.popup.OurHeavyWeightPopup;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.synth.Region;
import javax.swing.plaf.synth.SynthLookAndFeel;
import javax.swing.plaf.synth.SynthStyle;
import javax.swing.plaf.synth.SynthStyleFactory;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

@State(
  name = "LafManager",
  storages = {
    @Storage(value = "laf.xml", roamingType = RoamingType.PER_OS),
    @Storage(value = "options.xml", deprecated = true)
  }
)
public final class LafManagerImpl extends LafManager implements PersistentStateComponent<Element>, Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.LafManager");

  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String GNOME_THEME_PROPERTY_NAME = "gnome.Net/ThemeName";

  @NonNls private static final String[] ourPatchableFontResources = {"Button.font", "ToggleButton.font", "RadioButton.font",
    "CheckBox.font", "ColorChooser.font", "ComboBox.font", "Label.font", "List.font", "MenuBar.font", "MenuItem.font",
    "MenuItem.acceleratorFont", "RadioButtonMenuItem.font", "CheckBoxMenuItem.font", "Menu.font", "PopupMenu.font", "OptionPane.font",
    "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font", "TabbedPane.font", "Table.font", "TableHeader.font",
    "TextField.font", "FormattedTextField.font", "Spinner.font", "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font",
    "TitledBorder.font", "ToolBar.font", "ToolTip.font", "Tree.font"};

  @NonNls private static final String[] ourFileChooserTextKeys = {"FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
    "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"};

  private static final String[] ourAlloyComponentsToPatchSelection = {"Tree", "MenuItem", "Menu", "List",
    "ComboBox", "Table", "TextArea", "EditorPane", "TextPane", "FormattedTextField", "PasswordField",
    "TextField", "RadioButtonMenuItem", "CheckBoxMenuItem"};

  private final EventDispatcher<LafManagerListener> myEventDispatcher = EventDispatcher.create(LafManagerListener.class);
  private final UIManager.LookAndFeelInfo[] myLaFs;
  private UIManager.LookAndFeelInfo myCurrentLaf;
  private final Map<UIManager.LookAndFeelInfo, HashMap<String, Object>> myStoredDefaults = ContainerUtil.newHashMap();

  private static final Map<String, String> ourLafClassesAliases = ContainerUtil.newHashMap();
  static {
    ourLafClassesAliases.put("idea.dark.laf.classname", DarculaLookAndFeelInfo.CLASS_NAME);
  }

  public static boolean useIntelliJInsteadOfAqua() {
    return Registry.is("ide.mac.yosemite.laf") && isIntelliJLafEnabled() && SystemInfo.isJavaVersionAtLeast("1.8") && SystemInfo.isMacOSYosemite;
  }
  /**
   * Invoked via reflection.
   */
  LafManagerImpl() {
    List<UIManager.LookAndFeelInfo> lafList = ContainerUtil.newArrayList();

    if (SystemInfo.isMac) {
      if (useIntelliJInsteadOfAqua()) {
        lafList.add(new UIManager.LookAndFeelInfo("Default", IntelliJLaf.class.getName()));
      } else {
        lafList.add(new UIManager.LookAndFeelInfo("Default", UIManager.getSystemLookAndFeelClassName()));
      }
    }
    else {
      if (isIntelliJLafEnabled()) {
        lafList.add(new IntelliJLookAndFeelInfo());
      }
      else {
        lafList.add(new IdeaLookAndFeelInfo());
      }
      for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
        String name = laf.getName();
        if (!"Metal".equalsIgnoreCase(name)
            && !"CDE/Motif".equalsIgnoreCase(name)
            && !"Nimbus".equalsIgnoreCase(name)
            && !"Windows Classic".equalsIgnoreCase(name)
            && !name.startsWith("JGoodies")) {
          lafList.add(laf);
        }
      }
    }

    lafList.add(new DarculaLookAndFeelInfo());

    myLaFs = lafList.toArray(new UIManager.LookAndFeelInfo[lafList.size()]);

    if (!SystemInfo.isMac) {
      // do not sort LaFs on mac - the order is determined as Default, Darcula.
      // when we leave only system LaFs on other OSes, the order also should be determined as Default, Darcula

      Arrays.sort(myLaFs, (obj1, obj2) -> {
        String name1 = obj1.getName();
        String name2 = obj2.getName();
        return name1.compareToIgnoreCase(name2);
      });
    }

    myCurrentLaf = getDefaultLaf();
  }

  private static boolean isIntelliJLafEnabled() {
    return !Registry.is("idea.4.5.laf.enabled");
  }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener, @NotNull Disposable disposable) {
    myEventDispatcher.addListener(listener, disposable);
  }

  @Override
  public void removeLafManagerListener(@NotNull final LafManagerListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  @Override
  public void initComponent() {
    if (myCurrentLaf != null) {
      final UIManager.LookAndFeelInfo laf = findLaf(myCurrentLaf.getClassName());
      if (laf != null) {
        boolean needUninstall = UIUtil.isUnderDarcula();
        setCurrentLookAndFeel(laf); // setup default LAF or one specified by readExternal.
        updateWizardLAF(needUninstall);
      }
    }

    updateUI();

    if (SystemInfo.isXWindow) {
      PropertyChangeListener themeChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            fixGtkPopupStyle();
            patchGtkDefaults(UIManager.getLookAndFeelDefaults());
          });
        }
      };
      Toolkit.getDefaultToolkit().addPropertyChangeListener(GNOME_THEME_PROPERTY_NAME, themeChangeListener);
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          Toolkit.getDefaultToolkit().removePropertyChangeListener(GNOME_THEME_PROPERTY_NAME, themeChangeListener);
        }
      });
    }
  }

  public void updateWizardLAF(boolean wasUnderDarcula) {
    if (WelcomeWizardUtil.getWizardLAF() != null) {
      if (UIUtil.isUnderDarcula()) {
        DarculaInstaller.install();
      }
      else if (wasUnderDarcula) {
        DarculaInstaller.uninstall();
      }
      WelcomeWizardUtil.setWizardLAF(null);
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public void loadState(final Element element) {
    String className = null;
    Element lafElement = element.getChild(ELEMENT_LAF);
    if (lafElement != null) {
      className = lafElement.getAttributeValue(ATTRIBUTE_CLASS_NAME);
      if (className != null && ourLafClassesAliases.containsKey(className)) {
        className = ourLafClassesAliases.get(className);
      }
    }

    UIManager.LookAndFeelInfo laf = findLaf(className);
    // If LAF is undefined (wrong class name or something else) we have set default LAF anyway.
    if (laf == null) {
      laf = getDefaultLaf();
    }

    if (myCurrentLaf != null && !laf.getClassName().equals(myCurrentLaf.getClassName())) {
      setCurrentLookAndFeel(laf);
      updateUI();
    }

    myCurrentLaf = laf;
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    if (myCurrentLaf != null) {
      String className = myCurrentLaf.getClassName();
      if (className != null) {
        Element child = new Element(ELEMENT_LAF);
        child.setAttribute(ATTRIBUTE_CLASS_NAME, className);
        element.addContent(child);
      }
    }
    return element;
  }

  @NotNull
  @Override
  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels() {
    return myLaFs.clone();
  }

  @Override
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return myCurrentLaf;
  }

  public UIManager.LookAndFeelInfo getDefaultLaf() {
    String wizardLafName = WelcomeWizardUtil.getWizardLAF();
    if (wizardLafName != null) {
      UIManager.LookAndFeelInfo laf = findLaf(wizardLafName);
      if (laf != null) return laf;
      LOG.error("Could not find wizard L&F: " + wizardLafName);
    }

    if (SystemInfo.isMac) {
      String className = useIntelliJInsteadOfAqua() ? IntelliJLaf.class.getName() : UIManager.getSystemLookAndFeelClassName();
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

    String defaultLafName = isIntelliJLafEnabled() ? IntelliJLaf.class.getName() : IdeaLookAndFeelInfo.CLASS_NAME;
    UIManager.LookAndFeelInfo laf = findLaf(defaultLafName);
    if (laf != null) return laf;
    throw new IllegalStateException("No default L&F found: " + defaultLafName);
  }

  @Nullable
  private UIManager.LookAndFeelInfo findLaf(@Nullable String className) {
    if (className == null) {
      return null;
    }
    for (UIManager.LookAndFeelInfo laf : myLaFs) {
      if (Comparing.equal(laf.getClassName(), className)) {
        return laf;
      }
    }
    return null;
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  @Override
  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) {
    if (findLaf(lookAndFeelInfo.getClassName()) == null) {
      LOG.error("unknown LookAndFeel : " + lookAndFeelInfo);
      return;
    }
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
        return;
      }
    }
    else if (DarculaLookAndFeelInfo.CLASS_NAME.equals(lookAndFeelInfo.getClassName())) {
      DarculaLaf laf = new DarculaLaf();
      try {
        UIManager.setLookAndFeel(laf);
        updateForDarcula(true);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }
    else { // non default LAF
      try {
        LookAndFeel laf = ((LookAndFeel)Class.forName(lookAndFeelInfo.getClassName()).newInstance());
        if (laf instanceof MetalLookAndFeel) {
          MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
        }
        UIManager.setLookAndFeel(laf);
      }
      catch (Exception e) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.getMessage()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }
    myCurrentLaf = ObjectUtils.chooseNotNull(findLaf(lookAndFeelInfo.getClassName()), lookAndFeelInfo);
  }

  public static void updateForDarcula(boolean isDarcula) {
    JBColor.setDark(isDarcula);
    IconLoader.setUseDarkIcons(isDarcula);
  }

  public void setLookAndFeelAfterRestart(UIManager.LookAndFeelInfo lookAndFeelInfo) {
    myCurrentLaf = lookAndFeelInfo;
  }

  @Nullable
  private static Icon getAquaMenuDisabledIcon() {
    final Icon arrowIcon = (Icon)UIManager.get("Menu.arrowIcon");
    if (arrowIcon != null) {
      return IconLoader.getDisabledIcon(arrowIcon);
    }

    return null;
  }

  @Nullable
  private static Icon getAquaMenuInvertedIcon() {
    if (UIUtil.isUnderAquaLookAndFeel() || (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF())) {
      final Icon arrow = (Icon)UIManager.get("Menu.arrowIcon");
      if (arrow == null) return null;

      try {
        final Method method = ReflectionUtil.getMethod(arrow.getClass(), "getInvertedIcon");
        if (method != null) {
          return (Icon)method.invoke(arrow);
        }
        return null;
      }
      catch (InvocationTargetException | IllegalAccessException e1) {
        return null;
      }
    }
    return null;
  }

  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in {@code UISettings}.
   */
  @Override
  public void updateUI() {
    final UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();

    fixPopupWeight();

    fixGtkPopupStyle();

    fixTreeWideSelection(uiDefaults);

    fixMenuIssues(uiDefaults);

    if (UIUtil.isUnderAquaLookAndFeel()) {
      uiDefaults.put("Panel.opaque", Boolean.TRUE);
    }
    else if (UIUtil.isWinLafOnVista()) {
      uiDefaults.put("ComboBox.border", null);
    }

    initInputMapDefaults(uiDefaults);

    uiDefaults.put("Button.defaultButtonFollowsFocus", Boolean.FALSE);
    uiDefaults.put("Balloon.error.textInsets", new JBInsets(3, 8, 3, 8).asUIResource());

    patchFileChooserStrings(uiDefaults);

    patchLafFonts(uiDefaults);

    patchHiDPI(uiDefaults);

    patchGtkDefaults(uiDefaults);

    fixSeparatorColor(uiDefaults);

    for (Frame frame : Frame.getFrames()) {
      // OSX/Aqua fix: Some image caching components like ToolWindowHeader use
      // com.apple.laf.AquaNativeResources$CColorPaintUIResource
      // a Java wrapper for ObjC MagicBackgroundColor class (Java RGB values ignored).
      // MagicBackgroundColor always reports current Frame background.
      // So we need to set frames background to exact and correct value.
      if (SystemInfo.isMac) {
        //noinspection UseJBColor
        frame.setBackground(new Color(UIUtil.getPanelBackground().getRGB()));
      }

      updateUI(frame);
    }

    myEventDispatcher.getMulticaster().lookAndFeelChanged(this);
  }

  private static void patchHiDPI(UIDefaults defaults) {
    Object prevScaleVal = defaults.get("hidpi.scaleFactor");
    // used to normalize previously patched values
    float prevScale = prevScaleVal != null ? (Float)prevScaleVal : 1f;

    if (prevScale == JBUI.scale(1f) && prevScaleVal != null) return;

    List<String> myIntKeys = Arrays.asList("Tree.leftChildIndent",
                                           "Tree.rightChildIndent",
                                           "Tree.rowHeight");

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
          entry.setValue(Integer.valueOf(JBUI.scale(normValue)));
        }
      }
    }
    defaults.put("hidpi.scaleFactor", JBUI.scale(1f));
  }

  private static void fixMenuIssues(UIDefaults uiDefaults) {
    if (UIUtil.isUnderAquaLookAndFeel() || (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF())) {
      // update ui for popup menu to get round corners
      uiDefaults.put("PopupMenuUI", MacPopupMenuUI.class.getCanonicalName());
      uiDefaults.put("Menu.invertedArrowIcon", getAquaMenuInvertedIcon());
      uiDefaults.put("Menu.disabledArrowIcon", getAquaMenuDisabledIcon());
    }
    else if (UIUtil.isUnderJGoodiesLookAndFeel()) {
      uiDefaults.put("Menu.opaque", true);
      uiDefaults.put("MenuItem.opaque", true);
    }

    if (UIUtil.isUnderWin10LookAndFeel()) {
      uiDefaults.put("Menu.arrowIcon", new Win10MenuArrowIcon());
    } else if ((SystemInfo.isLinux || SystemInfo.isWindows) && (UIUtil.isUnderIntelliJLaF() || UIUtil.isUnderDarcula())) {
      uiDefaults.put("Menu.arrowIcon", new DefaultMenuArrowIcon(AllIcons.Actions.Right));
    }

    uiDefaults.put("MenuItem.background", UIManager.getColor("Menu.background"));
  }

  private static void fixTreeWideSelection(UIDefaults uiDefaults) {
    if (UIUtil.isUnderAlloyIDEALookAndFeel() || UIUtil.isUnderJGoodiesLookAndFeel()) {
      final Color bg = new ColorUIResource(56, 117, 215);
      final Color fg = new ColorUIResource(255, 255, 255);
      uiDefaults.put("info", bg);
      uiDefaults.put("textHighlight", bg);
      for (String key : ourAlloyComponentsToPatchSelection) {
        uiDefaults.put(key + ".selectionBackground", bg);
        uiDefaults.put(key + ".selectionForeground", fg);
      }
    }
  }

  private static void fixSeparatorColor(UIDefaults uiDefaults) {
    if (UIUtil.isUnderAquaLookAndFeel()) {
      uiDefaults.put("Separator.background", UIUtil.AQUA_SEPARATOR_BACKGROUND_COLOR);
      uiDefaults.put("Separator.foreground", UIUtil.AQUA_SEPARATOR_FOREGROUND_COLOR);
    }
  }

  /**
   * The following code is a trick! By default Swing uses lightweight and "medium" weight
   * popups to show JPopupMenu. The code below force the creation of real heavyweight menus -
   * this increases speed of popups and allows to get rid of some drawing artifacts.
   */
  private static void fixPopupWeight() {
    int popupWeight = OurPopupFactory.WEIGHT_MEDIUM;
    String property = System.getProperty("idea.popup.weight");
    if (property != null) property = property.toLowerCase(Locale.ENGLISH).trim();
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
      else if ("medium".equals(property)) {
        popupWeight = OurPopupFactory.WEIGHT_MEDIUM;
      }
      else if ("heavy".equals(property)) {
        popupWeight = OurPopupFactory.WEIGHT_HEAVY;
      }
      else {
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

  private static void fixGtkPopupStyle() {
    if (!UIUtil.isUnderGTKLookAndFeel()) return;

    final SynthStyleFactory original = SynthLookAndFeel.getStyleFactory();

    SynthLookAndFeel.setStyleFactory(new SynthStyleFactory() {
      @Override
      public SynthStyle getStyle(final JComponent c, final Region id) {
        final SynthStyle style = original.getStyle(c, id);
        if (id == Region.POPUP_MENU) {
          final Integer x = ReflectionUtil.getField(style.getClass(), style, int.class, "xThickness");
          if (x != null && x == 0) {
            // workaround for Sun bug #6636964
            ReflectionUtil.setField(style.getClass(), style, int.class, "xThickness", 1);
            ReflectionUtil.setField(style.getClass(), style, int.class, "yThickness", 3);
          }
        }
        return style;
      }
    });

    new JBPopupMenu();  // invokes updateUI() -> updateStyle()

    SynthLookAndFeel.setStyleFactory(original);
  }

  private static void patchFileChooserStrings(final UIDefaults defaults) {
    if (!defaults.containsKey(ourFileChooserTextKeys[0])) {
      // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
      for (String key : ourFileChooserTextKeys) {
        defaults.put(key, IdeBundle.message(key));
      }
    }
  }

  private static void patchGtkDefaults(UIDefaults defaults) {
    if (!UIUtil.isUnderGTKLookAndFeel()) return;

    Map<String, Icon> map = ContainerUtil.newHashMap(
      Arrays.asList("OptionPane.errorIcon", "OptionPane.informationIcon", "OptionPane.warningIcon", "OptionPane.questionIcon"),
      Arrays.asList(AllIcons.General.ErrorDialog, AllIcons.General.InformationDialog, AllIcons.General.WarningDialog, AllIcons.General.QuestionDialog));
    // GTK+ L&F keeps icons hidden in style
    SynthStyle style = SynthLookAndFeel.getStyle(new JOptionPane(""), Region.DESKTOP_ICON);
    for (String key : map.keySet()) {
      if (defaults.get(key) != null) continue;

      Object icon = style == null ? null : style.get(null, key);
      defaults.put(key, icon instanceof Icon ? icon : map.get(key));
    }

    Color fg = defaults.getColor("Label.foreground");
    Color bg = defaults.getColor("Label.background");
    if (fg != null && bg != null) {
      defaults.put("Label.disabledForeground", UIUtil.mix(fg, bg, 0.5));
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
      initFontDefaults(uiDefaults, new FontUIResource(uiSettings.getFontFace(), Font.PLAIN, uiSettings.getFontSize()));
      JBUI.setUserScaleFactor(JBUI.getFontScale(uiSettings.getFontSize()));
    }
    else {
      restoreOriginalFontDefaults(uiDefaults);
    }
  }

  private void restoreOriginalFontDefaults(UIDefaults defaults) {
    UIManager.LookAndFeelInfo lf = getCurrentLookAndFeel();
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults != null) {
      for (String resource : ourPatchableFontResources) {
        defaults.put(resource, lfDefaults.get(resource));
      }
    }
    JBUI.setUserScaleFactor(JBUI.getFontScale(JBUI.Fonts.label().getSize()));
  }

  private void storeOriginalFontDefaults(UIDefaults defaults) {
    UIManager.LookAndFeelInfo lf = getCurrentLookAndFeel();
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

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  @SuppressWarnings({"HardCodedStringLiteral"})
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

    public OurPopupFactory(final PopupFactory delegate) {
      myDelegate = delegate;
    }

    @Override
    public Popup getPopup(final Component owner, final Component contents, final int x, final int y) {
      final Point point = fixPopupLocation(contents, x, y);

      final int popupType = UIUtil.isUnderGTKLookAndFeel() ? WEIGHT_HEAVY : PopupUtil.getPopupType(this);
      if (popupType == WEIGHT_HEAVY && OurHeavyWeightPopup.isEnabled()) {
        return new OurHeavyWeightPopup(owner, contents, point.x, point.y);
      }
      if (popupType >= 0) {
        PopupUtil.setPopupType(myDelegate, popupType);
      }

      Popup popup = myDelegate.getPopup(owner, contents, point.x, point.y);
      Window window = UIUtil.getWindow(contents);
      String cleanupKey = "LafManagerImpl.rootPaneCleanup";
      if (window instanceof RootPaneContainer && window != UIUtil.getWindow(owner) &&
          ((RootPaneContainer)window).getRootPane().getClientProperty(cleanupKey) == null) {
        ((RootPaneContainer)window).getRootPane().putClientProperty(cleanupKey, cleanupKey);
        window.addWindowListener(new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            // cleanup will be handled by AbstractPopup wrapper
            if (PopupUtil.getPopupContainerFor(((RootPaneContainer)window).getRootPane()) != null) {
              window.removeWindowListener(this);
              ((RootPaneContainer)window).getRootPane().putClientProperty(cleanupKey, null);
            }
          }

          @Override
          public void windowClosed(WindowEvent e) {
            window.removeWindowListener(this);
            ((RootPaneContainer)window).getRootPane().putClientProperty(cleanupKey, null);
            DialogWrapper.cleanupRootPane(((RootPaneContainer)window).getRootPane());
            DialogWrapper.cleanupWindowListeners(window);
          }
        });
      }
      fixPopupSize(popup, contents);
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

    private static void fixPopupSize(final Popup popup, final Component contents) {
      if (!UIUtil.isUnderGTKLookAndFeel() || !(contents instanceof JPopupMenu)) return;

      for (Class<?> aClass = popup.getClass(); aClass != null && Popup.class.isAssignableFrom(aClass); aClass = aClass.getSuperclass()) {
        try {
          final Method getComponent = aClass.getDeclaredMethod("getComponent");
          getComponent.setAccessible(true);
          final Object component = getComponent.invoke(popup);
          if (component instanceof JWindow) {
            ((JWindow)component).setSize(new Dimension(0, 0));
          }
          break;
        }
        catch (Exception ignored) {
        }
      }
    }
  }

  private abstract static class MenuArrowIcon implements Icon, UIResource {
    private final Icon icon;
    private final Icon selectedIcon;
    private final Icon disabledIcon;

    private MenuArrowIcon(Icon icon, Icon selectedIcon, Icon disabledIcon) {
      this.icon = icon;
      this.selectedIcon = selectedIcon;
      this.disabledIcon = disabledIcon;
    }

    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
      JMenuItem b = (JMenuItem) c;
      ButtonModel model = b.getModel();

      if (!model.isEnabled()) {
        disabledIcon.paintIcon(c, g, x, y);
      } else if (model.isArmed() || ( c instanceof JMenu && model.isSelected())) {
        selectedIcon.paintIcon(c, g, x, y);
      } else {
        icon.paintIcon(c, g, x, y);
      }
    }

    @Override public int getIconWidth() {
      return icon.getIconWidth();
    }

    @Override public int getIconHeight() {
      return icon.getIconHeight();
    }
  }

  private static class DefaultMenuArrowIcon extends MenuArrowIcon {
    private static boolean invert = UIUtil.isUnderDarcula();
    private DefaultMenuArrowIcon(Icon icon) {
      super(invert ? IconUtil.brighter(icon, 2) : IconUtil.darker(icon, 2),
            IconUtil.brighter(icon, 8),
            invert ? IconUtil.darker(icon, 2) : IconUtil.brighter(icon, 2));
    }
  }

  private static class Win10MenuArrowIcon extends MenuArrowIcon {
    private static final String NAME = "menuTriangle";
    private Win10MenuArrowIcon() {
      super(MacIntelliJIconCache.getIcon(NAME, false, false, true),
            MacIntelliJIconCache.getIcon(NAME, true, false, true),
            MacIntelliJIconCache.getIcon(NAME, false, false, false));
    }
  }
}
