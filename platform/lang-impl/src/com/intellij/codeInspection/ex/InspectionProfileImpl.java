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

package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class InspectionProfileImpl extends ProfileEx implements ModifiableModel, InspectionProfile, ExternalizableScheme {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  @NonNls private static final String VALID_VERSION = "1.0";
  private static final Function<InspectionProfileEntry,InspectionToolWrapper> WRAPPER_FUNCTION =
    new Function<InspectionProfileEntry, InspectionToolWrapper>() {
      @Override
      public InspectionToolWrapper fun(InspectionProfileEntry tool) {
        return InspectionToolRegistrar.wrapTool(tool);
      }
    };

  private Map<String, ToolsImpl> myTools = new THashMap<String, ToolsImpl>();

  private Map<String, Boolean> myDisplayLevelMap;
  private Map<String, Element> myDeinstalledInspectionsSettings = new TreeMap<String, Element>();
  private boolean myLockedProfile = false;

  protected InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String INSPECTION_TOOL_TAG = "inspection_tool";

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

  @Override
  public void setModified(final boolean modified) {
    myModified = modified;
  }

  private boolean myModified = false;
  private volatile boolean myInitialized;

  InspectionProfileImpl(@NotNull InspectionProfileImpl inspectionProfile) {
    super(inspectionProfile.getName());

    myRegistrar = inspectionProfile.myRegistrar;
    myTools = new HashMap<String, ToolsImpl>();
    myDeinstalledInspectionsSettings = new LinkedHashMap<String, Element>(inspectionProfile.myDeinstalledInspectionsSettings);

    myBaseProfile = inspectionProfile.myBaseProfile;
    myLocal = inspectionProfile.myLocal;
    myLockedProfile = inspectionProfile.myLockedProfile;
    mySource = inspectionProfile;
    setProfileManager(inspectionProfile.getProfileManager());
    copyFrom(inspectionProfile);
  }

  public InspectionProfileImpl(final String profileName,
                               final InspectionToolRegistrar registrar,
                               final ProfileManager profileManager) {
    super(profileName);
    myRegistrar = registrar;
    myBaseProfile = getDefaultProfile();
    setProfileManager(profileManager);
  }

  public InspectionProfileImpl(@NonNls String profileName) {
    super(profileName);
    myRegistrar = InspectionToolRegistrar.getInstance();
    setProfileManager(InspectionProfileManager.getInstance());
  }

  public static InspectionProfileImpl createSimple(String name, final InspectionProfileEntry... tools) {
    InspectionProfileImpl profile = new InspectionProfileImpl(name, new InspectionToolRegistrar(null) {
      @Override
      public List<InspectionToolWrapper> createTools() {
        return ContainerUtil.map(tools, WRAPPER_FUNCTION);
      }
    }, InspectionProfileManager.getInstance());
    boolean init = INIT_INSPECTIONS;
    try {
      INIT_INSPECTIONS = true;
      profile.initialize(null);
    }
    finally {
      INIT_INSPECTIONS = init;
    }
    for (InspectionProfileEntry tool : tools) {
      profile.enableTool(tool.getShortName());
    }
    return profile;
  }

  @Override
  public InspectionProfile getParentProfile() {
    return mySource;
  }

  @Override
  public String getBaseProfileName() {
    if (myBaseProfile == null) return null;
    return myBaseProfile.getName();
  }

  @Override
  public void setBaseProfile(InspectionProfile profile) {
    myBaseProfile = (InspectionProfileImpl)profile;
  }

  @Override
  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isChanged() {
    if (mySource != null && mySource.myLockedProfile != myLockedProfile) return true;
    return myModified;
  }

  public VisibleTreeState getExpandedNodes() {
    if (myProfileManager instanceof ApplicationProfileManager) {
      return AppInspectionProfilesVisibleTreeState.getInstance().getVisibleTreeState(this);
    }
    else {
      DefaultProjectProfileManager projectProfileManager = (DefaultProjectProfileManager)myProfileManager;
      return ProjectInspectionProfilesVisibleTreeState.getInstance(projectProfileManager.getProject()).getVisibleTreeState(this);
    }
  }

  private static boolean toolSettingsAreEqual(String toolName, InspectionProfileImpl profile1, InspectionProfileImpl profile2) {
    final ToolsImpl toolList1 = profile1.myTools.get(toolName);
    final ToolsImpl toolList2 = profile2.myTools.get(toolName);

    return Comparing.equal(toolList1, toolList2);
  }

  @Override
  public boolean isProperSetting(HighlightDisplayKey key) {
    return isProperSetting(key.toString());
  }

  @Override
  public boolean isProperSetting(String toolId) {
    if (myBaseProfile != null) {
      final ToolsImpl tools = myBaseProfile.getTools(toolId);
      final ToolsImpl currentTools = myTools.get(toolId);
      return !Comparing.equal(tools, currentTools);
    }
    return false;
  }

  @Override
  public void resetToBase() {
    initInspectionTools(null);
    
    copyToolsConfigurations(myBaseProfile, null);
    myDisplayLevelMap = null;
  }

  @Override
  public void resetToEmpty() {
    final InspectionProfileEntry[] profileEntries = getInspectionTools(null);
    for (InspectionProfileEntry entry : profileEntries) {
      disableTool(entry.getShortName());
    }
  }

  @Override
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    final ToolsImpl tools = getTools(inspectionToolKey.toString());
    HighlightDisplayLevel level = tools != null ? tools.getLevel(element) : HighlightDisplayLevel.WARNING;
    if (!((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().isSeverityValid(level.getSeverity().toString())) {
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level);
    }
    return level;
  }


  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    final String locked = element.getAttributeValue(IS_LOCKED);
    if (locked != null) {
      myLockedProfile = Boolean.parseBoolean(locked);
    }
    myBaseProfile = getDefaultProfile();
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

    final Element highlightElement = element.getChild(USED_LEVELS);
    if (highlightElement != null) { //from old profiles
      ((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().readExternal(highlightElement);
    }

    StringInterner interner = new StringInterner();
    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      Element toolElement = (Element)o;
      JDOMUtil.internElement(toolElement, interner);

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      myDeinstalledInspectionsSettings.put(toolClassName, toolElement);
    }
  }

  public Set<HighlightSeverity> getUsedSeverities() {
    LOG.assertTrue(myInitialized);
    final Set<HighlightSeverity> result = new HashSet<HighlightSeverity>();
    for (ToolsImpl tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        result.add(state.getLevel().getSeverity());
      }
    }
    return result;
  }


  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    element.setAttribute(IS_LOCKED, String.valueOf(myLockedProfile));

    synchronized (myExternalInfo) {
    if (!myInitialized) {
        for (Element el : myDeinstalledInspectionsSettings.values()) {
          element.addContent((Element)el.clone());
        }
      return;
    }
    }

    Map<String, Boolean> diffMap = getDisplayLevelMap();
    if (diffMap != null) {

      diffMap = new TreeMap<String, Boolean>(diffMap);
      for (String toolName : myDeinstalledInspectionsSettings.keySet()) {
        diffMap.put(toolName, false);
      }

      for (final String toolName : diffMap.keySet()) {
        if (!myLockedProfile && diffMap.get(toolName).booleanValue()) continue;
        final Element toolElement = myDeinstalledInspectionsSettings.get(toolName);
        if (toolElement == null) {
          final ToolsImpl toolList = myTools.get(toolName);
          LOG.assertTrue(toolList != null);
          final Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
          inspectionElement.setAttribute(CLASS_TAG, toolName);
          toolList.writeExternal(inspectionElement);
          element.addContent(inspectionElement);
        }
        else {
          element.addContent((Element)toolElement.clone());
        }
      }
    }
  }

  @Override
  @Nullable
  public InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
    final Tools toolList = getTools(shortName);
    return toolList != null ? toolList.getInspectionTool(element) : null;
  }

  @Nullable
  @Override
  public InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element) {
    InspectionProfileEntry tool = getInspectionTool(shortName, element);
    return tool instanceof InspectionToolWrapper ? ((InspectionToolWrapper)tool).getTool() : tool;
  }

  @Override
  @Nullable
  public InspectionProfileEntry getInspectionTool(@NotNull String shortName) {
    final ToolsImpl tools = getTools(shortName);
    return tools != null? tools.getTool() : null;
  }

  public InspectionProfileEntry getToolById(String id, @NotNull PsiElement element) {
    initInspectionTools(element.getProject());
    for (Tools toolList : myTools.values()) {
      final InspectionProfileEntry tool = toolList.getInspectionTool(element);
      String toolId =
        tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getID() : tool.getShortName();
      if (id.equals(toolId)) return tool;
    }
    return null;
  }

  @Override
  public void save() throws IOException {
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  @Override
  public boolean isEditable() {
    return myEnabledTool == null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return isEditable() ? getName() : myEnabledTool;
  }

  @Override
  public void setEditable(final String displayName) {
    myEnabledTool = displayName;
  }

  public void load(Element element) {
    try {
      readExternal(element);
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", 0, getName()),
                                   InspectionsBundle.message("inspection.errors.occurred.dialog.title"));
        }
      }, ModalityState.NON_MODAL);
    }
  }

  @Override
  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  @Override
  public void lockProfile(boolean isLocked) {
    myLockedProfile = isLocked;
  }

  @Override
  @NotNull
  public InspectionProfileEntry[] getInspectionTools(@Nullable PsiElement element) {
    initInspectionTools(element != null ? element.getProject() : null);
    List<InspectionTool> result = new ArrayList<InspectionTool>();
    for (Tools toolList : myTools.values()) {
      result.add((InspectionTool)toolList.getInspectionTool(element));
    }
    return result.toArray(new InspectionTool[result.size()]);
  }

  public List<ToolsImpl> getAllEnabledInspectionTools(Project project) {
    initInspectionTools(project);
    final ArrayList<ToolsImpl> result = new ArrayList<ToolsImpl>();
    for (final ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        result.add(toolList);
      }
    }
    return result;
  }

  @Override
  public void disableTool(String toolId, PsiElement element) {
    getTools(toolId).disableTool(element);
  }

  public void disableToolByDefault(String toolId) {
    getToolDefaultState(toolId).setEnabled(false);
  }

  public ScopeToolState getToolDefaultState(String toolId) {
    return getTools(toolId).getDefaultState();
  }

  public void enableToolByDefault(String toolId) {
    getToolDefaultState(toolId).setEnabled(true);
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void initInspectionTools(@Nullable Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !INIT_INSPECTIONS) return;
    if (myInitialized) return;
    synchronized (myExternalInfo) {
      if (myInitialized) return;
      myInitialized = initialize(project);
    }
  }

  private boolean initialize(@Nullable Project project) {
    if (myBaseProfile != null) {
      myBaseProfile.initInspectionTools(project);
    }

    final List<InspectionToolWrapper> tools;
    try {
      tools = createTools();
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    for (InspectionToolWrapper tool : tools) {
      final String shortName = tool.getShortName();
      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key == null) {
        final InspectionEP extension = tool.getExtension();
        Computable<String> computable = extension == null ? new Computable.PredefinedValueComputable<String>(tool.getDisplayName()) : new Computable<String>() {
          @Override
          public String compute() {
            return extension.getDisplayName();
          }
        };
        if (tool instanceof LocalInspectionToolWrapper) {
          key = HighlightDisplayKey.register(shortName, computable, ((LocalInspectionToolWrapper)tool).getID(),
                                             ((LocalInspectionToolWrapper)tool).getAlternativeID());
        }
        else {
          key = HighlightDisplayKey.register(shortName, computable);
        }
      }

      LOG.assertTrue(key != null, shortName + " ; number of initialized tools: " + myTools.size());
      final ToolsImpl toolsList =
        new ToolsImpl(tool, myBaseProfile != null ? myBaseProfile.getErrorLevel(key) : tool.getDefaultLevel(),
                      !myLockedProfile && (myBaseProfile != null ? myBaseProfile.isToolEnabled(key) : tool.isEnabledByDefault()));
      final Element element = myDeinstalledInspectionsSettings.remove(tool.getShortName());
      if (element != null) {
        try {
          toolsList.readExternal(element, this);
        }
        catch (InvalidDataException e) {
          LOG.error(e);
        }
      }
      myTools.put(tool.getShortName(), toolsList);
    }
    if (mySource != null) {
      copyToolsConfigurations(mySource, project);
    }
    return true;
  }

  private List<InspectionToolWrapper> createTools() {
    if (mySource != null) {
      return ContainerUtil.map(mySource.getAllTools(), new Function<ScopeToolState, InspectionToolWrapper>() {
        @Override
        public InspectionToolWrapper fun(ScopeToolState state) {
          return (InspectionToolWrapper)state.getTool();
        }
      });
    }
    return myRegistrar.createTools();
  }

  private HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key) {
    final ToolsImpl tools = getTools(key.toString());
    LOG.assertTrue(tools != null, "profile name: " + myName +  " base profile: " + (myBaseProfile != null ? myBaseProfile.getName() : "-") + " key: " + key);
    return tools.getLevel();
  }

  @Override
  @NotNull
  public ModifiableModel getModifiableModel() {
    InspectionProfileImpl modifiableModel = new InspectionProfileImpl(this);
    modifiableModel.myExternalInfo.copy(myExternalInfo);
    return modifiableModel;
  }

  @Override
  public void copyFrom(InspectionProfile profile) {
    super.copyFrom(profile);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
    myBaseProfile = inspectionProfile.myBaseProfile;
  }

  private void copyToolsConfigurations(InspectionProfileImpl profile, @Nullable Project project) {
    try {
      for (ToolsImpl toolList : profile.myTools.values()) {
        final ToolsImpl tools = myTools.get(toolList.getShortName());
        final ScopeToolState defaultState = toolList.getDefaultState();
        tools.setDefaultState(copyToolSettings((InspectionToolWrapper)defaultState.getTool()), defaultState.isEnabled(), defaultState.getLevel());
        tools.removeAllScopes();
        tools.setEnabled(toolList.isEnabled());
        final List<ScopeToolState> nonDefaultToolStates = toolList.getNonDefaultTools();
        if (nonDefaultToolStates != null) {
          for (ScopeToolState state : nonDefaultToolStates) {
            final InspectionTool inspectionTool = copyToolSettings((InspectionToolWrapper)state.getTool());
            final NamedScope scope = project != null ? state.getScope(project) : state.getScope();
            if (scope != null) {
              tools.addTool(scope, inspectionTool, state.isEnabled(), state.getLevel());
            } else {
              tools.addTool(state.getScopeName(), inspectionTool, state.isEnabled(), state.getLevel());
            }
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

  private InspectionTool copyToolSettings(InspectionToolWrapper tool)
    throws WriteExternalException, InvalidDataException {
    final InspectionTool inspectionTool = tool.createCopy();
    if (tool.isInitialized()) {
      @NonNls String tempRoot = "config";
      Element config = new Element(tempRoot);
      tool.writeSettings(config);
      inspectionTool.readSettings(config);
    }
    return inspectionTool;
  }

  @Override
  public void cleanup(Project project) {
    for (final ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        for (InspectionProfileEntry tool : toolList.getAllTools()) {
          tool.projectClosed(project);
          if (((InspectionTool)tool).getContext() != null) {
            ((InspectionTool)tool).cleanup();
          }
        }
      }
    }
  }

  public void enableTool(String inspectionTool) {
    final ToolsImpl tools = getTools(inspectionTool);
    tools.setEnabled(true);
    if (tools.getNonDefaultTools() == null) {
      tools.getDefaultState().setEnabled(true);
    }
  }

  @Override
  public void enableTool(String inspectionTool, NamedScope namedScope) {
    getTools(inspectionTool).enableTool(namedScope);
  }

  @Override
  public void disableTool(String inspectionTool, NamedScope namedScope) {
    getTools(inspectionTool).disableTool(namedScope);
  }


  @Override
  public void disableTool(String inspectionTool) {
    final ToolsImpl tools = getTools(inspectionTool);
    tools.setEnabled(false);
    if (tools.getNonDefaultTools() == null) {
      tools.getDefaultState().setEnabled(false);
    }
  }

  @Override
  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    getTools(key.toString()).setLevel(level);
  }

  @Override
  public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
    if (key == null) {
      return false;
    }
    final Tools toolState = getTools(key.toString());
    return toolState != null && toolState.isEnabled(element);
  }

  @Override
  public boolean isToolEnabled(HighlightDisplayKey key) {
    return isToolEnabled(key, (PsiElement)null);
  }

  @Override
  public boolean isExecutable() {
    initInspectionTools(null);
    for (ToolsImpl tools : myTools.values()) {
      if (tools.isEnabled()) return true;
    }
    return false;
  }

  //invoke when isChanged() == true
  @Override
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
    myBaseProfile = inspectionProfile.myBaseProfile;
    myTools = inspectionProfile.myTools;
    myProfileManager = inspectionProfile.myProfileManager;

    myExternalInfo.copy(inspectionProfile.getExternalInfo());

    InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
  }

  private static class InspectionProfileImplHolder {
    private static final InspectionProfileImpl DEFAULT_PROFILE = new InspectionProfileImpl("Default");
  }

  public static InspectionProfileImpl getDefaultProfile() {
    return InspectionProfileImplHolder.DEFAULT_PROFILE;
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

  public void convert(Element element) {
    initInspectionTools(null);
    final Element scopes = element.getChild(DefaultProjectProfileManager.SCOPES);
    if (scopes == null) {
      return;
    }
    final List children = scopes.getChildren(SCOPE);
    for (Object s : children) {
      Element scopeElement = (Element)s;
      final String profile = scopeElement.getAttributeValue(DefaultProjectProfileManager.PROFILE);
      if (profile != null) {
        final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)getProfileManager().getProfile(profile);
        if (inspectionProfile != null) {
          final NamedScope scope = getProfileManager().getScopesManager().getScope(scopeElement.getAttributeValue(NAME));
          if (scope != null) {
            for (InspectionProfileEntry entry : inspectionProfile.getInspectionTools(null)) {
              final HighlightDisplayKey key = HighlightDisplayKey.find(entry.getShortName());
              try {
                getTools(entry.getShortName())
                  .addTool(scope, copyToolSettings((InspectionToolWrapper)entry), inspectionProfile.isToolEnabled(key),
                           inspectionProfile.getErrorLevel(key, (NamedScope)null));
              }
              catch (Exception e) {
                LOG.error(e);
              }
            }
          }
        }
      }
    }
    reduceConvertedScopes();
  }

  private void reduceConvertedScopes() {
    for (ToolsImpl tools : myTools.values()) {
      final ScopeToolState toolState = tools.getDefaultState();
      final List<ScopeToolState> nonDefaultTools = tools.getNonDefaultTools();
      if (nonDefaultTools != null) {
        boolean equal = true;
        boolean isEnabled = toolState.isEnabled();
        for (ScopeToolState state : nonDefaultTools) {
          isEnabled |= state.isEnabled();
          if (!state.equalTo(toolState)) {
            equal = false;
          }
        }
        tools.setEnabled(isEnabled);
        if (equal) {
          tools.removeAllScopes();
        }
      }
    }
  }

  @Override
  @NotNull
  public ExternalInfo getExternalInfo() {
    return myExternalInfo;
  }

  public List<ScopeToolState> getAllTools() {
    initInspectionTools(null);
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (Tools tools : myTools.values()) {
      result.addAll(tools.getTools());
    }
    return result;
  }

  public List<ScopeToolState> getDefaultStates() {
    initInspectionTools(null);
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (Tools tools : myTools.values()) {
      result.add(tools.getDefaultState());
    }
    return result;
  }

  public List<ScopeToolState> getNonDefaultTools(String shortName) {
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    final List<ScopeToolState> nonDefaultTools = getTools(shortName).getNonDefaultTools();
    if (nonDefaultTools != null) {
      result.addAll(nonDefaultTools);
    }
    return result;
  }

  public boolean isToolEnabled(HighlightDisplayKey key, NamedScope namedScope) {
    return getTools(key.toString()).isEnabled(namedScope);
  }

  public void removeScope(String toolId, int scopeIdx) {
    getTools(toolId).removeScope(scopeIdx);
  }

  public void removeAllScopes(String toolId) {
    getTools(toolId).removeAllScopes();
  }

  public void setScope(String toolId, int idx, NamedScope namedScope) {
    getTools(toolId).setScope(idx, namedScope);
  }

  public void moveScope(String toolId, int idx, int dir) {
    getTools(toolId).moveScope(idx, dir);
  }

  @Nullable
  private Map<String, Boolean> getDisplayLevelMap() {
    if (myBaseProfile == null) return null;
    if (myDisplayLevelMap == null) {
      initInspectionTools(null);
      myDisplayLevelMap = new TreeMap<String, Boolean>();
      for (String toolId : myTools.keySet()) {
        myDisplayLevelMap.put(toolId, toolSettingsAreEqual(toolId, myBaseProfile, this));
      }
    }
    return myDisplayLevelMap;
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key, NamedScope scope) {
    final ToolsImpl tools = getTools(key.toString());
    return tools != null ? tools.getLevel(scope) : HighlightDisplayLevel.WARNING;
  }

  public ScopeToolState addScope(InspectionProfileEntry tool, NamedScope scope, HighlightDisplayLevel level, boolean enabled) {
    return getTools(tool.getShortName()).prependTool(scope, tool, enabled, level);
  }


  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level, int scopeIdx) {
    getTools(key.toString()).setLevel(level, scopeIdx);
  }

  private ToolsImpl getTools(String toolId) {
    initInspectionTools(null);
    return myTools.get(toolId);
  }

  public void disableAllTools() {
    for (InspectionProfileEntry entry : getInspectionTools(null)) {
      disableTool(entry.getShortName());
    }
  }

  public String toString() {
    return mySource == null ? getName() : getName() + " (copy)";
  }
}
