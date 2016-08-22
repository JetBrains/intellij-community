/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.configurationStore.SerializableScheme;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManagerKt;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author max
 */
public class InspectionProfileImpl extends ProfileEx implements ModifiableModel, InspectionProfile, ExternalizableScheme,
                                                                SerializableScheme {
  @NonNls static final String INSPECTION_TOOL_TAG = "inspection_tool";
  @NonNls static final String CLASS_TAG = "class";
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  @NonNls private static final String VALID_VERSION = "1.0";
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String USED_LEVELS = "used_levels";
  public static final String DEFAULT_PROFILE_NAME = "Default";
  @TestOnly
  public static boolean INIT_INSPECTIONS = false;
  private final InspectionToolRegistrar myRegistrar;
  @NotNull
  private final Map<String, Element> myUninitializedSettings = new TreeMap<>();
  protected InspectionProfileImpl mySource;
  private Map<String, ToolsImpl> myTools = new THashMap<>();
  private volatile Map<String, Boolean> myDisplayLevelMap;
  @Attribute("is_locked")
  private boolean myLockedProfile;
  private final InspectionProfileImpl myBaseProfile;
  private volatile String myToolShortName = null;
  private String[] myScopesOrder;
  private String myDescription;
  private boolean myModified;
  private volatile boolean myInitialized;

  private final Object myLock = new Object();

  private SchemeDataHolder<? super InspectionProfileImpl> myDataHolder;

  InspectionProfileImpl(@NotNull InspectionProfileImpl inspectionProfile) {
    this(inspectionProfile.getName(), inspectionProfile.myRegistrar, inspectionProfile.getProfileManager(), inspectionProfile.myBaseProfile, null);
    myUninitializedSettings.putAll(inspectionProfile.myUninitializedSettings);

    setProjectLevel(inspectionProfile.isProjectLevel());
    myLockedProfile = inspectionProfile.myLockedProfile;
    mySource = inspectionProfile;
    copyFrom(inspectionProfile);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolRegistrar registrar,
                               @NotNull ProfileManager profileManager) {
    this(profileName, registrar, profileManager, getDefaultProfile(), null);
  }

  public InspectionProfileImpl(@NotNull @NonNls String profileName) {
    this(profileName, InspectionToolRegistrar.getInstance(), InspectionProfileManager.getInstance(), null, null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolRegistrar registrar,
                               @NotNull ProfileManager profileManager,
                               @Nullable InspectionProfileImpl baseProfile,
                               @Nullable SchemeDataHolder<? super InspectionProfileImpl> dataHolder) {
    super(profileName);

    myRegistrar = registrar;
    myBaseProfile = baseProfile;
    myDataHolder = dataHolder;
    myProfileManager = profileManager;
  }

  @NotNull
  public static InspectionProfileImpl createSimple(@NotNull String name,
                                                   @NotNull Project project,
                                                   @NotNull List<InspectionToolWrapper> toolWrappers) {
    InspectionProfileImpl profile = new InspectionProfileImpl(name, new InspectionToolRegistrar() {
      @NotNull
      @Override
      public List<InspectionToolWrapper> createTools() {
        return toolWrappers;
      }
    }, InspectionProfileManager.getInstance());
    for (InspectionToolWrapper toolWrapper : toolWrappers) {
      profile.enableTool(toolWrapper.getShortName(), project);
    }
    return profile;
  }

  private static boolean toolSettingsAreEqual(@NotNull String toolName, @NotNull InspectionProfileImpl profile1, @NotNull InspectionProfileImpl profile2) {
    final Tools toolList1 = profile1.myTools.get(toolName);
    final Tools toolList2 = profile2.myTools.get(toolName);

    return Comparing.equal(toolList1, toolList2);
  }

  @NotNull
  private static InspectionToolWrapper copyToolSettings(@NotNull InspectionToolWrapper toolWrapper)
    throws WriteExternalException, InvalidDataException {
    final InspectionToolWrapper inspectionTool = toolWrapper.createCopy();
    if (toolWrapper.isInitialized()) {
      @NonNls String tempRoot = "config";
      Element config = new Element(tempRoot);
      toolWrapper.getTool().writeSettings(config);
      inspectionTool.getTool().readSettings(config);
    }
    return inspectionTool;
  }

  @NotNull
  public static InspectionProfileImpl getDefaultProfile() {
    return InspectionProfileImplHolder.DEFAULT_PROFILE;
  }

  @Override
  public void setModified(final boolean modified) {
    myModified = modified;
  }

  @Override
  public InspectionProfile getParentProfile() {
    return mySource;
  }

  @Override
  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isChanged() {
    if (mySource != null && mySource.myLockedProfile != myLockedProfile) return true;
    return myModified;
  }

  @Override
  public boolean isProperSetting(@NotNull String toolId) {
    if (myBaseProfile != null) {
      final Tools tools = myBaseProfile.getTools(toolId, null);
      final Tools currentTools = myTools.get(toolId);
      return !Comparing.equal(tools, currentTools);
    }
    return false;
  }

  @Override
  public void resetToBase(Project project) {
    initInspectionTools(project);

    copyToolsConfigurations(myBaseProfile, project);
    myDisplayLevelMap = null;
  }

  @Override
  public void resetToEmpty(Project project) {
    initInspectionTools(project);
    final InspectionToolWrapper[] profileEntries = getInspectionTools(null);
    for (InspectionToolWrapper toolWrapper : profileEntries) {
      disableTool(toolWrapper.getShortName(), project);
    }
  }

  @Override
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    Project project = element == null ? null : element.getProject();
    final ToolsImpl tools = getTools(inspectionToolKey.toString(), project);
    HighlightDisplayLevel level = tools != null ? tools.getLevel(element) : HighlightDisplayLevel.WARNING;
    if (!((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().isSeverityValid(level.getSeverity().getName())) {
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level, project);
    }
    return level;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);

    final String version = element.getAttributeValue(VERSION_TAG);
    if (version == null || !version.equals(VALID_VERSION)) {
      element = InspectionProfileConvertor.convertToNewFormat(element, this);
    }

    final Element highlightElement = element.getChild(USED_LEVELS);
    if (highlightElement != null) {
      // from old profiles
      ((SeverityProvider)getProfileManager()).getOwnSeverityRegistrar().readExternal(highlightElement);
    }

    for (Element toolElement : element.getChildren(INSPECTION_TOOL_TAG)) {
      myUninitializedSettings.put(toolElement.getAttributeValue(CLASS_TAG), toolElement.clone());
    }
  }

  @NotNull
  public Set<HighlightSeverity> getUsedSeverities() {
    LOG.assertTrue(myInitialized);
    Set<HighlightSeverity> result = new THashSet<>();
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        result.add(state.getLevel().getSeverity());
      }
    }
    return result;
  }

  @NotNull
  public Element writeScheme() {
    if (myDataHolder != null) {
      return myDataHolder.read();
    }

    Element result = isProjectLevel() ? new Element("profile").setAttribute("version", "1.0") : new Element("inspections").setAttribute("profile_name", getName());
    serializeInto(result, false);

    if (isProjectLevel()) {
      return new Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(result);
    }
    return result;
  }

  @Override
  public void serializeInto(@NotNull Element element, boolean preserveCompatibility) {
    // must be first - compatibility
    element.setAttribute(VERSION_TAG, VALID_VERSION);

    super.serializeInto(element, preserveCompatibility);

    synchronized (myLock) {
      if (!myInitialized) {
        for (Element el : myUninitializedSettings.values()) {
          element.addContent(el.clone());
        }
        return;
      }
    }

    Map<String, Boolean> diffMap = getDisplayLevelMap();
    if (diffMap == null) {
      return;
    }

    diffMap = new TreeMap<>(diffMap);
    for (String toolName : myUninitializedSettings.keySet()) {
      diffMap.put(toolName, false);
    }

    for (String toolName : diffMap.keySet()) {
      if (!myLockedProfile && diffMap.get(toolName).booleanValue()) {
        markSettingsMerged(toolName, element);
        continue;
      }

      Element toolElement = myUninitializedSettings.get(toolName);
      if (toolElement == null) {
        ToolsImpl toolList = myTools.get(toolName);
        LOG.assertTrue(toolList != null);
        Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
        inspectionElement.setAttribute(CLASS_TAG, toolName);
        try {
          toolList.writeExternal(inspectionElement);
        }
        catch (WriteExternalException e) {
          LOG.error(e);
          continue;
        }

        if (!areSettingsMerged(toolName, inspectionElement)) {
          element.addContent(inspectionElement);
        }
      }
      else {
        element.addContent(toolElement.clone());
      }
    }
  }

  private void markSettingsMerged(@NotNull String toolName, @NotNull Element element) {
    //add marker if already merged but result is now default (-> empty node)
    final String mergedName = InspectionElementsMergerBase.getMergedMarkerName(toolName);
    if (!myUninitializedSettings.containsKey(mergedName)) {
      final InspectionElementsMergerBase merger = getMerger(toolName);
      if (merger != null && merger.markSettingsMerged(myUninitializedSettings)) {
        element.addContent(new Element(INSPECTION_TOOL_TAG).setAttribute(CLASS_TAG, mergedName));
      }
    }
  }

  private boolean areSettingsMerged(String toolName, Element inspectionElement) {
    //skip merged settings as they could be restored from already provided data
    final InspectionElementsMergerBase merger = getMerger(toolName);
    return merger != null && merger.areSettingsMerged(myUninitializedSettings, inspectionElement);
  }

  public void collectDependentInspections(@NotNull InspectionToolWrapper toolWrapper,
                                          @NotNull Set<InspectionToolWrapper> dependentEntries,
                                          Project project) {
    String mainToolId = toolWrapper.getMainToolId();

    if (mainToolId != null) {
      InspectionToolWrapper dependentEntryWrapper = getInspectionTool(mainToolId, project);

      if (dependentEntryWrapper == null) {
        LOG.error("Can't find main tool: '" + mainToolId+"' which was specified in "+toolWrapper);
        return;
      }
      if (!dependentEntries.add(dependentEntryWrapper)) {
        collectDependentInspections(dependentEntryWrapper, dependentEntries, project);
      }
    }
  }

  @Override
  @Nullable
  public InspectionToolWrapper getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
    final Tools toolList = getTools(shortName, element.getProject());
    return toolList == null ? null : toolList.getInspectionTool(element);
  }

  @Nullable
  @Override
  public InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element) {
    InspectionToolWrapper tool = getInspectionTool(shortName, element);
    return tool == null ? null : tool.getTool();
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
    model.commit();
  }

  @Override
  public <T extends InspectionProfileEntry> void modifyToolSettings(@NotNull final Key<T> shortNameKey,
                                                                    @NotNull final PsiElement psiElement,
                                                                    @NotNull final Consumer<T> toolConsumer) {
    modifyProfile(model -> {
      InspectionProfileEntry tool = model.getUnwrappedTool(shortNameKey.toString(), psiElement);
      //noinspection unchecked
      toolConsumer.consume((T) tool);
    });
  }

  @Override
  @Nullable
  public InspectionToolWrapper getInspectionTool(@NotNull String shortName, Project project) {
    final ToolsImpl tools = getTools(shortName, project);
    return tools != null? tools.getTool() : null;
  }

  public InspectionToolWrapper getToolById(@NotNull String id, @NotNull PsiElement element) {
    initInspectionTools(element.getProject());
    for (Tools toolList : myTools.values()) {
      final InspectionToolWrapper tool = toolList.getInspectionTool(element);
      if (id.equals(tool.getID())) return tool;
    }
    return null;
  }

  @Nullable
  public List<InspectionToolWrapper> findToolsById(@NotNull String id, @NotNull PsiElement element) {
    List<InspectionToolWrapper> result = null;
    initInspectionTools(element.getProject());
    for (Tools toolList : myTools.values()) {
      final InspectionToolWrapper tool = toolList.getInspectionTool(element);
      if (id.equals(tool.getID())) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(tool);
      }
    }
    return result;
  }

  @Override
  public void save() {
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  @Nullable
  @Override
  public String getSingleTool() {
    return myToolShortName;
  }

  @Override
  public void setSingleTool(@NotNull final String toolShortName) {
    myToolShortName = toolShortName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return getName();
  }

  @Override
  public void scopesChanged() {
    for (ScopeToolState toolState : getAllTools(null)) {
      toolState.scopesChanged();
    }
    InspectionProfileManager.getInstance().fireProfileChanged(this);
  }

  @Override
  @Transient
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
    initInspectionTools(element == null ? null : element.getProject());
    List<InspectionToolWrapper> result = new ArrayList<>();
    for (Tools toolList : myTools.values()) {
      result.add(toolList.getInspectionTool(element));
    }
    return result.toArray(new InspectionToolWrapper[result.size()]);
  }

  @Override
  @NotNull
  public List<Tools> getAllEnabledInspectionTools(Project project) {
    initInspectionTools(project);
    List<Tools> result = new ArrayList<>();
    for (final ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        result.add(toolList);
      }
    }
    return result;
  }

  @Override
  public void disableTool(@NotNull String toolId, @NotNull PsiElement element) {
    getTools(toolId, element.getProject()).disableTool(element);
  }

  public void disableToolByDefault(@NotNull Collection<String> toolIds, @Nullable Project project) {
    for (String toolId : toolIds) {
      getTools(toolId, project).setDefaultEnabled(false);
    }
  }

  @NotNull
  public ScopeToolState getToolDefaultState(@NotNull String toolId, @Nullable Project project) {
    return getTools(toolId, project).getDefaultState();
  }

  public void enableToolsByDefault(@NotNull List<String> toolIds, Project project) {
    for (final String toolId : toolIds) {
      getTools(toolId, project).setDefaultEnabled(true);
    }
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void initInspectionTools(@Nullable Project project) {
    //noinspection TestOnlyProblems
    if (myInitialized || (ApplicationManager.getApplication().isUnitTestMode() && !INIT_INSPECTIONS)) {
      return;
    }

    synchronized (myLock) {
      if (!myInitialized) {
        initialize(project);
      }
    }
  }

  private void initialize(@Nullable Project project) {
    SchemeDataHolder<? super InspectionProfileImpl> dataHolder = myDataHolder;
    if (dataHolder != null) {
      myDataHolder = null;
      Element element = dataHolder.read();
      if (element.getName().equals("component")) {
        element = element.getChild("profile");
      }
      assert element != null;
      readExternal(element);
    }

    if (myBaseProfile != null) {
      myBaseProfile.initInspectionTools(project);
    }

    final List<InspectionToolWrapper> tools;
    try {
      tools = createTools(project);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }

    final Map<String, List<String>> dependencies = new THashMap<>();
    for (InspectionToolWrapper toolWrapper : tools) {
      addTool(project, toolWrapper, dependencies);
    }

    DFSTBuilder<String> builder = new DFSTBuilder<>(GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<String>() {
      @Override
      public Collection<String> getNodes() {
        return dependencies.keySet();
      }

      @Override
      public Iterator<String> getIn(String n) {
        return dependencies.get(n).iterator();
      }
    })));
    if (builder.isAcyclic()) {
      myScopesOrder = ArrayUtil.toStringArray(builder.getSortedNodes());
    }

    if (mySource != null) {
      copyToolsConfigurations(mySource, project);
    }

    myInitialized = true;
    if (dataHolder != null) {
      // should be only after set myInitialized
      dataHolder.updateDigest(this);
    }
  }

  public void addTool(@Nullable Project project, @NotNull InspectionToolWrapper toolWrapper, @NotNull Map<String, List<String>> dependencies) {
    final String shortName = toolWrapper.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      final InspectionEP extension = toolWrapper.getExtension();
      Computable<String> computable = extension == null ? new Computable.PredefinedValueComputable<>(toolWrapper.getDisplayName()) : extension::getDisplayName;
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        key = HighlightDisplayKey.register(shortName, computable, toolWrapper.getID(),
                                           ((LocalInspectionToolWrapper)toolWrapper).getAlternativeID());
      }
      else {
        key = HighlightDisplayKey.register(shortName, computable);
      }
    }

    if (key == null) {
      LOG.error(shortName + " ; number of initialized tools: " + myTools.size());
      return;
    }

    HighlightDisplayLevel baseLevel = myBaseProfile != null && myBaseProfile.getTools(shortName, project) != null
                                   ? myBaseProfile.getErrorLevel(key, project)
                                   : HighlightDisplayLevel.DO_NOT_SHOW;
    HighlightDisplayLevel defaultLevel = toolWrapper.getDefaultLevel();
    HighlightDisplayLevel level = baseLevel.getSeverity().compareTo(defaultLevel.getSeverity()) > 0 ? baseLevel : defaultLevel;
    //HighlightDisplayLevel level = myBaseProfile != null && myBaseProfile.getTools(shortName, project) != null ? myBaseProfile.getErrorLevel(key, project) : toolWrapper.getDefaultLevel();
    boolean enabled = myBaseProfile != null ? myBaseProfile.isToolEnabled(key) : toolWrapper.isEnabledByDefault();
    final ToolsImpl toolsList = new ToolsImpl(toolWrapper, level, !myLockedProfile && enabled, enabled);
    final Element element = myUninitializedSettings.remove(shortName);
    try {
      if (element != null) {
        toolsList.readExternal(element, this, dependencies);
      }
      else if (!myUninitializedSettings.containsKey(InspectionElementsMergerBase.getMergedMarkerName(shortName))) {
        final InspectionElementsMergerBase merger = getMerger(shortName);
        if (merger != null) {
          final Element merged = merger.merge(myUninitializedSettings);
          if (merged != null) {
            toolsList.readExternal(merged, this, dependencies);
          }
        }
      }
    }
    catch (InvalidDataException e) {
      LOG.error("Can't read settings for " + toolWrapper, e);
    }
    myTools.put(shortName, toolsList);
  }

  @Nullable
  private static InspectionElementsMergerBase getMerger(String shortName) {
    final InspectionElementsMerger merger = InspectionElementsMerger.getMerger(shortName);
    if (merger instanceof InspectionElementsMergerBase) {
      return (InspectionElementsMergerBase)merger;
    }
    return merger != null ? new InspectionElementsMergerBase() {
      @Override
      public String getMergedToolName() {
        return merger.getMergedToolName();
      }

      @Override
      public String[] getSourceToolNames() {
        return merger.getSourceToolNames();
      }
    } : null;
  }

  @Nullable
  @Transient
  public String[] getScopesOrder() {
    return myScopesOrder;
  }

  public void setScopesOrder(String[] scopesOrder) {
    myScopesOrder = scopesOrder;
  }

  @NotNull
  private List<InspectionToolWrapper> createTools(@Nullable Project project) {
    if (mySource != null) {
      return ContainerUtil.map(mySource.getDefaultStates(project), ScopeToolState::getTool);
    }
    return myRegistrar.createTools();
  }

  private HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, @Nullable Project project) {
    final ToolsImpl tools = getTools(key.toString(), project);
    LOG.assertTrue(tools != null, "profile name: " + myName +  " base profile: " + (myBaseProfile != null ? myBaseProfile.getName() : "-") + " key: " + key);
    return tools.getLevel();
  }

  @Override
  @NotNull
  public InspectionProfileImpl getModifiableModel() {
    return new InspectionProfileImpl(this);
  }

  private void copyToolsConfigurations(@NotNull InspectionProfileImpl profile, @Nullable Project project) {
    try {
      for (ToolsImpl toolList : profile.myTools.values()) {
        final ToolsImpl tools = myTools.get(toolList.getShortName());
        final ScopeToolState defaultState = toolList.getDefaultState();
        tools.setDefaultState(copyToolSettings(defaultState.getTool()), defaultState.isEnabled(), defaultState.getLevel());
        tools.removeAllScopes();
        final List<ScopeToolState> nonDefaultToolStates = toolList.getNonDefaultTools();
        if (nonDefaultToolStates != null) {
          for (ScopeToolState state : nonDefaultToolStates) {
            final InspectionToolWrapper toolWrapper = copyToolSettings(state.getTool());
            final NamedScope scope = state.getScope(project);
            if (scope != null) {
              tools.addTool(scope, toolWrapper, state.isEnabled(), state.getLevel());
            }
            else {
              tools.addTool(state.getScopeName(), toolWrapper, state.isEnabled(), state.getLevel());
            }
          }
        }
        tools.setEnabled(toolList.isEnabled());
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @Override
  public void cleanup(@NotNull Project project) {
    if (!myInitialized) {
      return;
    }

    for (ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        toolList.cleanupTools(project);
      }
    }
  }

  public void enableTool(@NotNull String toolId, Project project) {
    final ToolsImpl tools = getTools(toolId, project);
    tools.setEnabled(true);
    if (tools.getNonDefaultTools() == null) {
      tools.getDefaultState().setEnabled(true);
    }
  }

  @Override
  public void enableTool(@NotNull String inspectionTool, NamedScope namedScope, Project project) {
    getTools(inspectionTool, project).enableTool(namedScope, project);
  }

  public void enableTools(@NotNull List<String> inspectionTools, NamedScope namedScope, Project project) {
    for (String inspectionTool : inspectionTools) {
      enableTool(inspectionTool, namedScope, project);
    }
  }

  @Override
  public void disableTool(@NotNull String inspectionTool, NamedScope namedScope, @NotNull Project project) {
    getTools(inspectionTool, project).disableTool(namedScope, project);
  }

  public void disableTools(@NotNull List<String> inspectionTools, NamedScope namedScope, @NotNull Project project) {
    for (String inspectionTool : inspectionTools) {
      disableTool(inspectionTool, namedScope, project);
    }
  }

  @Override
  public void disableTool(@NotNull String inspectionTool, Project project) {
    final ToolsImpl tools = getTools(inspectionTool, project);
    tools.setEnabled(false);
    if (tools.getNonDefaultTools() == null) {
      tools.getDefaultState().setEnabled(false);
    }
  }

  @Override
  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, Project project) {
    getTools(key.toString(), project).setLevel(level);
  }

  @Override
  public boolean isToolEnabled(@Nullable HighlightDisplayKey key, PsiElement element) {
    if (key == null) {
      return false;
    }
    final Tools toolState = getTools(key.toString(), element == null ? null : element.getProject());
    return toolState != null && toolState.isEnabled(element);
  }

  @Override
  public boolean isToolEnabled(@Nullable HighlightDisplayKey key) {
    return isToolEnabled(key, null);
  }

  @Override
  public boolean isExecutable(Project project) {
    initInspectionTools(project);
    for (Tools tools : myTools.values()) {
      if (tools.isEnabled()) return true;
    }
    return false;
  }

  //invoke when isChanged() == true
  @Override
  public void commit() {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    getProfileManager().updateProfile(mySource);
    mySource = null;
  }

  private void commit(@NotNull InspectionProfileImpl model) {
    setName(model.getName());
    setDescription(model.getDescription());
    setProjectLevel(model.isProjectLevel());
    myLockedProfile = model.myLockedProfile;
    myDisplayLevelMap = model.myDisplayLevelMap;
    myTools = model.myTools;
    myProfileManager = model.getProfileManager();

    InspectionProfileManager.getInstance().fireProfileChanged(model);
  }

  @Tag
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public void convert(@NotNull Element element, @NotNull Project project) {
    final Element scopes = element.getChild("scopes");
    if (scopes == null) {
      return;
    }

    initInspectionTools(project);

    for (Element scopeElement : scopes.getChildren(SCOPE)) {
      final String profile = scopeElement.getAttributeValue(ProjectInspectionProfileManagerKt.PROFILE);
      if (profile != null) {
        final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)getProfileManager().getProfile(profile);
        if (inspectionProfile != null) {
          final NamedScope scope = getProfileManager().getScopesManager().getScope(scopeElement.getAttributeValue(NAME));
          if (scope != null) {
            for (InspectionToolWrapper toolWrapper : inspectionProfile.getInspectionTools(null)) {
              final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
              try {
                InspectionToolWrapper toolWrapperCopy = copyToolSettings(toolWrapper);
                HighlightDisplayLevel errorLevel = inspectionProfile.getErrorLevel(key, null, project);
                getTools(toolWrapper.getShortName(), project).addTool(scope, toolWrapperCopy, inspectionProfile.isToolEnabled(key), errorLevel);
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

  @NotNull
  public List<ScopeToolState> getAllTools(@Nullable Project project) {
    initInspectionTools(project);

    List<ScopeToolState> result = new ArrayList<>();
    for (Tools tools : myTools.values()) {
      tools.collectTools(result);
    }
    return result;
  }

  @NotNull
  public List<ScopeToolState> getDefaultStates(@Nullable Project project) {
    initInspectionTools(project);
    final List<ScopeToolState> result = new ArrayList<>();
    for (Tools tools : myTools.values()) {
      result.add(tools.getDefaultState());
    }
    return result;
  }

  @NotNull
  public List<ScopeToolState> getNonDefaultTools(@NotNull String shortName, Project project) {
    final List<ScopeToolState> result = new ArrayList<>();
    final List<ScopeToolState> nonDefaultTools = getTools(shortName, project).getNonDefaultTools();
    if (nonDefaultTools != null) {
      result.addAll(nonDefaultTools);
    }
    return result;
  }

  public boolean isToolEnabled(@NotNull HighlightDisplayKey key, NamedScope namedScope, Project project) {
    return getTools(key.toString(), project).isEnabled(namedScope,project);
  }

  public void removeScope(@NotNull String toolId, @NotNull String scopeName, Project project) {
    getTools(toolId, project).removeScope(scopeName);
  }

  public void removeScopes(@NotNull List<String> toolIds, @NotNull String scopeName, Project project) {
    for (final String toolId : toolIds) {
      removeScope(toolId, scopeName, project);
    }
  }

  /**
   * @return null if it has no base profile
   */
  @Nullable
  private Map<String, Boolean> getDisplayLevelMap() {
    if (myBaseProfile == null) return null;
    if (myDisplayLevelMap == null) {
      synchronized (myLock) {
        if (myDisplayLevelMap == null) {
          initInspectionTools(null);
          Set<String> names = myTools.keySet();
          Map<String, Boolean> map = new THashMap<>(names.size());
          for (String toolId : names) {
            map.put(toolId, toolSettingsAreEqual(toolId, myBaseProfile, this));
          }
          myDisplayLevelMap = map;
          return map;
        }
      }
    }
    return myDisplayLevelMap;
  }

  public void profileChanged() {
    myDisplayLevelMap = null;
  }

  @NotNull
  @Transient
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, NamedScope scope, Project project) {
    final ToolsImpl tools = getTools(key.toString(), project);
    return tools != null ? tools.getLevel(scope, project) : HighlightDisplayLevel.WARNING;
  }

  public ScopeToolState addScope(@NotNull InspectionToolWrapper toolWrapper,
                                 NamedScope scope,
                                 @NotNull HighlightDisplayLevel level,
                                 boolean enabled,
                                 Project project) {
    return getTools(toolWrapper.getShortName(), project).prependTool(scope, toolWrapper, enabled, level);
  }

  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, String scopeName, Project project) {
    getTools(key.toString(), project).setLevel(level, scopeName, project);
  }

  public void setErrorLevel(@NotNull List<HighlightDisplayKey> keys, @NotNull HighlightDisplayLevel level, String scopeName, Project project) {
    for (HighlightDisplayKey key : keys) {
      setErrorLevel(key, level, scopeName, project);
    }
  }

  public ToolsImpl getTools(@NotNull String toolId, @Nullable Project project) {
    initInspectionTools(project);
    return myTools.get(toolId);
  }

  public void enableAllTools(Project project) {
    for (InspectionToolWrapper entry : getInspectionTools(null)) {
      enableTool(entry.getShortName(), project);
    }
  }

  public void disableAllTools(Project project) {
    for (InspectionToolWrapper entry : getInspectionTools(null)) {
      disableTool(entry.getShortName(), project);
    }
  }

  @Override
  @NotNull
  public String toString() {
    return mySource == null ? getName() : getName() + " (copy)";
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && ((InspectionProfileImpl)o).getProfileManager() == getProfileManager();
  }

  private static class InspectionProfileImplHolder {
    private static final InspectionProfileImpl DEFAULT_PROFILE = new InspectionProfileImpl(DEFAULT_PROFILE_NAME);
  }
}
