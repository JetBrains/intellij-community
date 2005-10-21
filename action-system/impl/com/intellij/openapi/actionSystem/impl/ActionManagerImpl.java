package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public final class ActionManagerImpl extends ActionManagerEx implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionManagerImpl");
  private static final int TIMER_DELAY = 500;
  private static final int UPDATE_DELAY_AFTER_TYPING = 500;

  private final Object myLock = new Object();
  private THashMap<String,Object> myId2Action;
  private THashMap<PluginId, THashSet<String>> myPlugin2Id;
  private TObjectIntHashMap<String> myId2Index;
  private THashMap<Object,String> myAction2Id;
  private ArrayList<String> myNotRegisteredInternalActionIds;
  private MyTimer myTimer;

  private int myRegisteredActionsCount;
  private ArrayList<AnActionListener> myActionListeners;
  private AnActionListener[] myCachedActionListeners;
  private String myLastPreformedActionId;
  private KeymapManager myKeymapManager;
  private DataManager myDataManager;
  private String myPrevPerformedActionId;
  private long myLastTimeEditorWasTypedIn = 0;
  @NonNls private static final String ACTION_ELEMENT_NAME = "action";
  @NonNls private static final String GROUP_ELEMENT_NAME = "group";
  @NonNls protected static final String ACTIONS_ELEMENT_NAME = "actions";
  @NonNls protected static final String CLASS_ATTR_NAME = "class";
  @NonNls protected static final String ID_ATTR_NAME = "id";
  @NonNls protected static final String INTERNAL_ATTR_NAME = "internal";
  @NonNls protected static final String ICON_ATTR_NAME = "icon";
  @NonNls protected static final String ADD_TO_GROUP_ELEMENT_NAME = "add-to-group";
  @NonNls protected static final String SHORTCUT_ELEMENT_NAME = "keyboard-shortcut";
  @NonNls protected static final String MOUSE_SHORTCUT_ELEMENT_NAME = "mouse-shortcut";
  @NonNls protected static final String DESCRIPTION = "description";
  @NonNls protected static final String TEXT = "text";
  @NonNls protected static final String POPAP_ATTR_NAME = "popup";
  @NonNls protected static final String SEPARATOR_ELEMENT_NAME = "separator";
  @NonNls protected static final String REFERENCE_ELEMENT_NAME = "reference";
  @NonNls protected static final String GROUPID_ATTR_NAME = "group-id";
  @NonNls protected static final String ANCHOR_ELEMENT_NAME = "anchor";
  @NonNls protected static final String FIRST = "first";
  @NonNls protected static final String LAST = "last";
  @NonNls protected static final String BEFORE = "before";
  @NonNls protected static final String AFTER = "after";
  @NonNls protected static final String RELATIVE_TO_ACTION_ATTR_NAME = "relative-to-action";
  @NonNls protected static final String FIRST_KEYSTROKE_ATTR_NAME = "first-keystroke";
  @NonNls protected static final String SECOND_KEYSTROKE_ATTR_NAME = "second-keystroke";
  @NonNls protected static final String KEYMAP_ATTR_NAME = "keymap";
  @NonNls protected static final String KEYSTROKE_ATTR_NAME = "keystroke";
  @NonNls protected static final String REF_ATTR_NAME = "ref";
  @NonNls private static final String ACTIONS_BUNDLE = "messages.ActionsBundle";

  ActionManagerImpl(KeymapManager keymapManager, DataManager dataManager) {
    myId2Action = new THashMap<String, Object>();
    myId2Index = new TObjectIntHashMap<String>();
    myAction2Id = new THashMap<Object, String>();
    myPlugin2Id = new THashMap<PluginId, THashSet<String>>();
    myNotRegisteredInternalActionIds = new ArrayList<String>();
    myActionListeners = new ArrayList<AnActionListener>();
    myCachedActionListeners = null;
    myKeymapManager = keymapManager;
    myDataManager = dataManager;
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public void addTimerListener(int delay, final TimerListener listener) {
    if (myTimer == null) {
      myTimer = new MyTimer();
      myTimer.start();
    }

    myTimer.addTimerListener(listener);
  }

  public void removeTimerListener(TimerListener listener) {
    LOG.assertTrue(myTimer != null);

    myTimer.removeTimerListener(listener);
  }

  public ActionPopupMenu createActionPopupMenu(String place, ActionGroup group) {
    return new ActionPopupMenuImpl(place, group);
  }

  public ActionToolbar createActionToolbar(final String place, final ActionGroup group, final boolean horizontal) {
    return new ActionToolbarImpl(place, group, horizontal, myDataManager, this, (KeymapManagerEx)myKeymapManager);
  }


  public void readExternal(Element element) {
    final ClassLoader classLoader = getClass().getClassLoader();
    for (final Object o : element.getChildren()) {
      Element children = (Element)o;
      if (ACTIONS_ELEMENT_NAME.equals(children.getName())) {
        processActionsElement(children, classLoader, null);
      }
    }
    registerActions();
  }

  private void registerActions() {
    final PluginDescriptor[] plugins = PluginManager.getPlugins();
    for (int i = 0; i < plugins.length; i++) {
      PluginDescriptor plugin = plugins[i];

      final Element e = plugin.getActionsDescriptionElement();
      if (e != null) {
        processActionsElement(e, plugin.getLoader(), plugin.getPluginId());
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public AnAction getAction(String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getAction(" + id + ")");
    }
    if (id == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("id cannot be null");
    }
    return getActionImpl(id, false);
  }

  private AnAction getActionImpl(String id, boolean canReturnStub) {
    AnAction action = (AnAction)myId2Action.get(id);
    if (!canReturnStub && action instanceof ActionStub) {
      action = convert((ActionStub)action);
    }
    return action;
  }

  /**
   * Converts action's stub to normal action.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private AnAction convert(ActionStub stub) {
    synchronized (myLock) {
      LOG.assertTrue(myAction2Id.contains(stub));
      myAction2Id.remove(stub);

      LOG.assertTrue(myId2Action.contains(stub.getId()));

      AnAction action = (AnAction)myId2Action.remove(stub.getId());
      LOG.assertTrue(action != null);
      LOG.assertTrue(action.equals(stub));

      Object obj;
      String className = stub.getClassName();
      try {
        obj = Class.forName(className, true, stub.getLoader()).newInstance();
      }
      catch (ClassNotFoundException e) {
        throw new IllegalStateException("class with name \"" + className + "\" not found");
      }
      catch (Exception e) {
        throw new IllegalStateException("cannot create class \"" + className + "\"");
      }

      if (!(obj instanceof AnAction)) {
        throw new IllegalStateException("class with name \"" + className + "\" should be instance of " + AnAction.class.getName());
      }

      stub.initAction((AnAction)obj);
      ((AnAction)obj).getTemplatePresentation().setText(stub.getText());

      myId2Action.put(stub.getId(), obj);
      myAction2Id.put(obj, stub.getId());

      return (AnAction)obj;
    }
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getId(AnAction action) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getId(" + action + ")");
    }
    if (action == null) {
      throw new IllegalArgumentException("action cannot be null");
    }
    LOG.assertTrue(!(action instanceof ActionStub));
    synchronized (myLock) {
      return myAction2Id.get(action);
    }
  }

  public String[] getActionIds(String idPrefix) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getActionIds(" + idPrefix + ")");
    }
    if (idPrefix == null) {
      LOG.error("idPrefix cannot be null");
      return null;
    }
    synchronized (myLock) {
      ArrayList<String> idList = new ArrayList<String>();
      for (String id : myId2Action.keySet()) {
        if (id.startsWith(idPrefix)) {
          idList.add(id);
        }
      }
      return idList.toArray(new String[idList.size()]);
    }
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses
   *         of <code>AnAction</code>.
   */
  @Nullable
  private AnAction processActionElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final PluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    @NonNls final String resBundleName = plugin != null ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = ResourceBundle.getBundle(resBundleName, Locale.getDefault(), loader);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processActionElement(" + element.getName() + ")");
    }
    if (!ACTION_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null || className.length() == 0) {
      LOG.error("action element should have specified \"class\" attribute");
      return null;
    }
    // read ID and register loaded action
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null || id.length() == 0) {
      LOG.error("ID of the action cannot be an empty string");
      return null;
    }
    if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    String text = loadTextForElement(element, bundle, id, ACTION_ELEMENT_NAME);

    if (text == null) {
      LOG.error("'text' attribute is mandatory (action ID=" + id + ")");
      return null;
    }

    ActionStub stub = new ActionStub(className, id, text, loader);
    Presentation presentation = stub.getTemplatePresentation();
    presentation.setText(text);

    // description

    presentation.setDescription(loadDescriptionForElement(element, bundle, id, ACTION_ELEMENT_NAME));

    // icon
    String iconPath = element.getAttributeValue(ICON_ATTR_NAME);
    if (iconPath != null) {
      try {
        final Class actionClass = Class.forName(className, true, loader);
        presentation.setIcon(IconLoader.getIcon(iconPath, actionClass));
      }
      catch (ClassNotFoundException ignored) {
      }
    }
    // process all links and key bindings if any
    for (final Object o : element.getChildren()) {
      Element e = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(e.getName())) {
        processAddToGroupNode(stub, e);
      }
      else if (SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processKeyboardShortcutNode(e, id);
      }
      else if (MOUSE_SHORTCUT_ELEMENT_NAME.equals(e.getName())) {
        processMouseShortcutNode(e, id);
      }
      else {
        LOG.error("unexpected name of element \"" + e.getName() + "\"");
        return null;
      }
    }
    // register action
    registerAction(id, stub, pluginId);
    return stub;
  }

  private String loadDescriptionForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    String description = null;
    if (bundle != null) {
      @NonNls final String key = elementType + "." + id + ".description";
      return CommonBundle.messageOrDefault(bundle, key, element.getAttributeValue(DESCRIPTION));
    } else {
      return element.getAttributeValue(DESCRIPTION);
    }
  }

  private String loadTextForElement(final Element element, final ResourceBundle bundle, final String id, String elementType) {
    return CommonBundle.messageOrDefault(bundle, elementType + "." + id + "." + TEXT, element.getAttributeValue(TEXT));
  }

  private AnAction processGroupElement(Element element, final ClassLoader loader, PluginId pluginId) {
    final PluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    @NonNls final String resBundleName = plugin != null ? plugin.getResourceBundleBaseName() : ACTIONS_BUNDLE;
    ResourceBundle bundle = null;
    if (resBundleName != null) {
      bundle = ResourceBundle.getBundle(resBundleName, Locale.getDefault(), loader);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processGroupElement(" + element.getName() + ")");
    }
    if (!GROUP_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue(CLASS_ATTR_NAME);
    if (className == null) { // use default group if class isn't specified
      className = DefaultActionGroup.class.getName();
    }
    try {
      Class aClass = Class.forName(className, true, loader);
      Object obj = new ConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(ApplicationManager.getApplication().getPicoContainer());

      if (!(obj instanceof ActionGroup)) {
        LOG.error("class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
        return null;
      }
      if (element.getChildren().size() != element.getChildren(ADD_TO_GROUP_ELEMENT_NAME).size() ) {  //
        if (!(obj instanceof DefaultActionGroup)) {
          LOG.error("class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                    " because there are children specified");
          return null;
        }
      }
      ActionGroup group = (ActionGroup)obj;
      // read ID and register loaded group
      String id = element.getAttributeValue(ID_ATTR_NAME);
      if (id != null && id.length() == 0) {
        LOG.error("ID of the group cannot be an empty string");
        return null;
      }
      if (Boolean.valueOf(element.getAttributeValue(INTERNAL_ATTR_NAME)).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id != null) {
        registerAction(id, group);
      }
      // text
      Presentation presentation = group.getTemplatePresentation();
      String text = loadTextForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      presentation.setText(text);
      // description
      String description = loadDescriptionForElement(element, bundle, id, GROUP_ELEMENT_NAME);
      presentation.setDescription(description);
      // icon
      String iconPath = element.getAttributeValue(ICON_ATTR_NAME);
      if (iconPath != null) {
        presentation.setIcon(IconLoader.getIcon(iconPath));
      }
      // popup
      String popup = element.getAttributeValue(POPAP_ATTR_NAME);
      if (popup != null) {
        group.setPopup(Boolean.valueOf(popup).booleanValue());
      }
      // process all group's children. There are other groups, actions, references and links
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, loader, pluginId);
          if (action != null) {
            assertActionIsGroupOrStub(action);
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child);
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          AnAction action = processGroupElement(child, loader, pluginId);
          if (action != null) {
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else if (ADD_TO_GROUP_ELEMENT_NAME.equals(name)) {
          processAddToGroupNode(group, child);
        }
        else if (REFERENCE_ELEMENT_NAME.equals(name)) {
          AnAction action = processReferenceElement(child);
          if (action != null) {
            ((DefaultActionGroup)group).add(action, this);
          }
        }
        else {
          LOG.error("unexpected name of element \"" + name + "\n");
          return null;
        }
      }
      return group;
    }
    catch (ClassNotFoundException e) {
      LOG.error("class with name \"" + className + "\" not found");
      return null;
    }
    catch (Exception e) {
      LOG.error("cannot create class \"" + className + "\"", e);
      return null;
    }
  }

  /**
   * @param element description of link
   */
  private void processAddToGroupNode(AnAction action, Element element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processAddToGroupNode(" + action + "," + element.getName() + ")");
    }

    // Real subclasses of AnAction should not be here
    if (!(action instanceof Separator)) {
      assertActionIsGroupOrStub(action);
    }

    if (!ADD_TO_GROUP_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    String groupId = element.getAttributeValue(GROUPID_ATTR_NAME);
    if (groupId == null || groupId.length() == 0) {
      LOG.error("attribute \"group-id\" should be defined");
      return;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      LOG.error("action with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group");
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      LOG.error("action with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName());
      return;
    }
    String anchorStr = element.getAttributeValue(ANCHOR_ELEMENT_NAME);
    if (anchorStr == null) {
      LOG.error("attribute \"anchor\" should be defined");
      return;
    }
    Anchor anchor;
    if (FIRST.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.FIRST;
    }
    else if (LAST.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.LAST;
    }
    else if (BEFORE.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.BEFORE;
    }
    else if (AFTER.equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.AFTER;
    }
    else {
      LOG.error("anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"");
      return;
    }
    String relativeToActionId = element.getAttributeValue(RELATIVE_TO_ACTION_ATTR_NAME);
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      LOG.error("\"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return;
    }
    ((DefaultActionGroup)parentGroup).add(action, new Constraints(anchor, relativeToActionId), this);
  }

  /**
   * @param parentGroup group wich is the parent of the separator. It can be <code>null</code> in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(DefaultActionGroup parentGroup, Element element) {
    if (!SEPARATOR_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    Separator separator = Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator, this);
    }
    // try to find inner <add-to-parent...> tag
    for (final Object o : element.getChildren()) {
      Element child = (Element)o;
      if (ADD_TO_GROUP_ELEMENT_NAME.equals(child.getName())) {
        processAddToGroupNode(separator, child);
      }
    }
  }

  private void processKeyboardShortcutNode(Element element, String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processKeyboardShortcutNode(" + element.getName() + ")");
    }

    String firstStrokeString = element.getAttributeValue(FIRST_KEYSTROKE_ATTR_NAME);
    if (firstStrokeString == null) {
      LOG.error("\"first-keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    KeyStroke firstKeyStroke = getKeyStroke(firstStrokeString);
    if (firstKeyStroke == null) {
      LOG.error("\"first-keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    KeyStroke secondKeyStroke = null;
    String secondStrokeString = element.getAttributeValue(SECOND_KEYSTROKE_ATTR_NAME);
    if (secondStrokeString != null) {
      secondKeyStroke = getKeyStroke(secondStrokeString);
      if (secondKeyStroke == null) {
        LOG.error("\"second-keystroke\" attribute has invalid value for action with id=" + actionId);
        return;
      }
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.trim().length() == 0) {
      LOG.error("attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      LOG.error("keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, new KeyboardShortcut(firstKeyStroke, secondKeyStroke));
  }

  private static void processMouseShortcutNode(Element element, String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processMouseShortcutNode(" + element.getName() + ")");
    }

    String keystrokeString = element.getAttributeValue(KEYSTROKE_ATTR_NAME);
    if (keystrokeString == null || keystrokeString.trim().length() == 0) {
      LOG.error("\"keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    MouseShortcut shortcut;
    try {
      shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
    }
    catch (Exception ex) {
      LOG.error("\"keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    String keymapName = element.getAttributeValue(KEYMAP_ATTR_NAME);
    if (keymapName == null || keymapName.length() == 0) {
      LOG.error("attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = KeymapManager.getInstance().getKeymap(keymapName);
    if (keymap == null) {
      LOG.error("keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, shortcut);
  }

  @Nullable
  private AnAction processReferenceElement(Element element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processReferenceElement(" + element.getName() + ")");
    }
    if (!REFERENCE_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String ref = element.getAttributeValue(REF_ATTR_NAME);

    if (ref==null) {
      // support old style references by id
      ref = element.getAttributeValue(ID_ATTR_NAME);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("ref=\"" + ref + "\"");
    }
    if (ref == null || ref.length() == 0) {
      LOG.error("ID of reference element should be defined");
      return null;
    }

    AnAction action = getActionImpl(ref, true);

    if (action == null) {
      if (!myNotRegisteredInternalActionIds.contains(ref)) {
        LOG.error("action specified by reference isn't registered (ID=" + ref + ")");
      }
      return null;
    }
    assertActionIsGroupOrStub(action);
    return action;
  }

  public void processActionsElement(Element element, ClassLoader loader, PluginId pluginId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processActionsNode(" + element.getName() + ")");
    }
    if (!ACTIONS_ELEMENT_NAME.equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    synchronized (myLock) {
      for (final Object o : element.getChildren()) {
        Element child = (Element)o;
        String name = child.getName();
        if (ACTION_ELEMENT_NAME.equals(name)) {
          AnAction action = processActionElement(child, loader, pluginId);
          if (action != null) {
            assertActionIsGroupOrStub(action);
          }
        }
        else if (GROUP_ELEMENT_NAME.equals(name)) {
          processGroupElement(child, loader, pluginId);
        }
        else if (SEPARATOR_ELEMENT_NAME.equals(name)) {
          processSeparatorNode(null, child);
        }
        else {
          LOG.error("unexpected name of element \"" + name + "\n");
        }
      }
    }
  }

  private void assertActionIsGroupOrStub(final AnAction action) {
    LOG.assertTrue(action instanceof ActionGroup || action instanceof ActionStub, "Action : "+action + "; class: "+action.getClass());
  }

  public void registerAction(String actionId, AnAction action, PluginId pluginId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: registerAction(" + action + ")");
    }
    if (action == null) {
      LOG.error("action cannot be null");
      return;
    }
    if (actionId == null) {
      LOG.error("action's id cannot be null");
      return;
    }
    synchronized (myLock) {
      if (myId2Action.containsKey(actionId)) {
        LOG.error("action with the ID \"" + actionId + "\" was already registered. Registered action is " + myId2Action.get(actionId));
        return;
      }
      if (myAction2Id.containsKey(action)) {
        LOG.error("action was already registered for another ID. ID is " + myAction2Id.get(action));
        return;
      }
      myId2Action.put(actionId, action);
      myId2Index.put(actionId, myRegisteredActionsCount++);
      myAction2Id.put(action, actionId);
      if (pluginId != null && !(action instanceof ActionGroup)){
        THashSet<String> pluginActionIds = myPlugin2Id.get(pluginId);
        if (pluginActionIds == null){
          pluginActionIds = new THashSet<String>();
          myPlugin2Id.put(pluginId, pluginActionIds);
        }
        pluginActionIds.add(actionId);
      }
      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId, myKeymapManager), null);
    }
  }

  public void registerAction(String actionId, AnAction action) {
    registerAction(actionId, action, null);
  }

  public void unregisterAction(String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: unregisterAction(" + actionId + ")");
    }
    if (actionId == null) {
      LOG.error("id cannot be null");
      return;
    }
    synchronized (myLock) {
      if (!myId2Action.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
          return;
        }
      }
      AnAction oldValue = (AnAction)myId2Action.remove(actionId);
      myAction2Id.remove(oldValue);
      myId2Index.remove(actionId);
      for (PluginId pluginName : myPlugin2Id.keySet()) {
        final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
        if (pluginActions != null) {
          pluginActions.remove(actionId);
        }
      }
    }
  }

  public String getComponentName() {
    return "ActionManager";
  }

  public Comparator<String> getRegistrationOrderComparator() {
    return new Comparator<String>() {
      public int compare(String id1, String id2) {
        return myId2Index.get(id1) - myId2Index.get(id2);
      }
    };
  }

  public String[] getPluginActions(PluginId pluginName) {
    if (myPlugin2Id.containsKey(pluginName)){
      final THashSet<String> pluginActions = myPlugin2Id.get(pluginName);
      return pluginActions.toArray(new String[pluginActions.size()]);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private AnActionListener[] getActionListeners() {
    if (myCachedActionListeners == null) {
      myCachedActionListeners = myActionListeners.toArray(new AnActionListener[myActionListeners.size()]);
    }

    return myCachedActionListeners;
  }

  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
    myCachedActionListeners = null;
  }

  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
    myCachedActionListeners = null;
  }

  public void fireBeforeActionPerformed(AnAction action, DataContext dataContext) {
    if (action != null) {
      myPrevPerformedActionId = myLastPreformedActionId;
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeActionPerformed(action, dataContext);
    }
  }

  public void fireBeforeEditorTyping(char c, DataContext dataContext) {
    myLastTimeEditorWasTypedIn = System.currentTimeMillis();
    AnActionListener[] listeners = getActionListeners();
    for (AnActionListener listener : listeners) {
      listener.beforeEditorTyping(c, dataContext);
    }
  }

  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }

  public String getPrevPreformedActionId() {
    return myPrevPerformedActionId;
  }

  private boolean haveActiveFrames() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    final WindowManagerEx wmanager = WindowManagerEx.getInstanceEx();
    if (wmanager == null) return false;
    for (Project project : projects) {
      final IdeFrame frame = wmanager.getFrame(project);
      if (frame != null && frame.getState() != JFrame.ICONIFIED) return true;
    }
    return false;
  }

  private class MyTimer extends Timer implements ActionListener {
    private final List<TimerListener> myTimerListeners = Collections.synchronizedList(new ArrayList<TimerListener>());

    MyTimer() {
      super(TIMER_DELAY, null);
      addActionListener(this);
      setRepeats(true);
    }

    public void addTimerListener(TimerListener listener){
      myTimerListeners.add(listener);
    }

    public void removeTimerListener(TimerListener listener){
      final boolean removed = myTimerListeners.remove(listener);
      LOG.assertTrue(removed, "Unknown listener " + listener);
    }

    public void actionPerformed(ActionEvent e) {
      if (myLastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis() || !haveActiveFrames()) {
        return;
      }

      TimerListener[] listeners = myTimerListeners.toArray(new TimerListener[myTimerListeners.size()]);
      for (TimerListener listener : listeners) {
        runListenerAction(listener);
      }
   }

    private void runListenerAction(final TimerListener listener) {
      ModalityState modalityState = listener.getModalityState();
      if (modalityState == null) return;
      if (!ModalityState.current().dominates(modalityState)) {
        listener.run();
      }
    }
  }
}