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
package com.intellij.ide.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.StartupUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.IdeaBlueMetalTheme;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacPopupMenuUI;
import com.intellij.ui.plaf.beg.*;
import com.intellij.util.ui.UIUtil;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import com.sun.java.swing.plaf.windows.WindowsTreeUI;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.action.GetPropertyAction;

import javax.swing.*;
import javax.swing.border.Border;
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

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
@State(
  name = "LafManager",
  roamingType = RoamingType.PER_PLATFORM,
  storages = {@Storage(
    file = "$APP_CONFIG$/options.xml")})
public final class LafManagerImpl extends LafManager implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.LafManager");

  @NonNls private static final String IDEA_LAF_CLASS_NAME = "idea.laf.classname";
  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";
  @NonNls private static final String GNOME_THEME_PROPERTY_NAME = "gnome.Net/ThemeName";

  @NonNls private static final String[] ourPatchableFontResources = new String[]{
    "Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font", "ColorChooser.font", "ComboBox.font",
    "Label.font", "List.font", "MenuBar.font", "MenuItem.font", "MenuItem.acceleratorFont", "RadioButtonMenuItem.font",
    "CheckBoxMenuItem.font", "Menu.font", "PopupMenu.font", "OptionPane.font", "Panel.font", "ProgressBar.font",
    "ScrollPane.font", "Viewport.font", "TabbedPane.font", "Table.font", "TableHeader.font", "TextField.font",
    "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font", "TitledBorder.font", "ToolBar.font",
    "ToolTip.font", "Tree.font"
  };
  @NonNls private static final String[] ourFileChooserTextKeys = new String[] {
    "FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText", "FileChooser.listViewActionLabelText",
    "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"
  };
  @NonNls private static final String[] ourOptionPaneIconKeys = {
    "OptionPane.errorIcon", "OptionPane.informationIcon", "OptionPane.warningIcon", "OptionPane.questionIcon"
  };

  private final EventListenerList myListenerList;
  private final UIManager.LookAndFeelInfo[] myLafs;
  private UIManager.LookAndFeelInfo myCurrentLaf;
  private final HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>> myStoredDefaults = new HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>>();
  private final UISettings myUiSettings;
  private String myLastWarning = null;
  private PropertyChangeListener myThemeChangeListener = null;


  /**
   * invoked by reflection
   *
   * @param uiSettings
   */
  LafManagerImpl(UISettings uiSettings) {
    myUiSettings = uiSettings;
    myListenerList = new EventListenerList();

    IdeaLookAndFeelInfo ideaLaf = new IdeaLookAndFeelInfo();
    UIManager.LookAndFeelInfo[] installedLafs = UIManager.getInstalledLookAndFeels();

    // Get all installed LAFs
    myLafs = new UIManager.LookAndFeelInfo[1 + installedLafs.length];
    myLafs[0] = ideaLaf;
    System.arraycopy(installedLafs, 0, myLafs, 1, installedLafs.length);
    Arrays.sort(myLafs, new Comparator<UIManager.LookAndFeelInfo>() {
      public int compare(UIManager.LookAndFeelInfo obj1, UIManager.LookAndFeelInfo obj2) {
        String name1 = obj1.getName();
        String name2 = obj2.getName();
        return name1.compareToIgnoreCase(name2);
      }
    });

    // Setup current LAF. Unfortunately it's system depended.
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
      setCurrentLookAndFeel(findLaf(myCurrentLaf.getClassName())); // setup default LAF or one specified by readExternal.
    }
    updateUI();

    if (SystemInfo.isLinux) {
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
    return myLafs.clone();
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
    final String lowercaseProductName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    final String systemLafClassName = UIManager.getSystemLookAndFeelClassName();
    if (SystemInfo.isMac) {
      UIManager.LookAndFeelInfo laf = findLaf(systemLafClassName);
      LOG.assertTrue(laf != null);
      return laf;
    }
    if ("Rubymine".equals(lowercaseProductName) || "Pycharm".equals(lowercaseProductName)) {
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
    UIManager.LookAndFeelInfo ideaLaf = findLaf(IDEA_LAF_CLASS_NAME);
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
  private UIManager.LookAndFeelInfo findLaf(String className){
    for (UIManager.LookAndFeelInfo laf : myLafs) {
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
    if (IDEA_LAF_CLASS_NAME.equals(lookAndFeelInfo.getClassName())) { // that is IDEA default LAF
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

    if (lafInfo.getName().contains("GTK") && SystemInfo.isLinux && !SystemInfo.isJavaVersionAtLeast("1.6.0_12")) {
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
    fixPopupWeight();

    fixGtkPopupStyle();

    final UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();
    if (UIUtil.isUnderAquaLookAndFeel()) {
      // update ui for popup menu to get round corners
      uiDefaults.put("PopupMenuUI", MacPopupMenuUI.class.getCanonicalName());
      uiDefaults.put("Menu.invertedArrowIcon", getAquaMenuInvertedIcon());
      uiDefaults.put("Menu.disabledArrowIcon", getAquaMenuDisabledIcon());
    }

    initInputMapDefaults(uiDefaults);

    UIManager.put("Button.defaultButtonFollowsFocus", Boolean.FALSE);

    patchFileChooserStrings(uiDefaults);
    if (shouldPatchLAFFonts()) {
      storeOriginalFontDefaults(uiDefaults);
      initFontDefaults(uiDefaults);
    }
    else {
      restoreOriginalFontDefaults(uiDefaults);
    }

    patchOptionPaneIcons(uiDefaults);

    Frame[] frames = Frame.getFrames();
    for (Frame frame : frames) {
      updateUI(frame);
    }
    fireLookAndFeelChanged();
    
    fixSeparatorColor(uiDefaults);
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

    new JPopupMenu();  // invokes updateUI() -> updateStyle()

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

  private boolean shouldPatchLAFFonts() {
    //noinspection HardCodedStringLiteral
    return getCurrentLookAndFeel().getName().startsWith("IDEA") || UISettings.getInstance().OVERRIDE_NONIDEA_LAF_FONTS;
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
  private static void initInputMapDefaults(UIDefaults defaults){
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
  private void initFontDefaults(UIDefaults defaults) {
    defaults.put("Tree.ancestorInputMap", null);
    int uiFontSize = myUiSettings.FONT_SIZE;
    String uiFontFace = myUiSettings.FONT_FACE;
    FontUIResource font = new FontUIResource(uiFontFace, Font.PLAIN, uiFontSize);
    FontUIResource font1 = new FontUIResource("Serif", Font.PLAIN, uiFontSize);
    FontUIResource font3 = new FontUIResource("Monospaced", Font.PLAIN, uiFontSize);

    for (String fontResource : ourPatchableFontResources) {
      defaults.put(fontResource, font);
    }

    defaults.put("PasswordField.font", font3);
    defaults.put("TextArea.font", font3);
    defaults.put("TextPane.font", font1);
    defaults.put("EditorPane.font", font1);
    defaults.put("TitledBorder.font", font);
  }


  private static final class IdeaLookAndFeelInfo extends UIManager.LookAndFeelInfo{
    public IdeaLookAndFeelInfo(){
      super(IdeBundle.message("idea.default.look.and.feel"), IDEA_LAF_CLASS_NAME);
    }

    public boolean equals(Object obj){
      return (obj instanceof IdeaLookAndFeelInfo);
    }

    public int hashCode(){
      return getName().hashCode();
    }
  }

  private static final class IdeaLaf extends MetalLookAndFeel{
    protected void initComponentDefaults(UIDefaults table) {
      super.initComponentDefaults(table);
      initInputMapDefaults(table);
      initIdeaDefaults(table);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private static void initIdeaDefaults(UIDefaults defaults) {
      defaults.put("Menu.maxGutterIconWidth", 18);
      defaults.put("MenuItem.maxGutterIconWidth", 18);
      // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
      defaults.put("MenuItem.acceleratorDelimiter", "-");

      defaults.put("TitledBorder.titleColor",IdeaBlueMetalTheme.primary1);
      ColorUIResource col = new ColorUIResource(230, 230, 230);
      defaults.put("ScrollBar.background", col);
      defaults.put("ScrollBar.track", col);

      //Border scrollPaneBorder = new BorderUIResource(new BegBorders.ScrollPaneBorder());
      //defaults.put("ScrollPane.border", scrollPaneBorder);
      defaults.put("TextField.border", BegBorders.getTextFieldBorder());
      defaults.put("PasswordField.border", BegBorders.getTextFieldBorder());
      Border popupMenuBorder = new BegPopupMenuBorder();
      defaults.put("PopupMenu.border", popupMenuBorder);
      defaults.put("ScrollPane.border", BegBorders.getScrollPaneBorder());

      defaults.put("ButtonUI", BegButtonUI.class.getName());
      defaults.put("ComboBoxUI", BegComboBoxUI.class.getName());
      defaults.put("RadioButtonUI", BegRadioButtonUI.class.getName());
      defaults.put("CheckBoxUI", BegCheckBoxUI.class.getName());
      defaults.put("TabbedPaneUI", BegTabbedPaneUI.class.getName());
      defaults.put("TableUI", BegTableUI.class.getName());
      defaults.put("TreeUI", BegTreeUI.class.getName());
      //defaults.put("ScrollPaneUI", BegScrollPaneUI.class.getName());

      defaults.put("TabbedPane.tabInsets", new Insets(0, 4, 0, 4));
      defaults.put("ToolTip.background", new ColorUIResource(255, 255, 231));
      defaults.put("ToolTip.border", new ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1));
      defaults.put("Tree.ancestorInputMap", null);
      defaults.put("FileView.directoryIcon", IconLoader.getIcon("/nodes/folder.png"));
      defaults.put("FileChooser.upFolderIcon", IconLoader.getIcon("/nodes/upFolder.png"));
      defaults.put("FileChooser.newFolderIcon", IconLoader.getIcon("/nodes/newFolder.png"));
      defaults.put("FileChooser.homeFolderIcon", IconLoader.getIcon("/nodes/homeFolder.png"));
      defaults.put("OptionPane.errorIcon", IconLoader.getIcon("/general/errorDialog.png"));
      defaults.put("OptionPane.informationIcon", IconLoader.getIcon("/general/informationDialog.png"));
      defaults.put("OptionPane.warningIcon", IconLoader.getIcon("/general/warningDialog.png"));
      defaults.put("OptionPane.questionIcon", IconLoader.getIcon("/general/questionDialog.png"));
      defaults.put("Tree.openIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeOpen.gif"));
      defaults.put("Tree.closedIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeClosed.gif"));
      defaults.put("Tree.leafIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeLeaf.gif"));
      defaults.put("Tree.expandedIcon", WindowsTreeUI.ExpandedIcon.createExpandedIcon());
      defaults.put("Tree.collapsedIcon", WindowsTreeUI.CollapsedIcon.createCollapsedIcon());
      defaults.put("Table.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[] {
                         "ctrl C", "copy",
                         "ctrl V", "paste",
                         "ctrl X", "cut",
                           "COPY", "copy",
                          "PASTE", "paste",
                            "CUT", "cut",
                 "control INSERT", "copy",
                   "shift INSERT", "paste",
                   "shift DELETE", "cut",
                          "RIGHT", "selectNextColumn",
                       "KP_RIGHT", "selectNextColumn",
                           "LEFT", "selectPreviousColumn",
                        "KP_LEFT", "selectPreviousColumn",
                           "DOWN", "selectNextRow",
                        "KP_DOWN", "selectNextRow",
                             "UP", "selectPreviousRow",
                          "KP_UP", "selectPreviousRow",
                    "shift RIGHT", "selectNextColumnExtendSelection",
                 "shift KP_RIGHT", "selectNextColumnExtendSelection",
                     "shift LEFT", "selectPreviousColumnExtendSelection",
                  "shift KP_LEFT", "selectPreviousColumnExtendSelection",
                     "shift DOWN", "selectNextRowExtendSelection",
                  "shift KP_DOWN", "selectNextRowExtendSelection",
                       "shift UP", "selectPreviousRowExtendSelection",
                    "shift KP_UP", "selectPreviousRowExtendSelection",
                        "PAGE_UP", "scrollUpChangeSelection",
                      "PAGE_DOWN", "scrollDownChangeSelection",
                           "HOME", "selectFirstColumn",
                            "END", "selectLastColumn",
                  "shift PAGE_UP", "scrollUpExtendSelection",
                "shift PAGE_DOWN", "scrollDownExtendSelection",
                     "shift HOME", "selectFirstColumnExtendSelection",
                      "shift END", "selectLastColumnExtendSelection",
                   "ctrl PAGE_UP", "scrollLeftChangeSelection",
                 "ctrl PAGE_DOWN", "scrollRightChangeSelection",
                      "ctrl HOME", "selectFirstRow",
                       "ctrl END", "selectLastRow",
             "ctrl shift PAGE_UP", "scrollRightExtendSelection",
           "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
                "ctrl shift HOME", "selectFirstRowExtendSelection",
                 "ctrl shift END", "selectLastRowExtendSelection",
                            "TAB", "selectNextColumnCell",
                      "shift TAB", "selectPreviousColumnCell",
                          //"ENTER", "selectNextRowCell",
                    "shift ENTER", "selectPreviousRowCell",
                         "ctrl A", "selectAll",
                         //"ESCAPE", "cancel",
                             "F2", "startEditing"
           }));
    }
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
