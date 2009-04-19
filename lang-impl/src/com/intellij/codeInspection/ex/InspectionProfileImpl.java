package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class InspectionProfileImpl extends ProfileEx implements ModifiableModel, InspectionProfile, ExternalizableScheme {
  @NonNls private static InspectionProfileImpl DEFAULT_PROFILE;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  @NonNls private static final String VALID_VERSION = "1.0";

  private Map<String, Tools> myTools = new HashMap<String, Tools>();

  //diff map with base profile
  private LinkedHashMap<String, ToolState> myDisplayLevelMap = new LinkedHashMap<String, ToolState>();
  private boolean myLockedProfile = false;

  protected InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String INSPECTION_TOOL_TAG = "inspection_tool";
  @NonNls static final String ENABLED_TAG = "enabled";
  @NonNls static final String LEVEL_TAG = "level";
  @NonNls private static final String CLASS_TAG = "class";
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String ROOT_ELEMENT_TAG = "inspections";
  @NonNls static final String SCOPE = "scope";

  private String myEnabledTool = null;
  @NonNls private static final String USED_LEVELS = "used_levels";

  private final InspectionToolRegistrar myRegistrar;
  @NonNls private static final String IS_LOCKED = "is_locked";
  private final ExternalInfo myExternalInfo = new ExternalInfo();
  static final String NAME = "name";
  public static boolean INIT_INSPECTIONS = false;

//private String myBaseProfileName;

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  private boolean myModified = false;
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  private VisibleTreeState myVisibleTreeState = new VisibleTreeState();

  public InspectionProfileImpl(InspectionProfileImpl inspectionProfile) {
    super(inspectionProfile.getName());

    myRegistrar = inspectionProfile.myRegistrar;
    myTools = new HashMap<String, Tools>();
    initInspectionTools();

    myDisplayLevelMap = new LinkedHashMap<String, ToolState>(inspectionProfile.myDisplayLevelMap);
    myVisibleTreeState = new VisibleTreeState(inspectionProfile.myVisibleTreeState);

    myBaseProfile = inspectionProfile.myBaseProfile;
    myLocal = inspectionProfile.myLocal;
    myLockedProfile = inspectionProfile.myLockedProfile;
    mySource = inspectionProfile;
    setProfileManager(inspectionProfile.getProfileManager());
    copyFrom(inspectionProfile);
  }

  public InspectionProfileImpl(final String inspectionProfile,
                               final InspectionToolRegistrar registrar,
                               final ProfileManager profileManager) {
    super(inspectionProfile);
    myRegistrar = registrar;
    myBaseProfile = getDefaultProfile();
    setProfileManager(profileManager);
  }

  public InspectionProfileImpl(@NonNls String name) {
    super(name);
    myRegistrar = InspectionToolRegistrar.getInstance();
    setProfileManager(InspectionProfileManager.getInstance());
  }

  public InspectionProfile getParentProfile() {
    return mySource;
  }

  @Override
  public void setProfileManager(@NotNull ProfileManager profileManager) {
    super.setProfileManager(profileManager);
    final NamedScopesHolder scopesHolder = profileManager.getScopesManager();
    if (scopesHolder != null) {
      scopesHolder.addScopeListener(new NamedScopesHolder.ScopeListener() {
        public void scopesChanged() { //todo scopes change tracking

        }
      });
    }
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

  private static boolean toolSettingsAreEqual(String toolName, InspectionProfileImpl profile1, InspectionProfileImpl profile2) {
    final Tools toolList1 = profile1.myTools.get(toolName);
    final Tools toolList2 = profile2.myTools.get(toolName);

    if (toolList1 == null && toolList2 == null) {
      return true;
    }
    if (toolList1 != null && toolList2 != null) {
      if (toolList1.getTools() == null && toolList2.getTools() != null) return false;
      if (toolList2.getTools() == null && toolList1.getTools() != null) return false;
      if (toolList1.getTools() == null && toolList2.getTools() == null) {
        return toolSettingsAreEqual(toolList1.getTool(), toolList2.getTool());
      }
      if (toolList1.getTools().size() != toolList2.getTools().size()) return false;
      for (int i = 0; i < toolList1.getTools().size(); i++) {
        if (!toolSettingsAreEqual(toolList1.getTools().get(i).second, toolList2.getTools().get(i).second)) return false;
      }
      return true;
    }
    return false;
  }

  private static boolean toolSettingsAreEqual(InspectionTool tool1, InspectionTool tool2) {
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
    return false;
  }

  public boolean isProperSetting(HighlightDisplayKey key) {
    if (myBaseProfile == null) {
      return false;
    }
    final String toolName = key.toString();
    final boolean toolsSettings = toolSettingsAreEqual(toolName, this, myBaseProfile);
    if (myDisplayLevelMap.keySet().contains(toolName)) {
      if (toolsSettings && myDisplayLevelMap.get(toolName).equals(myBaseProfile.getToolState(toolName))) {
        if (!myLockedProfile) {
          myDisplayLevelMap.remove(toolName);
        }
        return false;
      }
      return true;
    }


    if (!toolsSettings) {
      myDisplayLevelMap.put(key.toString(), myBaseProfile.getToolState(toolName));
      return true;
    }

    return false;
  }


  public void resetToBase() {
    myDisplayLevelMap.clear();
    copyToolsConfigurations(myBaseProfile, null);
    if (myLockedProfile && myBaseProfile != null) { //store whole state for locked profiles
      for (InspectionProfileEntry entry : myBaseProfile.getInspectionTools(null)) {
        final String shortName = entry.getShortName();
        myDisplayLevelMap.put(shortName, myBaseProfile.getToolState(shortName));
      }
    }
  }

  public void resetToEmpty() {
    final InspectionProfileEntry[] profileEntries = getInspectionTools(null);
    for (InspectionProfileEntry entry : profileEntries) {
      disableTool(entry.getShortName());
    }
  }

  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    HighlightDisplayLevel level = getToolState(inspectionToolKey.toString()).getLevel();
    if (!((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().isSeverityValid(level.getSeverity().toString())) {
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level);
    }
    return level;
  }

  private ToolState getToolState(String key) {
    ToolState state = myDisplayLevelMap.get(key);
    if (state == null) {
      if (myBaseProfile != null) {
        state = myBaseProfile.getToolState(key);
        if (myLockedProfile && state != null) {
          state.setEnabled(false);
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
    initInspectionTools();
    myDisplayLevelMap.clear();
    final String version = element.getAttributeValue(VERSION_TAG);
    if (version == null || !version.equals(VALID_VERSION)) {
      try {
        element = InspectionProfileConvertor.convertToNewFormat(element, this);
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

    final Element highlightElement = element.getChild(USED_LEVELS);
    if (highlightElement != null) { //from old profiles
      ((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().readExternal(highlightElement);
    }

    myBaseProfile = getDefaultProfile();

    final NamedScopesHolder scopesHolder = getProfileManager().getScopesManager();
    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      Element toolElement = (Element)o;

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      final String levelName = toolElement.getAttributeValue(LEVEL_TAG);
      HighlightDisplayLevel level =
        HighlightDisplayLevel.find(((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().getSeverity(levelName));
      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
        level = HighlightDisplayLevel.WARNING;
      }
      final String enabled = toolElement.getAttributeValue(ENABLED_TAG);
      HighlightDisplayKey key = HighlightDisplayKey.find(toolClassName);
      final ToolState toolState = new ToolState(level, enabled != null && Boolean.parseBoolean(enabled));
      myDisplayLevelMap.put(toolClassName, toolState);

      if (key == null) {
        toolState.setToolElement(toolElement);
        continue; //tool was somehow dropped
      }

      Tools toolList = myTools.get(toolClassName);
      LOG.assertTrue(toolList != null);
      final InspectionTool tool = toolList.getInspectionTool((NamedScope)null);
      if (scopesHolder != null) {
        final List children = toolElement.getChildren(SCOPE);
        if (!children.isEmpty()) {
          for (Object sO : children) {
            final Element scopeElement = (Element)sO;
            final String scopeName = scopeElement.getAttributeValue(NAME);
            if (scopeName != null) {
              final NamedScope namedScope = scopesHolder.getScope(scopeName);
              if (namedScope != null) {
                final String errorLevel = scopeElement.getAttributeValue(LEVEL_TAG);
                final String enabledInScope = scopeElement.getAttributeValue(ENABLED_TAG);
                toolState.addScope(namedScope, errorLevel != null ? HighlightDisplayLevel
                  .find(((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().getSeverity(errorLevel)) : level,
                                   enabledInScope != null && Boolean.parseBoolean(enabledInScope));

                final InspectionTool inspectionTool = myRegistrar.createInspectionTool(toolClassName, tool);
                inspectionTool.readSettings(scopeElement);
                toolList.addTool(namedScope, inspectionTool);
              }
            }
          }
        }
        else {
          tool.readSettings(toolElement);
        }
      }
      else {
        tool.readSettings(toolElement);
      }
    }
  }


  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    element.setAttribute(IS_LOCKED, String.valueOf(myLockedProfile));

    for (final String toolName : myDisplayLevelMap.keySet()) {
      final ToolState state = getToolState(toolName);
      final HighlightDisplayKey key = HighlightDisplayKey.find(toolName);
      if (key != null) {
        final Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
        inspectionElement.setAttribute(CLASS_TAG, toolName);
        final Map<String, Element> scopesElements = state.writeExternal(inspectionElement);

        final Tools toolList = myTools.get(toolName);
        LOG.assertTrue(toolList != null);
        toolList.writeExternal(inspectionElement, scopesElements);

        element.addContent(inspectionElement);
      }
      else {
        final Element toolElement = state.getToolElement();
        LOG.assertTrue(toolElement != null);
        toolElement.detach();
        element.addContent(toolElement);
      }
    }
  }

  public InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
    initInspectionTools();
    final Tools toolList = myTools.get(shortName);
    return toolList != null ? toolList.getInspectionTool(element) : null;
  }

  public InspectionProfileEntry getToolById(String id, PsiElement element) {
    initInspectionTools();
    for (Tools toolList : myTools.values()) {
      final InspectionTool tool = toolList.getInspectionTool(element);
      String toolId =
        tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getTool().getID() : tool.getShortName();
      if (id.equals(toolId)) return tool;
    }
    return null;
  }

  public void save() throws IOException {
    /*
    if (isLocal()) {
      if (myName.compareTo("Default") == 0 && myElement == null){
        myElement = new Element(ROOT_ELEMENT_TAG);
      }
      if (myElement != null) {
        try {
          myElement = new Element(ROOT_ELEMENT_TAG);
          myElement.setAttribute(PROFILE_NAME_TAG, myName);
          writeExternal(myElement);
          myVisibleTreeState.writeExternal(myElement);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
        }
      }
    }
    */
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  public boolean isEditable() {
    return myEnabledTool == null;
  }

  @NotNull
  public String getDisplayName() {
    return isEditable() ? getName() : myEnabledTool;
  }

  public void setEditable(final String displayName) {
    myEnabledTool = displayName;
  }

  public void load(Element element) {
    try {
      readExternal(element);
      myVisibleTreeState.readExternal(element);
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, getName()),
                                   InspectionsBundle.message("inspection.errors.occured.dialog.title"));
        }
      }, ModalityState.NON_MODAL);
    }
  }

  public boolean isDefault() {
    return myDisplayLevelMap.isEmpty();
  }

  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  public void lockProfile(boolean isLocked) {
    for (Tools toolList : myTools.values()) {
      final String key = toolList.getShortName();
      if (isLocked) {
        myDisplayLevelMap.put(key, getToolState(key));
      }
      else if (!isProperSetting(HighlightDisplayKey.find(key))) {
        myDisplayLevelMap.remove(key);
      }
    }
    myLockedProfile = isLocked;
  }

  @NotNull
  public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
    initInspectionTools();
    ArrayList<InspectionTool> result = new ArrayList<InspectionTool>();
    for (Tools toolList : myTools.values()) {
      result.add(toolList.getInspectionTool(element));
    }
    return result.toArray(new InspectionTool[result.size()]);
  }

  public List<Pair<InspectionProfileEntry, NamedScope>> getAllEnabledInspectionTools() {
    initInspectionTools();
    final ArrayList<Pair<InspectionProfileEntry, NamedScope>> result = new ArrayList<Pair<InspectionProfileEntry, NamedScope>>();
    for (final Tools toolList : myTools.values()) {
      final ToolState state = getToolState(toolList.getShortName());
      if (state.isEnabled()) {
        if (state.getScopes() != null) {
          final List<NamedScope> scopes = state.getEnabledScopes();
          for (Pair<NamedScope, InspectionTool> tool : toolList.getTools()) {
            if (tool.first == null || scopes.contains(tool.first)) {
              result.add(new Pair<InspectionProfileEntry, NamedScope>(tool.second, tool.first));
            }
          }
        }
        else {
          result.add(new Pair<InspectionProfileEntry, NamedScope>(toolList.getTool(), null));
        }
      }
    }
    return result;
  }

  public void disableTool(String toolId, PsiElement element) {
    getToolState(toolId).disableTool(element);
  }

  public void enableTool(String toolId, PsiElement element) {
    getToolState(toolId).enableTool(element);
  }

  public boolean wasInitialized() {
    return myInitialized.get();
  }

  public void initInspectionTools() {
    if (!ApplicationManager.getApplication().isUnitTestMode() || INIT_INSPECTIONS) {
      if (!myInitialized.getAndSet(true)) {
        if (myBaseProfile != null) {
          myBaseProfile.initInspectionTools();
        }
        final InspectionTool[] tools = myRegistrar.createTools();
        for (InspectionTool tool : tools) {
          final String shortName = tool.getShortName();
          if (HighlightDisplayKey.find(shortName) == null) {
            if (tool instanceof LocalInspectionToolWrapper) {
              HighlightDisplayKey.register(shortName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID(),
                                           ((LocalInspectionToolWrapper)tool).getTool().getAlternativeID());
            }
            else {
              HighlightDisplayKey.register(shortName);
            }
          }
          myTools.put(tool.getShortName(), new Tools(tool));
        }
        if (mySource != null) {
          copyToolsConfigurations(mySource, null);
        }
      }
    }
  }

  @NotNull
  public ModifiableModel getModifiableModel() {
    InspectionProfileImpl modifiableModel = new InspectionProfileImpl(this);
    modifiableModel.myExternalInfo.copy(myExternalInfo);
    return modifiableModel;
  }

  public void copyFrom(InspectionProfile profile) {
    super.copyFrom(profile);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
    myDisplayLevelMap = new LinkedHashMap<String, ToolState>(inspectionProfile.myDisplayLevelMap);
    myBaseProfile = inspectionProfile.myBaseProfile;
    //copyToolsConfigurations(inspectionProfile, null);
  }

  public void copyToolsConfigurations(InspectionProfileImpl profile, NamedScope scope) {
    try {
      initInspectionTools();
      for (Tools toolList : profile.myTools.values()) {
        final InspectionProfileEntry profileEntry = profile.myTools.get(toolList.getShortName()).getInspectionTool(scope);
        if (toolList.getTools() != null) {
          for (Pair<NamedScope, InspectionTool> pair : toolList.getTools()) {
            final InspectionTool inspectionTool = copyToolSettings(pair.second);
            final Tools tools = myTools.get(inspectionTool.getShortName());
            if (pair.first != null) {
              tools.addTool(pair.first, inspectionTool);
            } else {
              tools.setTool(inspectionTool);
            }
          }
        }
        else {
          final InspectionTool inspectionTool = copyToolSettings(toolList.getTool());
          final Tools tools = myTools.get(inspectionTool.getShortName());
          if (scope != null) {
            tools.addTool(scope, inspectionTool);
          } else {
            tools.setTool(inspectionTool);
          }
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

  

  private InspectionTool copyToolSettings(InspectionTool tool)
    throws WriteExternalException, InvalidDataException {
    @NonNls String tempRoot = "config";
    Element config = new Element(tempRoot);
    tool.writeSettings(config);
    final InspectionTool inspectionTool = myRegistrar.createInspectionTool(tool.getShortName(), tool);
    inspectionTool.readSettings(config);
    return inspectionTool;
  }

  public void cleanup() {
    for (Map.Entry<String, Tools> entry : myTools.entrySet()) {
      final Tools toolList = entry.getValue();
      for (final InspectionTool tool : toolList.getAllTools()) {
        if (tool.getContext() != null) {
          tool.cleanup();
        }
      }
    }
  }

  public void enableTool(String inspectionTool) {
    setState(inspectionTool, new ToolState(getErrorLevel(HighlightDisplayKey.find(inspectionTool), (NamedScope)null), true));
  }

  public void enableTool(String inspectionTool, NamedScope namedScope) {
    getToolState(inspectionTool).enableTool(namedScope);
  }

  public void disableTool(String inspectionTool, NamedScope namedScope) {
    getToolState(inspectionTool).disableTool(namedScope);
  }


  public void disableTool(String inspectionTool) {
    setState(inspectionTool, new ToolState(getErrorLevel(HighlightDisplayKey.find(inspectionTool), (NamedScope)null), false));
  }

  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    setState(key.toString(), new ToolState(level, isToolEnabled(key)));
  }

  private void setState(String key, ToolState state) {
    if (myBaseProfile != null && state.equals(myBaseProfile.getToolState(key))) {
      if (toolSettingsAreEqual(key, this, myBaseProfile) && !myLockedProfile) { //settings may differ
        myDisplayLevelMap.remove(key);
      }
      else {
        myDisplayLevelMap.put(key, state);
      }
    }
    else {
      myDisplayLevelMap.put(key, state);
    }
  }

  public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
    final ToolState toolState = getToolState(key.toString());
    return toolState != null && toolState.isEnabled(element);
  }

  public boolean isToolEnabled(HighlightDisplayKey key) {
    return isToolEnabled(key, (PsiElement)null);
  }

  public boolean isExecutable() {
    initInspectionTools();
    for (String name : myTools.keySet()) {
      if (isToolEnabled(HighlightDisplayKey.find(name))) {
        return true;
      }
    }
    return false;
  }

  //invoke when isChanged() == true
  public void commit() throws IOException {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    getProfileManager().updateProfile(mySource);
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

    myExternalInfo.copy(inspectionProfile.getExternalInfo());

    InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
  }

  public static synchronized InspectionProfileImpl getDefaultProfile() {
    if (DEFAULT_PROFILE == null) {
      DEFAULT_PROFILE = new InspectionProfileImpl("Default");
      final InspectionProfileEntry[] inspectionTools = DEFAULT_PROFILE.getInspectionTools(null);
      for (InspectionProfileEntry tool : inspectionTools) {
        final String shortName = tool.getShortName();
        /*
        final String shortName = tool.getShortName();
        if (tool instanceof LocalInspectionToolWrapper) {
          HighlightDisplayKey.register(shortName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
        } else {
          HighlightDisplayKey.register(shortName);
        } */
        DEFAULT_PROFILE.myDisplayLevelMap.put(shortName, new ToolState(tool.getDefaultLevel(), tool.isEnabledByDefault()));
      }
    }
    return DEFAULT_PROFILE;
  }

  public Document saveToDocument() throws WriteExternalException {
    if (isLocal()) {
      Element root = new Element(ROOT_ELEMENT_TAG);
      root.setAttribute(PROFILE_NAME_TAG, myName);
      writeExternal(root);
      //myVisibleTreeState.writeExternal(root);
      return new Document(root);
    }
    else {
      return null;
    }

  }

  public VisibleTreeState getVisibleTreeState() {
    return myVisibleTreeState;
  }

  public void setVisibleTreeState(final VisibleTreeState state) {
    myVisibleTreeState = state;
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key, NamedScope namedScope) {
    final ToolState state = getToolState(key.toString());
    LOG.assertTrue(state != null);
    return state.getErrorLevel(namedScope);
  }


  public void addScope(InspectionProfileEntry entry, NamedScope scope, HighlightDisplayLevel errorLevel, boolean toolEnabled) {
    ToolState state = myDisplayLevelMap.get(entry.getShortName());
    if (state == null) {

      if (Comparing.equal(entry.getDefaultLevel(), errorLevel) && entry.isEnabledByDefault() == toolEnabled) return;

      state = new ToolState(errorLevel, toolEnabled);
      setState(entry.getShortName(), state);
    }
    state.addScope(scope, errorLevel, toolEnabled);
  }


  public void convert(Element element, Project project) {
    final List<InspectionTool> tools = new ArrayList<InspectionTool>();

    for (Tools toolList : myTools.values()) { //remember current tools settings
      tools.add(toolList.getTool());
    }

    final Element scopes = element.getChild(DefaultProjectProfileManager.SCOPES);
    if (scopes != null) {
      final List children = scopes.getChildren(SCOPE);
      if (children != null) {
        for (Object s : children) {
          Element scopeElement = (Element)s;
          final String profile = scopeElement.getAttributeValue(DefaultProjectProfileManager.PROFILE);
          if (profile != null) {
            final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)getProfileManager().getProfile(profile);
            if (inspectionProfile != null) {
              final NamedScope scope = getProfileManager().getScopesManager().getScope(scopeElement.getAttributeValue(NAME));
              if (scope != null) {
                copyToolsConfigurations(inspectionProfile, scope);
                for (InspectionProfileEntry entry : inspectionProfile.getInspectionTools(null)) {
                  final HighlightDisplayKey key = HighlightDisplayKey.find(entry.getShortName());
                  addScope(entry, scope, inspectionProfile.getErrorLevel(key, (NamedScope)null), inspectionProfile.isToolEnabled(key));
                }
              }
            }
          }
        }
      }
    }

    for (InspectionTool tool : tools) {
      final NamedScope allScope = DefaultScopesProvider.getInstance(project).getAllScope();
      myTools.get(tool.getShortName()).addTool(allScope, tool);
      final ToolState state = myDisplayLevelMap.get(tool.getShortName());
      addScope(tool, allScope, state != null ? state.getLevel() : tool.getDefaultLevel(),
               state != null ? state.isEnabled() : tool.isEnabledByDefault());
    }
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }

  public List<Pair<NamedScope, InspectionTool>> getAllTools() {
    final List<Pair< NamedScope, InspectionTool>> result = new ArrayList<Pair<NamedScope, InspectionTool>>();
    for (Tools tools : myTools.values()) {
      result.addAll(tools.getTools());
    }
    return result;
  }

  public List<Pair<NamedScope, InspectionTool>> getAllTools(String shortName) {
    final List<Pair< NamedScope, InspectionTool>> result = new ArrayList<Pair<NamedScope, InspectionTool>>();
    result.addAll(myTools.get(shortName).getTools());
    return result;
  }

  public boolean isToolEnabled(HighlightDisplayKey key, NamedScope namedScope) {
    return getToolState(key.toString()).isEnabled(namedScope);
  }
}
