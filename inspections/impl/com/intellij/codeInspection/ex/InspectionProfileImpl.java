package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author max
 */
public class InspectionProfileImpl extends ProfileEx implements ModifiableModel, InspectionProfile {
  @NonNls public static final InspectionProfileImpl DEFAULT_PROFILE = new InspectionProfileImpl("Default");
  static {
    final InspectionProfileEntry[] inspectionTools = DEFAULT_PROFILE.getInspectionTools();
    for (InspectionProfileEntry tool : inspectionTools) {
      final String shortName = tool.getShortName();
      HighlightDisplayKey key;
      if (tool instanceof LocalInspectionToolWrapper) {
        key = HighlightDisplayKey.register(shortName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
      } else {
        key = HighlightDisplayKey.register(shortName);
      }
      if (key != null) { //in case when short name isn't unique
        DEFAULT_PROFILE.myDisplayLevelMap.put(key, new ToolState(tool.getDefaultLevel(), tool.isEnabledByDefault()));
      }
    }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  @NonNls private static String VALID_VERSION = "1.0";

  private HashMap<String, InspectionTool> myTools = new HashMap<String, InspectionTool>();

  //diff map with base profile
  private LinkedHashMap<HighlightDisplayKey, ToolState> myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>();
  private boolean myLockedProfile = false;

  protected InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String INSPECTION_TOOL_TAG = "inspection_tool";
  @NonNls private static final String ENABLED_TAG = "enabled";
  @NonNls private static final String LEVEL_TAG = "level";
  @NonNls private static final String CLASS_TAG = "class";
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String ROOT_ELEMENT_TAG = "inspections";
  private String myEnabledTool = null;
  @NonNls private static final String USED_LEVELS = "used_levels";
  private boolean myOverrideSeverities = true;

  private InspectionToolRegistrar myRegistrar;
  @NonNls private static final String IS_LOCKED = "is_locked";

//private String myBaseProfileName;

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  private boolean myModified = false;
  private boolean myInitialized = false;

  private VisibleTreeState myVisibleTreeState = new VisibleTreeState();

  InspectionProfileImpl(InspectionProfileImpl inspectionProfile) {
    super(inspectionProfile.getName());

    myRegistrar = inspectionProfile.myRegistrar;
    myTools = new HashMap<String, InspectionTool>();
    initInspectionTools();

    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(inspectionProfile.myDisplayLevelMap);
    myVisibleTreeState = new VisibleTreeState(inspectionProfile.myVisibleTreeState);

    myBaseProfile = inspectionProfile.myBaseProfile;
    myLocal = inspectionProfile.myLocal;
    myLockedProfile = inspectionProfile.myLockedProfile;
    myFile = inspectionProfile.myFile;
    mySource = inspectionProfile;
    copyFrom(inspectionProfile);
  }

  public InspectionProfileImpl(final String inspectionProfile, 
                               final File file,
                               final InspectionToolRegistrar registrar) {
    super(inspectionProfile, file);
    myRegistrar = registrar;
    myBaseProfile = DEFAULT_PROFILE;
  }

  public InspectionProfileImpl(@NonNls String name) {
    super(name);
    myInitialized = true;
    myRegistrar = InspectionToolRegistrar.getInstance();
  }

  public InspectionProfile getParentProfile() {
    return mySource;
  }

  public String getBaseProfileName() {
    if (myBaseProfile == null) return null;
    return myBaseProfile.getName();
  }

  public void setBaseProfile(InspectionProfile profile) {
    myBaseProfile = (InspectionProfileImpl)profile;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isChanged() {
    if (mySource != null && mySource.myLockedProfile != myLockedProfile) return true;
    return myModified;
  }

  public VisibleTreeState getExpandedNodes() {
    return myVisibleTreeState;
  }

  private static boolean toolSettingsAreEqual(HighlightDisplayKey key,
                                       InspectionProfileImpl profile1,
                                       InspectionProfileImpl profile2) {
    final String toolName = key.toString();
    final InspectionProfileEntry tool1 = profile1.getInspectionTool(toolName);//findInspectionToolByName(profile1, toolDisplayName);
    final InspectionProfileEntry tool2 = profile2.getInspectionTool(toolName);//findInspectionToolByName(profile2, toolDisplayName);
    if (tool1 == null && tool2 == null) {
      return true;
    }
    if (tool1 != null && tool2 != null) {
      try {
        @NonNls String tempRoot = "root";
        Element oldToolSettings = new Element(tempRoot);
        tool1.writeSettings(oldToolSettings);
        Element newToolSettings = new Element(tempRoot);
        tool2.writeSettings(newToolSettings);
        return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  public boolean isProperSetting(HighlightDisplayKey key) {
    if (myBaseProfile == null) {
      return false;
    }
    final boolean toolsSettings = toolSettingsAreEqual(key, this, myBaseProfile);
    if (myDisplayLevelMap.keySet().contains(key)) {
      if (toolsSettings && myDisplayLevelMap.get(key).equals(myBaseProfile.getToolState(key))) {
        if (!myLockedProfile) {
          myDisplayLevelMap.remove(key);
        }
        return false;
      }
      return true;
    }


    if (!toolsSettings) {
      myDisplayLevelMap.put(key, myBaseProfile.getToolState(key));
      return true;
    }

    return false;
  }


  public void resetToBase() {
    myDisplayLevelMap.clear();
    copyToolsConfigurations(myBaseProfile);
    myInitialized = true;
  }

  public String getName() {
    return myName;
  }

  public void patchTool(InspectionProfileEntry tool) {
    myTools.put(tool.getShortName(), (InspectionTool)tool);
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey) {
    HighlightDisplayLevel level = getToolState(inspectionToolKey).getLevel();
    if (!SeverityRegistrar.isSeverityValid(level.getSeverity())){
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level);
    }
    return level;
  }

  private ToolState getToolState(HighlightDisplayKey key) {
    ToolState state = myDisplayLevelMap.get(key);
    if (state == null) {
      if (myBaseProfile != null) {
        state = myBaseProfile.getToolState(key);
        if (myLockedProfile && state != null){
          state.myEnabled = false;
        }
      }
    }
    //default level for converted profiles
    if (state == null) {
      state = new ToolState(HighlightDisplayLevel.WARNING, false);
    }
    return state;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    if (myFile == null && myTools.isEmpty()){ //can't load tools in any other way
      initInspectionTools();
    }
    myDisplayLevelMap.clear();
    final String version = element.getAttributeValue(VERSION_TAG);
    if (myFile != null && (version == null || !version.equals(VALID_VERSION))) {
      try {
        element = InspectionProfileConvertor.convertToNewFormat(myFile, this);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (JDOMException e) {
        LOG.error(e);
      }
    }

    final String locked = element.getAttributeValue(IS_LOCKED);
    if (locked != null) {
      myLockedProfile = Boolean.parseBoolean(locked);
    }

    if (myOverrideSeverities) {
      final Element highlightElement = element.getChild(USED_LEVELS);
      if (highlightElement != null) {
        SeverityRegistrar.getInstance().readExternal(highlightElement);
      }
    }

    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      Element toolElement = (Element)o;

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      final String levelName = toolElement.getAttributeValue(LEVEL_TAG);
      HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
        level = HighlightDisplayLevel.WARNING;
      }

      InspectionTool tool = myTools.get(toolClassName);
      if (tool != null) {
        tool.readSettings(toolElement);
      }

      HighlightDisplayKey key = HighlightDisplayKey.find(toolClassName);
      if (key == null) continue; //tool was somehow dropped

      final String enabled = toolElement.getAttributeValue(ENABLED_TAG);
      myDisplayLevelMap.put(key, new ToolState(level, enabled != null && Boolean.parseBoolean(enabled)));
    }

    myBaseProfile = DEFAULT_PROFILE;
  }


  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    element.setAttribute(IS_LOCKED, String.valueOf(myLockedProfile));

    Element highlightSettings = new Element(USED_LEVELS);
    SeverityRegistrar.getInstance().writeExternal(highlightSettings);
    element.addContent(highlightSettings);

    for (final HighlightDisplayKey key : myDisplayLevelMap.keySet()) {
      Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
      final String toolName = key.toString();
      inspectionElement.setAttribute(CLASS_TAG, toolName);
      inspectionElement.setAttribute(LEVEL_TAG, getToolState(key).getLevel().toString());
      inspectionElement.setAttribute(ENABLED_TAG, Boolean.toString(isToolEnabled(key)));

      final InspectionTool tool = myTools.get(toolName);
      if (tool != null) {
        tool.writeSettings(inspectionElement);
      }
      element.addContent(inspectionElement);
    }
  }

  public InspectionProfileEntry getInspectionTool(String shortName) {
    if (myTools.isEmpty()) {
      initInspectionTools();
    }
    return myTools.get(shortName);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void save() {
    if (isLocal()) {
      if (myName.compareTo("Default") == 0 && myFile == null){
        try {
          myFile = InspectionProfileManager.getInstance().createUniqueProfileFile("Default");
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      if (myFile != null) {
        try {
          if (!myFile.exists()){
            if (!myFile.createNewFile()){
              myFile = File.createTempFile("profile", ".xml", InspectionProfileManager.getInstance().getProfileDirectory());
            }
          }
          Element root = new Element(ROOT_ELEMENT_TAG);
          root.setAttribute(PROFILE_NAME_TAG, myName);
          writeExternal(root);
          myVisibleTreeState.writeExternal(root);
          JDOMUtil.writeDocument(new Document(root), myFile, CodeStyleSettingsManager.getSettings(null).getLineSeparator());
        }
        catch (WriteExternalException e) {
          LOG.error(e);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  public boolean isEditable() {
    return myEnabledTool == null;
  }

  public String getDisplayName() {
    return isEditable() ? getName() : myEnabledTool;
  }

  public void setEditable(final String displayName) {
    myEnabledTool = displayName;
  }

  private void load(boolean overrideSeverities){
    myOverrideSeverities = overrideSeverities;
    try {
      if (myFile != null) {
        Document document = JDOMUtil.loadDocument(myFile);
        final Element root = document.getRootElement();
        readExternal(root);
        myVisibleTreeState.readExternal(root);
        myInitialized = true;
      }
    }
    catch (FileNotFoundException e) {
      //ignore
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", myFile != null ? 0 : 1,  myFile != null ? myFile.getName() : ""), InspectionsBundle.message("inspection.errors.occured.dialog.title"));
        }
      }, ModalityState.NON_MODAL);
    }
    myOverrideSeverities = true;
  }

  public void load() {
    load(true);
  }

  public boolean isDefault() {
    return myDisplayLevelMap.isEmpty();
  }

  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  public void lockProfile(boolean isLocked){
    for (InspectionTool tool : myTools.values()) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (isLocked){
        myDisplayLevelMap.put(key, getToolState(key));
      } else if (!isProperSetting(key)){
        myDisplayLevelMap.remove(key);
      }
    }
    myLockedProfile = isLocked;
  }

  public InspectionProfileEntry[] getInspectionTools() {
    if (myTools.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
     initInspectionTools();
    }
    ArrayList<InspectionTool> result = new ArrayList<InspectionTool>();
    result.addAll(myTools.values());
    return result.toArray(new InspectionTool[result.size()]);
  }

  public void initInspectionTools() {
    if (myBaseProfile != null){
      myBaseProfile.initInspectionTools();
    }
    final InspectionTool[] tools = myRegistrar.createTools();
    for (InspectionTool tool : tools) {
      myTools.put(tool.getShortName(), tool);
    }
    if (mySource != null){
      copyToolsConfigurations(mySource);
    }
    load(false);
  }

  public ModifiableModel getModifiableModel() {
    return new InspectionProfileImpl(this);
  }

  public void copyFrom(InspectionProfile profile) {
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
    super.copyFrom(inspectionProfile);
    if (profile == null) return;
    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(inspectionProfile.myDisplayLevelMap);
    myBaseProfile = inspectionProfile.myBaseProfile;
    copyToolsConfigurations(inspectionProfile);
  }

  public void inheritFrom(InspectionProfile profile) {
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
    myBaseProfile = inspectionProfile;
    copyToolsConfigurations(inspectionProfile);
  }

  private void copyToolsConfigurations(InspectionProfileImpl profile) {
    try {
      if (!profile.myTools.isEmpty()) {
        final InspectionProfileEntry[] inspectionTools = getInspectionTools();
        for (InspectionProfileEntry inspectionTool : inspectionTools) {
          copyToolConfig(inspectionTool, profile);
        }
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private static void copyToolConfig(final InspectionProfileEntry inspectionTool, final InspectionProfileImpl profile)
    throws WriteExternalException, InvalidDataException {
    final String name = inspectionTool.getShortName();
    final InspectionProfileEntry tool = profile.getInspectionTool(name);
    if (tool != null){
      @NonNls String tempRoot = "config";
      Element config = new Element(tempRoot);
      tool.writeSettings(config);
      inspectionTool.readSettings(config);
    }
  }

  public void cleanup() {
    if (myTools.isEmpty()) return;
    if (!myTools.isEmpty()) {
      for (final String key : myTools.keySet()) {
        final InspectionTool tool = myTools.get(key);
        if (tool.getContext() != null) {
          tool.cleanup();
        }
      }
    }
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void enableTool(String inspectionTool){
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), true));
  }

  public void disableTool(String inspectionTool){
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), false));
  }


  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    setState(key, new ToolState(level, isToolEnabled(key)));
  }

  private void setState(HighlightDisplayKey key, ToolState state) {
    if (myBaseProfile != null &&
        state.equals(myBaseProfile.getToolState(key))) {
      if (toolSettingsAreEqual(key, this, myBaseProfile) && !myLockedProfile){ //settings may differ
        myDisplayLevelMap.remove(key);
      } else {
        myDisplayLevelMap.put(key, state);
      }
    }
    else {
      myDisplayLevelMap.put(key, state);
    }
  }

  public boolean isToolEnabled(HighlightDisplayKey key) {
    final ToolState toolState = getToolState(key);
    return toolState != null && toolState.isEnabled();    
  }

  public boolean isExecutable() {
    if (myTools.isEmpty()){
      //initialize
      initInspectionTools();
    }
    for (String name : myTools.keySet()) {
      if (isToolEnabled(HighlightDisplayKey.find(name))){
        return true;
      }
    }
    return false;
  }

  //invoke when isChanged() == true
  public void commit(final ProfileManager profileManager) {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    profileManager.updateProfile(mySource);
    mySource = null;
  }

  private void commit(InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.myName;
    myLocal = inspectionProfile.myLocal;
    myLockedProfile = inspectionProfile.myLockedProfile;
    myDisplayLevelMap = inspectionProfile.myDisplayLevelMap;
    myVisibleTreeState = inspectionProfile.myVisibleTreeState;
    myBaseProfile = inspectionProfile.myBaseProfile;
    myTools = inspectionProfile.myTools;

    save();
  }

  private static class ToolState {
    private HighlightDisplayLevel myLevel;
    private boolean myEnabled;

    public ToolState(final HighlightDisplayLevel level, final boolean enabled) {
      myLevel = level;
      myEnabled = enabled;
    }

    public ToolState(final HighlightDisplayLevel level) {
      myLevel = level;
      myEnabled = true;
    }

    public HighlightDisplayLevel getLevel() {
      return myLevel;
    }

    public void setLevel(final HighlightDisplayLevel level) {
      myLevel = level;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }

    public boolean equals(Object object) {
      if (!(object instanceof ToolState)) return false;
      final ToolState state = (ToolState)object;
      return myLevel == state.getLevel() &&
             myEnabled == state.isEnabled();
    }

    public int hashCode() {
      return myLevel.hashCode();
    }
  }

}
