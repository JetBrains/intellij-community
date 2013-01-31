/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.idea.StartupUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacPopupMenuUI;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.action.GetPropertyAction;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
@State(
  name = "LafManager",
  roamingType = RoamingType.PER_PLATFORM,
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/options.xml")})
public final class LafManagerImpl extends LafManager implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.LafManager");

  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String GNOME_THEME_PROPERTY_NAME = "gnome.Net/ThemeName";

  @NonNls private static final String[] ourPatchableFontResources = {"Button.font", "ToggleButton.font", "RadioButton.font",
    "CheckBox.font", "ColorChooser.font", "ComboBox.font", "Label.font", "List.font", "MenuBar.font", "MenuItem.font",
    "MenuItem.acceleratorFont", "RadioButtonMenuItem.font", "CheckBoxMenuItem.font", "Menu.font", "PopupMenu.font", "OptionPane.font",
    "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font", "TabbedPane.font", "Table.font", "TableHeader.font",
    "TextField.font", "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font", "TitledBorder.font", "ToolBar.font",
    "ToolTip.font", "Tree.font"};

  @NonNls private static final String[] ourFileChooserTextKeys = {"FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
    "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"};

  @NonNls private static final String[] ourOptionPaneIconKeys = {"OptionPane.errorIcon", "OptionPane.informationIcon",
    "OptionPane.warningIcon", "OptionPane.questionIcon"};

  private static final String[] ourAlloyComponentsToPatchSelection = {"Tree", "MenuItem", "Menu", "List",
    "ComboBox", "Table", "TextArea", "EditorPane", "TextPane", "FormattedTextField", "PasswordField",
    "TextField", "RadioButtonMenuItem", "CheckBoxMenuItem"};

  private final EventListenerList myListenerList;
  private final UIManager.LookAndFeelInfo[] myLaFs;
  private UIManager.LookAndFeelInfo myCurrentLaf;
  private final HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>> myStoredDefaults = new HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>>();
  private final UISettings myUiSettings;
  private String myLastWarning = null;
  private PropertyChangeListener myThemeChangeListener = null;


  /**
   * Invoked via reflection.
   */
  LafManagerImpl(UISettings uiSettings) {
    myUiSettings = uiSettings;
    myListenerList = new EventListenerList();

    List<UIManager.LookAndFeelInfo> lafList = ContainerUtil.newArrayList();

    if (SystemInfo.isMac) {
      lafList.add(new UIManager.LookAndFeelInfo("Default", UIManager.getSystemLookAndFeelClassName()));
    }
    else {
      lafList.add(new IdeaLookAndFeelInfo());
      for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
        String name = laf.getName();
        if (!"Metal".equalsIgnoreCase(name) && !"CDE/Motif".equalsIgnoreCase(name)) {
          lafList.add(laf);
        }
      }
    }

    if (Registry.is("dark.laf.available")) {
      lafList.add(new DarculaLookAndFeelInfo());
    }

    myLaFs = lafList.toArray(new UIManager.LookAndFeelInfo[lafList.size()]);

    if (!SystemInfo.isMac) {
      // do not sort LaFs on mac - the order is determined as Default, Darcula.
      // when we leave only system LaFs on other OSes, the order also should be determined as Default, Darcula
      
      Arrays.sort(myLaFs, new Comparator<UIManager.LookAndFeelInfo>() {
        public int compare(UIManager.LookAndFeelInfo obj1, UIManager.LookAndFeelInfo obj2) {
          String name1 = obj1.getName();
          String name2 = obj2.getName();
          return name1.compareToIgnoreCase(name2);
        }
      });
    }

    myCurrentLaf = getDefaultLaf();
  }

  /**
   * Adds specified listener
   */
  public void addLafManagerListener(@NotNull final LafManagerListener l) {
    myListenerList.add(LafManagerListener.class, l);
  }

  /**
   * Removes specified listener
   */
  public void removeLafManagerListener(@NotNull final LafManagerListener l) {
    myListenerList.remove(LafManagerListener.class, l);
  }

  private void fireLookAndFeelChanged() {
    LafManagerListener[] listeners = myListenerList.getListeners(LafManagerListener.class);
    for (LafManagerListener listener : listeners) {
      listener.lookAndFeelChanged(this);
    }
  }

  @NotNull
  public String getComponentName() {
    return "LafManager";
  }

  public void initComponent() {
    if (myCurrentLaf != null) {
      final UIManager.LookAndFeelInfo laf = findLaf(myCurrentLaf.getClassName());
      if (laf != null) {
        setCurrentLookAndFeel(laf); // setup default LAF or one specified by readExternal.
      }
    }

    updateUI();

    if (SystemInfo.isXWindow) {
      myThemeChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(final PropertyChangeEvent evt) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              fixGtkPopupStyle();
              patchOptionPaneIcons(UIManager.getLookAndFeelDefaults());
            }
          });
        }
      };
      Toolkit.getDefaultToolkit().addPropertyChangeListener(GNOME_THEME_PROPERTY_NAME, myThemeChangeListener);
    }
  }

  public void disposeComponent() {
    if (myThemeChangeListener != null) {
      Toolkit.getDefaultToolkit().removePropertyChangeListener(GNOME_THEME_PROPERTY_NAME, myThemeChangeListener);
      myThemeChangeListener = null;
    }
  }

  public void loadState(final Element element) {
    String className = null;
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ELEMENT_LAF.equals(child.getName())) {
        className = child.getAttributeValue(ATTRIBUTE_CLASS_NAME);
        break;
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

  public Element getState() {
    Element element = new Element("state");
    if (myCurrentLaf != null) {
      String className = myCurrentLaf.getClassName();
      if (className != null){
        Element child=new Element(ELEMENT_LAF);
        child.setAttribute(ATTRIBUTE_CLASS_NAME, className);
        element.addContent(child);
      }
    }
    return element;
  }

  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels(){
    return myLaFs.clone();
  }

  public UIManager.LookAndFeelInfo getCurrentLookAndFeel(){
    return myCurrentLaf;
  }

  /**
   * @return default LookAndFeelInfo for the running OS. For Win32 and
   * Linux the method returns Alloy LAF or IDEA LAF if first not found, for Mac OS X it returns Aqua
   * RubyMine uses Native L&F for linux as well
   */
  private UIManager.LookAndFeelInfo getDefaultLaf() {
    final String systemLafClassName = UIManager.getSystemLookAndFeelClassName();
    if (SystemInfo.isMac) {
      UIManager.LookAndFeelInfo laf = findLaf(systemLafClassName);
      LOG.assertTrue(laf != null);
      return laf;
    }
    if (PlatformUtils.isRubyMine() || PlatformUtils.isPyCharm()) {
      final String desktop = AccessController.doPrivileged(new GetPropertyAction("sun.desktop"));
      if ("gnome".equals(desktop)) {
        UIManager.LookAndFeelInfo laf=findLaf(systemLafClassName);
        if (laf != null) {
          return laf;
        }
        LOG.info("Could not find system look and feel: " + laf);
      }
    }
    // Default
    final String defaultLafName = StartupUtil.getDefaultLAF();
    if (defaultLafName != null) {
      UIManager.LookAndFeelInfo defaultLaf = findLaf(defaultLafName);
      if (defaultLaf != null) {
        return defaultLaf;
      }
    }
    UIManager.LookAndFeelInfo ideaLaf = findLaf(IdeaLookAndFeelInfo.CLASS_NAME);
    if (ideaLaf != null) {
      return ideaLaf;
    }
    throw new IllegalStateException("No default look&feel found");
  }

  /**
   * Finds LAF by its class name.
   * will be returned.
   */
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
  public void setCurrentLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo) {
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
      MetalLookAndFeel.setCurrentTheme(new IdeaDarkMetalTheme());
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
    myCurrentLaf = lookAndFeelInfo;

    checkLookAndFeel(lookAndFeelInfo, false);
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
    if (!UIUtil.isUnderAquaLookAndFeel()) return null;
    final Icon arrow = (Icon) UIManager.get("Menu.arrowIcon");
    if (arrow == null) return null;

    try {
      final Method method = arrow.getClass().getMethod("getInvertedIcon");
      if (method != null) {
        method.setAccessible(true);
        return (Icon) method.invoke(arrow);
      }

      return null;
    }
    catch (NoSuchMethodException e1) {
      return null;
    }
    catch (InvocationTargetException e1) {
      return null;
    }
    catch (IllegalAccessException e1) {
      return null;
    }
  }

  @Override
  public boolean checkLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo) {
    return checkLookAndFeel(lookAndFeelInfo, true);
  }

  private boolean checkLookAndFeel(final UIManager.LookAndFeelInfo lafInfo, final boolean confirm) {
    String message = null;

    if (lafInfo.getName().contains("GTK") && SystemInfo.isXWindow && !SystemInfo.isJavaVersionAtLeast("1.6.0_12")) {
      message = IdeBundle.message("warning.problem.laf.1");
    }

    if (message != null) {
      if (confirm) {
        final String[] options = {IdeBundle.message("confirm.set.look.and.feel"), CommonBundle.getCancelButtonText()};
        final int result = Messages.showOkCancelDialog(message, CommonBundle.getWarningTitle(), options[0], options[1], Messages.getWarningIcon());
        if (result == 0) {
          myLastWarning = message;
          return true;
        }
        return false;
      }

      if (!message.equals(myLastWarning)) {
        Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "L&F Manager", message, NotificationType.WARNING,
                                                  NotificationListener.URL_OPENING_LISTENER));
        myLastWarning = message;
      }
    }

    return true;
  }

  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in <code>UISettings</code>.
   */
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

    patchFileChooserStrings(uiDefaults);

    patchLafFonts(uiDefaults);

    patchOptionPaneIcons(uiDefaults);

    fixSeparatorColor(uiDefaults);

    for (Frame frame : Frame.getFrames()) {
      updateUI(frame);
    }
    fireLookAndFeelChanged();
  }

  private static void fixMenuIssues(UIDefaults uiDefaults) {
    if (UIUtil.isUnderAquaLookAndFeel()) {
      // update ui for popup menu to get round corners
      uiDefaults.put("PopupMenuUI", MacPopupMenuUI.class.getCanonicalName());
      uiDefaults.put("Menu.invertedArrowIcon", getAquaMenuInvertedIcon());
      uiDefaults.put("Menu.disabledArrowIcon", getAquaMenuDisabledIcon());
    }
    else if (UIUtil.isUnderJGoodiesLookAndFeel()) {
      uiDefaults.put("Menu.opaque", true);
      uiDefaults.put("MenuItem.opaque", true);
    }
    uiDefaults.put("MenuItem.background", UIManager.getColor("Menu.background"));
  }

  private static void fixTreeWideSelection(UIDefaults uiDefaults) {
    if (UIUtil.isUnderAlloyIDEALookAndFeel() || UIUtil.isUnderJGoodiesLookAndFeel()) {
      final Color bg = new ColorUIResource(56, 117, 215);
      final Color fg = new ColorUIResource(Color.WHITE);
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
    if (property != null) property = property.toLowerCase().trim();
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
            try {
              Field f = style.getClass().getDeclaredField("xThickness");
              f.setAccessible(true);
              final Object x = f.get(style);
              if (x instanceof Integer && (Integer)x == 0) {
                // workaround for Sun bug #6636964
                f.set(style, 1);
                f = style.getClass().getDeclaredField("yThickness");
                f.setAccessible(true);
                f.set(style, 3);
              }
            }
            catch (Exception ignore) { }
          }
          return style;
        }
      });

    new JBPopupMenu();  // invokes updateUI() -> updateStyle()

    SynthLookAndFeel.setStyleFactory(original);
  }

  private static void patchFileChooserStrings(final UIDefaults defaults) {
    if (!defaults.containsKey(ourFileChooserTextKeys [0])) {
      // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
      for (String key : ourFileChooserTextKeys) {
        defaults.put(key, IdeBundle.message(key));
      }
    }
  }

  private static void patchOptionPaneIcons(final UIDefaults defaults) {
    if (UIUtil.isUnderGTKLookAndFeel() && defaults.get(ourOptionPaneIconKeys[0]) == null) {
      // GTK+ L&F keeps icons hidden in style
      final SynthStyle style = SynthLookAndFeel.getStyle(new JOptionPane(""), Region.DESKTOP_ICON);
      if (style != null) {
        for (final String key : ourOptionPaneIconKeys) {
          final Object icon = style.get(null, key);
          if (icon != null) defaults.put(key, icon);
        }
      }
    }
  }

  private void patchLafFonts(UIDefaults uiDefaults) {
    if (UISettings.getInstance().OVERRIDE_NONIDEA_LAF_FONTS) {
      storeOriginalFontDefaults(uiDefaults);
      initFontDefaults(uiDefaults, myUiSettings.FONT_FACE, myUiSettings.FONT_SIZE);
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
  }

  private void storeOriginalFontDefaults(UIDefaults defaults) {
    UIManager.LookAndFeelInfo lf = getCurrentLookAndFeel();
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults == null) {
      lfDefaults = new HashMap<String, Object>();
      for (String resource : ourPatchableFontResources) {
        lfDefaults.put(resource, defaults.get(resource));
      }
      myStoredDefaults.put(lf, lfDefaults);
    }
  }

  private static void updateUI(Window window){
    if(!window.isDisplayable()){
      return;
    }
    SwingUtilities.updateComponentTreeUI(window);
    Window[] children=window.getOwnedWindows();
    for (Window aChildren : children) {
      updateUI(aChildren);
    }
  }

  /**
   * Repaints all displayable window.
   */
  public void repaintUI(){
    Frame[] frames=Frame.getFrames();
    for (Frame frame : frames) {
      repaintUI(frame);
    }
  }

  private static void repaintUI(Window window){
    if(!window.isDisplayable()){
      return;
    }
    window.repaint();
    Window[] children=window.getOwnedWindows();
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
  public static void initInputMapDefaults(UIDefaults defaults){
    // Make ENTER work in JTrees
    InputMap treeInputMap = (InputMap)defaults.get("Tree.focusInputMap");
    if(treeInputMap!=null){ // it's really possible. For example,  GTK+ doesn't have such map
      treeInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"toggle");
    }
    // Cut/Copy/Paste in JTextAreas
    InputMap textAreaInputMap=(InputMap)defaults.get("TextArea.focusInputMap");
    if(textAreaInputMap!=null){ // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textAreaInputMap, false);
    }
    // Cut/Copy/Paste in JTextFields
    InputMap textFieldInputMap=(InputMap)defaults.get("TextField.focusInputMap");
    if(textFieldInputMap!=null){ // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(textFieldInputMap, false);
    }
    // Cut/Copy/Paste in JPasswordField
    InputMap passwordFieldInputMap=(InputMap)defaults.get("PasswordField.focusInputMap");
    if(passwordFieldInputMap!=null){ // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(passwordFieldInputMap, false);
    }
    // Cut/Copy/Paste in JTables
    InputMap tableInputMap=(InputMap)defaults.get("Table.ancestorInputMap");
    if(tableInputMap!=null){ // It really can be null, for example when LAF isn't properly initialized (Alloy license problem)
      installCutCopyPasteShortcuts(tableInputMap, true);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static void initFontDefaults(UIDefaults defaults, String fontFace, int fontSize) {
    defaults.put("Tree.ancestorInputMap", null);
    FontUIResource uiFont = new FontUIResource(fontFace, Font.PLAIN, fontSize);
    FontUIResource textFont = new FontUIResource("Serif", Font.PLAIN, fontSize);
    FontUIResource monoFont = new FontUIResource("Monospaced", Font.PLAIN, fontSize);

    for (String fontResource : ourPatchableFontResources) {
      defaults.put(fontResource, uiFont);
    }

    defaults.put("PasswordField.font", monoFont);
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

    public Popup getPopup(final Component owner, final Component contents, final int x, final int y) throws IllegalArgumentException {
      final Point point = fixPopupLocation(contents, x, y);

      final int popupType = UIUtil.isUnderGTKLookAndFeel() ? WEIGHT_HEAVY : PopupUtil.getPopupType(this);
      if (popupType >= 0) {
        PopupUtil.setPopupType(myDelegate, popupType);
      }

      final Popup popup = myDelegate.getPopup(owner, contents, point.x, point.y);
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
        catch (Exception ignored) { }
      }
    }
  }
}
