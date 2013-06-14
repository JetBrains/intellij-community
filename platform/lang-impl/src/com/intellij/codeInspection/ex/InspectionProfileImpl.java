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
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
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
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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

  private Map<String, ToolsImpl> myTools = new THashMap<String, ToolsImpl>();

  private Map<String, Boolean> myDisplayLevelMap;
  @NotNull private final Map<String, Element> myDeinstalledInspectionsSettings;
  private boolean myLockedProfile = false;

  protected InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String INSPECTION_TOOL_TAG = "inspection_tool";

  @NonNls private static final String CLASS_TAG = "class";
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String ROOT_ELEMENT_TAG = "inspections";

  private String myEnabledTool = null;
  @NonNls private static final String USED_LEVELS = "used_levels";

  final InspectionToolRegistrar myRegistrar;
  @NonNls private static final String IS_LOCKED = "is_locked";
  private final ExternalInfo myExternalInfo = new ExternalInfo();
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

  public InspectionProfileImpl(@NotNull final String profileName,
                               @NotNull InspectionToolRegistrar registrar,
                               @NotNull final ProfileManager profileManager) {
    super(profileName);
    myRegistrar = registrar;
    myBaseProfile = getDefaultProfile();
    setProfileManager(profileManager);
    myDeinstalledInspectionsSettings = new TreeMap<String, Element>();
  }

  public InspectionProfileImpl(@NotNull @NonNls String profileName) {
    super(profileName);
    myRegistrar = InspectionToolRegistrar.getInstance();
    setProfileManager(InspectionProfileManager.getInstance());
    myDeinstalledInspectionsSettings = new TreeMap<String, Element>();
  }

  @NotNull
  public static InspectionProfileImpl createSimple(@NotNull String name, @NotNull final InspectionToolWrapper ... toolWrappers) {
    InspectionProfileImpl profile = new InspectionProfileImpl(name, new InspectionToolRegistrar(null) {
      @NotNull
      @Override
      public List<InspectionToolWrapper> createTools() {
        return Arrays.asList(toolWrappers);
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
    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      profile.enableTool(toolWrapper.getShortName());
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

  private static boolean toolSettingsAreEqual(String toolName, @NotNull InspectionProfileImpl profile1, @NotNull InspectionProfileImpl profile2) {
    final Tools toolList1 = profile1.myTools.get(toolName);
    final Tools toolList2 = profile2.myTools.get(toolName);

    return Comparing.equal(toolList1, toolList2);
  }

  @Override
  public boolean isProperSetting(@NotNull HighlightDisplayKey key) {
    return isProperSetting(key.toString());
  }

  @Override
  public boolean isProperSetting(String toolId) {
    if (myBaseProfile != null) {
      final Tools tools = myBaseProfile.getTools(toolId);
      final Tools currentTools = myTools.get(toolId);
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
    final InspectionToolWrapper[] profileEntries = getInspectionTools(null);
    for (InspectionToolWrapper toolWrapper : profileEntries) {
      disableTool(toolWrapper.getShortName());
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
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    final String locked = element.getAttributeValue(IS_LOCKED);
    if (locked != null) {
      myLockedProfile = Boolean.parseBoolean(locked);
    }
    if (!ApplicationManager.getApplication().isUnitTestMode() || myBaseProfile == null) {
      // todo remove this strange side effect
      myBaseProfile = getDefaultProfile();
    }
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

    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      // make clone to avoid retaining memory via o.parent pointers
      Element toolElement = ((Element)o).clone();
      IdeaPluginDescriptorImpl.internJDOMElement(toolElement);

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      myDeinstalledInspectionsSettings.put(toolClassName, toolElement);
    }
  }

  @NotNull
  public Set<HighlightSeverity> getUsedSeverities() {
    LOG.assertTrue(myInitialized);
    final Set<HighlightSeverity> result = new HashSet<HighlightSeverity>();
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        result.add(state.getLevel().getSeverity());
      }
    }
    return result;
  }


  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    element.setAttribute(IS_LOCKED, String.valueOf(myLockedProfile));

    synchronized (myExternalInfo) {
    if (!myInitialized) {
        for (Element el : myDeinstalledInspectionsSettings.values()) {
          element.addContent(el.clone());
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
          element.addContent(toolElement.clone());
        }
      }
    }
  }

  public void collectDependentInspections(@NotNull InspectionToolWrapper toolWrapper, @NotNull Set<InspectionToolWrapper> dependentEntries) {
    String mainToolId = toolWrapper.getMainToolId();

    if (mainToolId != null) {
      InspectionToolWrapper dependentEntryWrapper = getInspectionTool(mainToolId);

      if (dependentEntryWrapper != null) {
        if (!dependentEntries.add(dependentEntryWrapper)) {
          collectDependentInspections(dependentEntryWrapper, dependentEntries);
        }
      }
      else {
        LOG.error("Can't find main tool: " + mainToolId);
      }
    }
  }

  @Override
  @Nullable
  public InspectionToolWrapper getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
    final Tools toolList = getTools(shortName);
    return toolList != null ? (InspectionToolWrapper)toolList.getInspectionTool(element) : null;
  }

  @Nullable
  @Override
  public InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element) {
    InspectionToolWrapper tool = getInspectionTool(shortName, element);
    return tool == null ? tool : tool.getTool();
  }

  @Override
  public <T extends InspectionProfileEntry> T getUnwrappedTool(@NotNull Key<T> shortNameKey, @NotNull PsiElement element) {
    //noinspection unchecked
    return (T) getUnwrappedTool(shortNameKey.toString(), element);
  }

  @Override
  public void modifyProfile(@NotNull Consumer<ModifiableModel> modelConsumer) {
    ModifiableModel model = getModifiableModel();
    modelConsumer.consume(model);
    try {
      model.commit();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public <T extends InspectionProfileEntry> void modifyToolSettings(@NotNull final Key<T> shortNameKey,
                                                                    @NotNull final PsiElement psiElement,
                                                                    @NotNull final Consumer<T> toolConsumer) {
    modifyProfile(new Consumer<ModifiableModel>() {
      @Override
      public void consume(@NotNull ModifiableModel model) {
        InspectionProfileEntry tool = model.getUnwrappedTool(shortNameKey.toString(), psiElement);
        //noinspection unchecked
        toolConsumer.consume((T) tool);
      }
    });
  }

  @Override
  @Nullable
  public InspectionToolWrapper getInspectionTool(@NotNull String shortName) {
    final ToolsImpl tools = getTools(shortName);
    return tools != null? tools.getTool() : null;
  }

  public InspectionToolWrapper getToolById(@NotNull String id, @NotNull PsiElement element) {
    initInspectionTools(element.getProject());
    for (Tools toolList : myTools.values()) {
      final InspectionToolWrapper tool = (InspectionToolWrapper)toolList.getInspectionTool(element);
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
  public void scopesChanged() {
    for (ScopeToolState toolState : getAllTools()) {
      toolState.scopesChanged();
    }
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  @Override
  public void setEditable(final String displayName) {
    myEnabledTool = displayName;
  }

  public void load(@NotNull Element element) {
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
  public InspectionToolWrapper[] getInspectionTools(@Nullable PsiElement element) {
    initInspectionTools(element != null ? element.getProject() : null);
    List<InspectionToolWrapper> result = new ArrayList<InspectionToolWrapper>();
    for (Tools toolList : myTools.values()) {
      result.add((InspectionToolWrapper)toolList.getInspectionTool(element));
    }
    return result.toArray(new InspectionToolWrapper[result.size()]);
  }

  @Override
  @NotNull
  public List<Tools> getAllEnabledInspectionTools(Project project) {
    initInspectionTools(project);
    List<Tools> result = new ArrayList<Tools>();
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

  @NotNull
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
    for (InspectionToolWrapper toolWrapper : tools) {
      final String shortName = toolWrapper.getShortName();
      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key == null) {
        final InspectionEP extension = toolWrapper.getExtension();
        Computable<String> computable = extension == null ? new Computable.PredefinedValueComputable<String>(toolWrapper.getDisplayName()) : new Computable<String>() {
          @Override
          public String compute() {
            return extension.getDisplayName();
          }
        };
        if (toolWrapper instanceof LocalInspectionToolWrapper) {
          key = HighlightDisplayKey.register(shortName, computable, ((LocalInspectionToolWrapper)toolWrapper).getID(),
                                             ((LocalInspectionToolWrapper)toolWrapper).getAlternativeID());
        }
        else {
          key = HighlightDisplayKey.register(shortName, computable);
        }
      }

      LOG.assertTrue(key != null, shortName + " ; number of initialized tools: " + myTools.size());
      HighlightDisplayLevel level = myBaseProfile != null ? myBaseProfile.getErrorLevel(key) : toolWrapper.getDefaultLevel();
      boolean enabled = myBaseProfile != null ? myBaseProfile.isToolEnabled(key) : toolWrapper.isEnabledByDefault();
      final ToolsImpl toolsList = new ToolsImpl(toolWrapper, level, !myLockedProfile && enabled, enabled);
      final Element element = myDeinstalledInspectionsSettings.remove(toolWrapper.getShortName());
      if (element != null) {
        try {
          toolsList.readExternal(element, this);
        }
        catch (InvalidDataException e) {
          LOG.error("Can't read settings for " + toolWrapper, e);
        }
      }
      myTools.put(toolWrapper.getShortName(), toolsList);
    }
    if (mySource != null) {
      copyToolsConfigurations(mySource, project);
    }
    return true;
  }

  @NotNull
  private List<InspectionToolWrapper> createTools() {
    if (mySource != null) {
      return ContainerUtil.map(mySource.getAllTools(), new Function<ScopeToolState, InspectionToolWrapper>() {
        @NotNull
        @Override
        public InspectionToolWrapper fun(@NotNull ScopeToolState state) {
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
  public void copyFrom(@NotNull InspectionProfile profile) {
    super.copyFrom(profile);
    final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
    myBaseProfile = inspectionProfile.myBaseProfile;
  }

  private void copyToolsConfigurations(@NotNull InspectionProfileImpl profile, @Nullable Project project) {
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
            final InspectionToolWrapper toolWrapper = copyToolSettings((InspectionToolWrapper)state.getTool());
            final NamedScope scope = project != null ? state.getScope(project) : ScopeToolStateUtil.getScope(state);
            if (scope != null) {
              tools.addTool(scope, toolWrapper, state.isEnabled(), state.getLevel());
            }
            else {
              tools.addTool(state.getScopeName(), toolWrapper, state.isEnabled(), state.getLevel());
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

  @NotNull
  private static InspectionToolWrapper copyToolSettings(@NotNull InspectionToolWrapper tool)
    throws WriteExternalException, InvalidDataException {
    final InspectionToolWrapper inspectionTool = tool.createCopy();
    if (tool.isInitialized()) {
      @NonNls String tempRoot = "config";
      Element config = new Element(tempRoot);
      tool.writeSettings(config);
      inspectionTool.readSettings(config);
    }
    return inspectionTool;
  }

  @Override
  public void cleanup(@NotNull Project project) {
    for (final ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        for (InspectionToolWrapper toolWrapper : toolList.getAllTools()) {
          toolWrapper.projectClosed(project);
          toolWrapper.cleanup();
        }
      }
    }
  }

  public void enableTool(@NotNull String inspectionTool) {
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
  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level) {
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
    for (Tools tools : myTools.values()) {
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

  private void commit(@NotNull InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.myName;
    myLocal = inspectionProfile.myLocal;
    myLockedProfile = inspectionProfile.myLockedProfile;
    myDisplayLevelMap = inspectionProfile.myDisplayLevelMap;
    myBaseProfile = inspectionProfile.myBaseProfile;
    myTools = inspectionProfile.myTools;
    myProfileManager = inspectionProfile.getProfileManager();

    myExternalInfo.copy(inspectionProfile.getExternalInfo());

    InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
  }

  private static class InspectionProfileImplHolder {
    private static final InspectionProfileImpl DEFAULT_PROFILE = new InspectionProfileImpl("Default");
  }

  @NotNull
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

  @Override
  public void convert(@NotNull Element element) {
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
            for (InspectionToolWrapper wrapper : inspectionProfile.getInspectionTools(null)) {
              final HighlightDisplayKey key = HighlightDisplayKey.find(wrapper.getShortName());
              try {
                InspectionToolWrapper toolWrapper = copyToolSettings(wrapper);
                getTools(wrapper.getShortName())
                  .addTool(scope, toolWrapper, inspectionProfile.isToolEnabled(key), inspectionProfile.getErrorLevel(key, (NamedScope)null));
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

  @NotNull
  public List<ScopeToolState> getAllTools() {
    initInspectionTools(null);
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (Tools tools : myTools.values()) {
      result.addAll(tools.getTools());
    }
    return result;
  }

  @NotNull
  public List<ScopeToolState> getDefaultStates() {
    initInspectionTools(null);
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    for (Tools tools : myTools.values()) {
      result.add(tools.getDefaultState());
    }
    return result;
  }

  @NotNull
  public List<ScopeToolState> getNonDefaultTools(String shortName) {
    final List<ScopeToolState> result = new ArrayList<ScopeToolState>();
    final List<ScopeToolState> nonDefaultTools = getTools(shortName).getNonDefaultTools();
    if (nonDefaultTools != null) {
      result.addAll(nonDefaultTools);
    }
    return result;
  }

  public boolean isToolEnabled(@NotNull HighlightDisplayKey key, NamedScope namedScope) {
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

  /**
   * @return null if it has no base profile
   */
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

  @NotNull
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, NamedScope scope) {
    final ToolsImpl tools = getTools(key.toString());
    return tools != null ? tools.getLevel(scope) : HighlightDisplayLevel.WARNING;
  }

  public ScopeToolState addScope(@NotNull InspectionToolWrapper toolWrapper, NamedScope scope, @NotNull HighlightDisplayLevel level, boolean enabled) {
    return getTools(toolWrapper.getShortName()).prependTool(scope, toolWrapper, enabled, level);
  }

  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, int scopeIdx) {
    getTools(key.toString()).setLevel(level, scopeIdx);
  }

  private ToolsImpl getTools(@NotNull String toolId) {
    initInspectionTools(null);
    return myTools.get(toolId);
  }

  public void enableAllTools() {
    for (InspectionToolWrapper entry : getInspectionTools(null)) {
      enableTool(entry.getShortName());
    }
  }

  public void disableAllTools() {
    for (InspectionToolWrapper entry : getInspectionTools(null)) {
      disableTool(entry.getShortName());
    }
  }

  @NotNull
  public String toString() {
    return mySource == null ? getName() : getName() + " (copy)";
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o)) {
      return ((InspectionProfileImpl) o).getProfileManager() == InspectionProfileImpl.this.getProfileManager();
    }
    return false;
  }
}
