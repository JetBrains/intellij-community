/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
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
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        id = "other",
        file = "$APP_CONFIG$/options.xml")})
public final class LafManagerImpl extends LafManager implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.ui.LafManager");

  @NonNls private static final String IDEA_LAF_CLASSNAME = "idea.laf.classname";

  /**
   * One of the possible values of -Didea.popup.weight property. Heavy weight means
   * that all popups are shown inside the window. Under UNIXes it's possible to configure
   * window manager "Focus follows mouse with Auto Raise". In this case popup window will
   * be immediately closed after showing.
   */
  @NonNls private static final String HEAVY_WEIGHT_POPUP="heavy";
  /**
   * One of the possible values of -Didea.popup.weight property. Medium weight means
   * that popup will be shouw inside the paren't JLayeredPane if it can be fit to it.
   * Otherwise popup will be shown in the window. This mode is defaut for the Swing but
   * it's very slow (much slower then heavy weight popups).
   */
  @NonNls private static final String MEDIUM_WEIGHT_POPUP="medium";

  private final EventListenerList myListenerList;
  private final UIManager.LookAndFeelInfo[] myLafs;
  private UIManager.LookAndFeelInfo myCurrentLaf;

  @NonNls private static final String[] ourPatcheableFontResources = new String[]{
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

  private final HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>> myStoredDefaults = new HashMap<UIManager.LookAndFeelInfo, HashMap<String, Object>>();
  private final UISettings myUiSettings;

  @NonNls private static final String ELEMENT_LAF = "laf";
  @NonNls private static final String ATTRIBUTE_CLASS_NAME = "class-name";

  /** invoked by reflection
   * @param uiSettings   */
  LafManagerImpl(UISettings uiSettings){
    myUiSettings = uiSettings;
    myListenerList=new EventListenerList();

    IdeaLookAndFeelInfo ideaLaf=new IdeaLookAndFeelInfo();
    UIManager.LookAndFeelInfo[] installedLafs=UIManager.getInstalledLookAndFeels();

    // Get all installed LAFs
    myLafs=new UIManager.LookAndFeelInfo[1+installedLafs.length];
    myLafs[0]=ideaLaf;
    System.arraycopy(installedLafs,0,myLafs,1,installedLafs.length);
    Arrays.sort(myLafs,new MyComparator());

    // Setup current LAF. Unfortunately it's system depended.
    myCurrentLaf=getDefaultLaf();    
  }

  /**
   * Adds specified listener
   */
  public void addLafManagerListener(@NotNull final LafManagerListener l){
    myListenerList.add(LafManagerListener.class, l);
  }

  /**
   * Removes specified listener
   */
  public void removeLafManagerListener(@NotNull final LafManagerListener l){
    myListenerList.remove(LafManagerListener.class, l);
  }

  private void fireLookAndFeelChanged(){
    LafManagerListener[] listeners = myListenerList.getListeners(LafManagerListener.class);
    for (LafManagerListener listener : listeners) {
      listener.lookAndFeelChanged(this);
    }
  }

  @NotNull
  public String getComponentName(){
    return "LafManager";
  }

  public void initComponent() {
    setCurrentLookAndFeel(findLaf(myCurrentLaf.getClassName())); // setup default LAF or one specfied by readExternal.
    updateUI();
  }

  public void disposeComponent(){}

  public void loadState(final Element element) {

    boolean updateUI = myCurrentLaf != null;

    String className=null;
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ELEMENT_LAF.equals(child.getName())) {
        className = child.getAttributeValue(ATTRIBUTE_CLASS_NAME);
        break;
      }
    }

    UIManager.LookAndFeelInfo laf=findLaf(className);
    // If LAF is undefined (wrong class name or something else) we have set default LAF anyway.
    if(laf==null){
      laf=getDefaultLaf();
    }

    myCurrentLaf=laf;

    if (updateUI) {
      setCurrentLookAndFeel(laf);
      updateUI();
    }
  }

  public Element getState() {
    Element element = new Element("state");
    if(myCurrentLaf.getClassName()!=null){
      Element child=new Element(ELEMENT_LAF);
      child.setAttribute(ATTRIBUTE_CLASS_NAME,myCurrentLaf.getClassName());
      element.addContent(child);
    }
    return element;
  }

  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels(){
    return myLafs.clone();
  }

  public UIManager.LookAndFeelInfo getCurrentLookAndFeel(){
    return myCurrentLaf;
  }

  public boolean isUnderAquaLookAndFeel() {
    //noinspection HardCodedStringLiteral
    return "Mac OS X".equals(getCurrentLookAndFeel().getName());
  }

  public boolean isUnderQuaquaLookAndFeel() {
    //noinspection HardCodedStringLiteral
    return "Quaqua".equals(getCurrentLookAndFeel().getName());
  }

  /**
   * @return default LookAndFeelInfo for the running OS. For Win32 and
   * Linux the method returns Alloy LAF or IDEA LAF if first not found, for Mac OS X it returns Aqua
   */
  private UIManager.LookAndFeelInfo getDefaultLaf(){
    if(SystemInfo.isMac) {
      UIManager.LookAndFeelInfo laf=findLaf(UIManager.getSystemLookAndFeelClassName());
      LOG.assertTrue(laf!=null);
      return laf;
    }
    else {
      String defaultLafName = StartupUtil.getDefaultLAF();
      if (defaultLafName != null) {
        UIManager.LookAndFeelInfo defaultLaf = findLaf(defaultLafName);
        if (defaultLaf != null) {
          return defaultLaf;
        }
      }
      return findLaf(IDEA_LAF_CLASSNAME);
    }
  }

  /**
   * Finds LAF by its class name.
   * will be returned.
   */
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
  public void setCurrentLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo){
    if(findLaf(lookAndFeelInfo.getClassName())==null){
      LOG.error("unknown LookAndFeel : "+lookAndFeelInfo);
      return;
    }

    // Set L&F

    if(IDEA_LAF_CLASSNAME.equals(lookAndFeelInfo.getClassName())){ // that is IDEA default LAF
      IdeaLaf laf=new IdeaLaf();
      IdeaLaf.setCurrentTheme(new IdeaBlueMetalTheme());
      try {
        UIManager.setLookAndFeel(laf);
      } catch (Exception exc) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }else{ // non default LAF
      try {
        LookAndFeel laf=((LookAndFeel)Class.forName(lookAndFeelInfo.getClassName()).newInstance());
        if(laf instanceof MetalLookAndFeel){
          MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
        }
        UIManager.setLookAndFeel(laf);
      } catch(Exception exc) {
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }
    myCurrentLaf=lookAndFeelInfo;

    // The following code is a trick! By default Swing uses lightweight and "medium" weight
    // popups to show JPopupMenu. The code below force the creation of real heavyweight menus.
    // It dramatically increases speed of popups.

    //noinspection HardCodedStringLiteral
    String popupWeight=System.getProperty("idea.popup.weight");
    if(popupWeight==null){ // use defaults if popup weight isn't specified
      if(SystemInfo.isWindows){
        popupWeight=HEAVY_WEIGHT_POPUP;
      }else{ // UNIXes (Linux and MAC) go here
        popupWeight=MEDIUM_WEIGHT_POPUP;
      }
    }

    if (SystemInfo.isMacOSLeopard) {
      // Force heavy weight popups under Leopard, otherwise they don't have shadow or any kind of border.
      popupWeight = HEAVY_WEIGHT_POPUP;
    }

    popupWeight = popupWeight.trim();

    PopupFactory popupFactory;

    final PopupFactory oldFactory = PopupFactory.getSharedInstance();
    if(HEAVY_WEIGHT_POPUP.equals(popupWeight)){
      popupFactory=new PopupFactory(){
        public Popup getPopup(
          Component owner,
          Component contents,
          int x,
          int y
        ) throws IllegalArgumentException{
          final Point point = fixPopupLocation(contents, x, y);
          return oldFactory.getPopup(owner, contents, point.x, point.y);
        }
      };
    }else if(MEDIUM_WEIGHT_POPUP.equals(popupWeight)){
      popupFactory=new PopupFactory() {

        public Popup getPopup(final Component owner, final Component contents, final int x, final int y) throws IllegalArgumentException {
          return createPopup(owner, contents, x, y);
        }

        private Popup createPopup(final Component owner, final Component contents, final int x, final int y) {
          final Point point = fixPopupLocation(contents, x, y);
          return oldFactory.getPopup(owner, contents, point.x, point.y);
        }
      };
    }else{
      throw new IllegalStateException("unknown value of property -Didea.popup.weight: "+popupWeight);
    }
    PopupFactory.setSharedInstance(popupFactory);

    // update ui for popup menu to get round corners
    if (UIUtil.isUnderAquaLookAndFeel()) {
      final UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();
      uiDefaults.put("PopupMenuUI", MacPopupMenuUI.class.getCanonicalName());
      final Icon icon = getAquaMenuInvertedIcon();
      if (icon != null) {
        uiDefaults.put("Menu.invertedArrowIcon", icon);
      }
    }
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

  private Point fixPopupLocation(final Component contents, final int x, final int y) {
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


  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in <code>UISettings</code>.
   */
  public void updateUI(){
    UIDefaults lookAndFeelDefaults=UIManager.getLookAndFeelDefaults();
    initInputMapDefaults(lookAndFeelDefaults);
    patchFileChooserStrings(lookAndFeelDefaults);
    if (shouldPatchLAFFonts()) {
      storeOriginalFontDefaults(lookAndFeelDefaults);
      initFontDefaults(lookAndFeelDefaults);
    }
    else {
      restoreOriginalFontDefaults(lookAndFeelDefaults);
    }

    Frame[] frames=Frame.getFrames();
    for (Frame frame : frames) {
      updateUI(frame);
    }
    fireLookAndFeelChanged();
  }

  private void patchFileChooserStrings(final UIDefaults defaults) {
    if (!defaults.containsKey(ourFileChooserTextKeys [0])) {
      // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
      for (String key : ourFileChooserTextKeys) {
        defaults.put(key, IdeBundle.message(key));
      }
    }
  }

  private void restoreOriginalFontDefaults(UIDefaults defaults) {
    UIManager.LookAndFeelInfo lf = getCurrentLookAndFeel();
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults != null) {
      for (String resource : ourPatcheableFontResources) {
        defaults.put(resource, lfDefaults.get(resource));
      }
    }
  }

  private void storeOriginalFontDefaults(UIDefaults defaults) {
    UIManager.LookAndFeelInfo lf = getCurrentLookAndFeel();
    HashMap<String, Object> lfDefaults = myStoredDefaults.get(lf);
    if (lfDefaults == null) {
      lfDefaults = new HashMap<String, Object>();
      for (String resource : ourPatcheableFontResources) {
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

  private static void installCutCopyPasteShortcuts(InputMap inputMap, boolean useSimpleActionKeys){
    String copyActionKey = useSimpleActionKeys ? "copy" : DefaultEditorKit.copyAction;
    String pasteActionKey = useSimpleActionKeys ? "paste" : DefaultEditorKit.pasteAction;
    String cutActionKey = useSimpleActionKeys ? "cut" : DefaultEditorKit.cutAction;
    // Ctrl+Ins, Shift+Ins, Shift+Del
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,KeyEvent.CTRL_MASK|KeyEvent.CTRL_DOWN_MASK),copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT,KeyEvent.SHIFT_MASK|KeyEvent.SHIFT_DOWN_MASK),pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,KeyEvent.SHIFT_MASK|KeyEvent.SHIFT_DOWN_MASK),cutActionKey);
    // Ctrl+C, Ctrl+V, Ctrl+X
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C,KeyEvent.CTRL_MASK|KeyEvent.CTRL_DOWN_MASK),copyActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V,KeyEvent.CTRL_MASK|KeyEvent.CTRL_DOWN_MASK),pasteActionKey);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X,KeyEvent.CTRL_MASK|KeyEvent.CTRL_DOWN_MASK),DefaultEditorKit.cutAction);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initInputMapDefaults(UIDefaults defaults){
    // Make ENTER work in JTrees
    InputMap treeInputMap = (InputMap)defaults.get("Tree.focusInputMap");
    if(treeInputMap!=null){ // it's really possible. For examle,  GTK+ doesn't have such map
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
    // Cut/Copy/Paste in JPAsswordField
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

    for (String fontResource : ourPatcheableFontResources) {
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
      super(IdeBundle.message("idea.default.look.and.feel"), IDEA_LAF_CLASSNAME);
    }

    public boolean equals(Object obj){
      return (obj instanceof IdeaLookAndFeelInfo);
    }

    public int hashCode(){
      return getName().hashCode();
    }
  }

  private static final class MyComparator implements Comparator{
    public int compare(Object obj1,Object obj2){
      String name1=((UIManager.LookAndFeelInfo)obj1).getName();
      String name2=((UIManager.LookAndFeelInfo)obj2).getName();
      return name1.compareToIgnoreCase(name2);
    }
  }

  private static final class IdeaLaf extends MetalLookAndFeel{
    protected void initComponentDefaults(UIDefaults table) {
      super.initComponentDefaults(table);
      initInputMapDefaults(table);
      initIdeaDefaults(table);
    }

    protected void initSystemColorDefaults(UIDefaults table) {
      super.initSystemColorDefaults(table);
      /*
      table.put("control", new ColorUIResource(236, 233, 216));
      table.put("controlHighlight", new ColorUIResource(255, 255, 255));
      table.put("controlShadow", new ColorUIResource(172, 167, 153));
      */
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

//      Border scrollPaneBorder = new BorderUIResource(new BegBorders.ScrollPaneBorder());
//      defaults.put("ScrollPane.border", scrollPaneBorder);
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
//      defaults.put("ScrollPaneUI", BegScrollPaneUI.class.getName());

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
      defaults.put("Tree.expandedIcon", com.sun.java.swing.plaf.windows.WindowsTreeUI.ExpandedIcon.createExpandedIcon());
      defaults.put("Tree.collapsedIcon", com.sun.java.swing.plaf.windows.WindowsTreeUI.CollapsedIcon.createCollapsedIcon());
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
}
