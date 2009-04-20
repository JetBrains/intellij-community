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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
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
import org.jetbrains.annotations.Nullable;

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

  private Map<String, Boolean> myDisplayLevelMap;
  private Map<String, Element> myDeinstalledInspectionsSettings = new HashMap<String, Element>();
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

  final InspectionToolRegistrar myRegistrar;
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
      return toolList1.equalTo(toolList2);
    }
    return false;
  }

  public boolean isProperSetting(HighlightDisplayKey key) {
    final Map<String, Boolean> diffMap = getDisplayLevelMap();
    if (diffMap != null) {
      final Tools tools = myBaseProfile.myTools.get(key.toString());
      final Tools currentTools = myTools.get(key.toString());
      if (tools != null && currentTools != null) {
        diffMap.put(key.toString(), tools.equalTo(currentTools));
      } else {
        diffMap.put(key.toString(), tools == currentTools);
      }
      return !diffMap.get(key.toString()).booleanValue();
    }
    return false;
  }

  public void resetToBase() {
    copyToolsConfigurations(myBaseProfile, null);
  }

  public void resetToEmpty() {
    final InspectionProfileEntry[] profileEntries = getInspectionTools(null);
    for (InspectionProfileEntry entry : profileEntries) {
      disableTool(entry.getShortName());
    }
  }

  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    final Tools tools = myTools.get(inspectionToolKey.toString());
    HighlightDisplayLevel level = tools != null ? tools.getLevel(element) : HighlightDisplayLevel.WARNING;
    if (!((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().isSeverityValid(level.getSeverity().toString())) {
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level);
    }
    return level;
  }


  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myBaseProfile = getDefaultProfile();
    initInspectionTools();
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

    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      Element toolElement = (Element)o;

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      if (HighlightDisplayKey.find(toolClassName) == null) {
        myDeinstalledInspectionsSettings.put(toolClassName, toolElement);
        continue;
      }

      final Tools toolList = myTools.get(toolClassName);
      LOG.assertTrue(toolList != null);

      toolList.readExternal(toolElement, this);
    }
  }


  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    element.setAttribute(IS_LOCKED, String.valueOf(myLockedProfile));

    Map<String, Boolean> diffMap = getDisplayLevelMap();
    if (diffMap != null) {

      diffMap = new TreeMap<String, Boolean>(diffMap);
      for (String toolName : myDeinstalledInspectionsSettings.keySet()) {
        diffMap.put(toolName, false);
      }

      for (final String toolName : diffMap.keySet()) {
        if (diffMap.get(toolName).booleanValue()) continue;
        final HighlightDisplayKey key = HighlightDisplayKey.find(toolName);
        if (key != null) {
          final Tools toolList = myTools.get(toolName);
          LOG.assertTrue(toolList != null);
          final Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
          inspectionElement.setAttribute(CLASS_TAG, toolName);
          toolList.writeExternal(inspectionElement);
          element.addContent(inspectionElement);
        }
        else {
          final Element toolElement = myDeinstalledInspectionsSettings.get(toolName);
          LOG.assertTrue(toolElement != null);
          toolElement.detach();
          element.addContent(toolElement);
        }
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
    for (Boolean def : myDisplayLevelMap.values()) {
      if (!def.booleanValue()) return false;
    }
    return true;
  }

  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  public void lockProfile(boolean isLocked) {
    for (Tools toolList : myTools.values()) {
      final String key = toolList.getShortName();
      if (isLocked) {
        myDisplayLevelMap.put(key, Boolean.FALSE);
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

  public List<ScopeToolState> getAllEnabledInspectionTools() {
    initInspectionTools();
    final ArrayList<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (final Tools toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        if (toolList.getScopes() != null) {
          for (ScopeToolState state : toolList.getTools()) {
            if (state.getScope() == null || state.isEnabled()) {
              result.add(state);
            }
          }
        }
        else {
          result.add(new ScopeToolState(null, toolList.getTool(), toolList.isEnabled(), toolList.getLevel()));
        }
      }
    }
    return result;
  }

  public void disableTool(String toolId, PsiElement element) {
    myTools.get(toolId).disableTool(element);
  }

  public void enableTool(String toolId, PsiElement element) {
    myTools.get(toolId).enableTool(element);
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
          HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
          if (key == null) {
            if (tool instanceof LocalInspectionToolWrapper) {
              key = HighlightDisplayKey.register(shortName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID(),
                                           ((LocalInspectionToolWrapper)tool).getTool().getAlternativeID());
            }
            else {
              key = HighlightDisplayKey.register(shortName);
            }
          }
          myTools.put(tool.getShortName(), new Tools(tool, myBaseProfile != null ? myBaseProfile.getErrorLevel(key) : tool.getDefaultLevel(), myBaseProfile != null ? myBaseProfile.isToolEnabled(key) : tool.isEnabledByDefault()));
        }
        if (mySource != null) {
          copyToolsConfigurations(mySource, null);
        }
      }
    }
  }

  private HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key) {
    return myTools.get(key.toString()).getLevel();
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
    myDisplayLevelMap = new LinkedHashMap<String, Boolean>(inspectionProfile.myDisplayLevelMap);
    myBaseProfile = inspectionProfile.myBaseProfile;
  }

  public void copyToolsConfigurations(InspectionProfileImpl profile, NamedScope scope) {
    try {
      initInspectionTools();
      for (Tools toolList : profile.myTools.values()) {
        if (toolList.getTools() != null) {
          for (ScopeToolState state : toolList.getTools()) {
            final InspectionTool inspectionTool = copyToolSettings((InspectionTool)state.getTool());
            final Tools tools = myTools.get(inspectionTool.getShortName());
            if (state.getScope() != null) {
              tools.addTool(state.getScope(), inspectionTool, state.isEnabled(), state.getLevel());
            } else {
              tools.setTool(inspectionTool, state.isEnabled(), state.getLevel());
            }
          }
        }
        else {
          final InspectionTool inspectionTool = copyToolSettings(toolList.getTool());
          final Tools tools = myTools.get(inspectionTool.getShortName());
          if (scope != null) {
            tools.addTool(scope, inspectionTool, toolList.isEnabled(), toolList.getLevel());
          } else {
            final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool.getShortName());
            tools.setTool(inspectionTool, profile.isToolEnabled(key), profile.getErrorLevel(key));
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
    myTools.get(inspectionTool).enableTool();
  }

  public void enableTool(String inspectionTool, NamedScope namedScope) {
    myTools.get(inspectionTool).enableTool(namedScope);
  }

  public void disableTool(String inspectionTool, NamedScope namedScope) {
    myTools.get(inspectionTool).disableTool(namedScope);
  }


  public void disableTool(String inspectionTool) {
    myTools.get(inspectionTool).disableTool();
  }

  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    myTools.get(key.toString()).setLevel(level);
  }

  public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
    final Tools toolState = myTools.get(key.toString());
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
      final Tools toolsSettings = myTools.get(tool.getShortName());
      addScope(tool, allScope, toolsSettings.getLevel(), toolsSettings.isEnabled());
    }
  }

  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }

  public List<ScopeToolState> getAllTools() {
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (Tools tools : myTools.values()) {
      result.addAll(tools.getTools());
    }
    return result;
  }

  public List<ScopeToolState> getAllTools(String shortName) {
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    result.addAll(myTools.get(shortName).getTools());
    return result;
  }

  public boolean isToolEnabled(HighlightDisplayKey key, NamedScope namedScope) {
    return myTools.get(key.toString()).isEnabled(namedScope);
  }

  public List<NamedScope> getScopes(String toolId) {
    return myTools.get(toolId).getScopes();
  }

  public void removeScope(String toolId, NamedScope scope) {
    myTools.get(toolId).removeScope(scope);
  }

  public void setScope(String toolId, int idx, NamedScope namedScope) {
    myTools.get(toolId).setScope(idx, namedScope);
  }

  public void moveScope(String toolId, int idx, int dir) {
    myTools.get(toolId).moveScope(idx, dir);
  }

  @Nullable
  private Map<String, Boolean> getDisplayLevelMap() {
    if (myBaseProfile == null) return null;
    if (myDisplayLevelMap == null) {
      myDisplayLevelMap = new TreeMap<String, Boolean>();
      for (String toolId : myTools.keySet()) {
        myDisplayLevelMap.put(toolId, toolSettingsAreEqual(toolId, myBaseProfile, this));
      }
    }
    return myDisplayLevelMap;
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key, NamedScope scope) {
    final Tools tools = myTools.get(key.toString());
    return tools != null ? tools.getLevel(scope) : HighlightDisplayLevel.WARNING;
  }

  public void addScope(InspectionProfileEntry tool, NamedScope scope, HighlightDisplayLevel level, boolean enabled) {
     myTools.get(tool.getShortName()).addTool(scope, (InspectionTool)tool, enabled, level);
  }
}
