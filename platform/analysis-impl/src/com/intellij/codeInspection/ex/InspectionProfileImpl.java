/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.NotNullList;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
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
import java.util.function.Supplier;

public class InspectionProfileImpl extends NewInspectionProfile {
  @NonNls static final String INSPECTION_TOOL_TAG = "inspection_tool";
  @NonNls static final String CLASS_TAG = "class";
  protected static final Logger LOG = Logger.getInstance(InspectionProfileImpl.class);
  @NonNls private static final String VALID_VERSION = "1.0";
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String USED_LEVELS = "used_levels";
  @TestOnly
  public static boolean INIT_INSPECTIONS = false;
  @NotNull protected final Supplier<List<InspectionToolWrapper>> myToolSupplier;
  protected final Map<String, Element> myUninitializedSettings = new TreeMap<>();
  protected Map<String, ToolsImpl> myTools = new THashMap<>();
  protected volatile Set<String> myChangedToolNames;
  @Attribute("is_locked")
  protected boolean myLockedProfile;
  protected final InspectionProfileImpl myBaseProfile;
  private volatile String myToolShortName;
  private String[] myScopesOrder;
  private String myDescription;

  private SchemeDataHolder<? super InspectionProfileImpl> myDataHolder;

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull Supplier<List<InspectionToolWrapper>> toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager) {
    this(profileName, toolSupplier, profileManager, InspectionProfileKt.getBASE_PROFILE(), null);
  }

  public InspectionProfileImpl(@NotNull String profileName) {
    this(profileName, InspectionToolRegistrar.getInstance(), (BaseInspectionProfileManager)InspectionProfileManager.getInstance(), null, null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull Supplier<List<InspectionToolWrapper>> toolSupplier,
                               @Nullable InspectionProfileImpl baseProfile) {
    this(profileName, toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance(), baseProfile, null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull Supplier<List<InspectionToolWrapper>> toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager,
                               @Nullable InspectionProfileImpl baseProfile,
                               @Nullable SchemeDataHolder<? super InspectionProfileImpl> dataHolder) {
    super(profileName, profileManager);

    myToolSupplier = toolSupplier;
    myBaseProfile = baseProfile;
    myDataHolder = dataHolder;
    if (dataHolder != null) {
      schemeState = SchemeState.UNCHANGED;
    }
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull Supplier<List<InspectionToolWrapper>> toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager,
                               @Nullable SchemeDataHolder<? super InspectionProfileImpl> dataHolder) {
    this(profileName, toolSupplier, profileManager, InspectionProfileKt.getBASE_PROFILE(), dataHolder);
  }

  private static boolean toolSettingsAreEqual(@NotNull String toolName, @NotNull InspectionProfileImpl profile1, @NotNull InspectionProfileImpl profile2) {
    final Tools toolList1 = profile1.myTools.get(toolName);
    final Tools toolList2 = profile2.myTools.get(toolName);
    return Comparing.equal(toolList1, toolList2);
  }

  @NotNull
  protected static InspectionToolWrapper copyToolSettings(@NotNull InspectionToolWrapper toolWrapper) {
    final InspectionToolWrapper inspectionTool = toolWrapper.createCopy();
    if (toolWrapper.isInitialized()) {
      Element config = new Element("config");
      toolWrapper.getTool().writeSettings(config);
      inspectionTool.getTool().readSettings(config);
    }
    return inspectionTool;
  }

  @Override
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    Project project = element == null ? null : element.getProject();
    final ToolsImpl tools = getToolsOrNull(inspectionToolKey.toString(), project);
    HighlightDisplayLevel level = tools != null ? tools.getLevel(element) : HighlightDisplayLevel.WARNING;
    if (!getProfileManager().getOwnSeverityRegistrar().isSeverityValid(level.getSeverity().getName())) {
      level = HighlightDisplayLevel.WARNING;
      setErrorLevel(inspectionToolKey, level, project);
    }
    return level;
  }

  @Override
  public void readExternal(@NotNull Element element) {
    mySerializer.readExternal(this, element);

    final Element highlightElement = element.getChild(USED_LEVELS);
    if (highlightElement != null) {
      // from old profiles
      getProfileManager().getOwnSeverityRegistrar().readExternal(highlightElement);
    }

    String version = element.getAttributeValue(VERSION_TAG);
    if (version == null || !version.equals(VALID_VERSION)) {
      InspectionToolWrapper[] tools = getInspectionTools(null);
      for (Element toolElement : element.getChildren("inspection_tool")) {
        String toolClassName = toolElement.getAttributeValue(CLASS_TAG);
        String shortName = convertToShortName(toolClassName, tools);
        if (shortName == null) {
          continue;
        }
        toolElement.setAttribute(CLASS_TAG, shortName);
        myUninitializedSettings.put(shortName, toolElement.clone());
      }
    }
    else {
      for (Element toolElement : element.getChildren(INSPECTION_TOOL_TAG)) {
        myUninitializedSettings.put(toolElement.getAttributeValue(CLASS_TAG), toolElement.clone());
      }
    }
  }

  @Nullable
  private static String convertToShortName(@Nullable String displayName, InspectionToolWrapper[] tools) {
    if (displayName == null) return null;
    for (InspectionToolWrapper tool : tools) {
      if (displayName.equals(tool.getDisplayName())) {
        return tool.getShortName();
      }
    }
    return null;
  }

  @NotNull
  public Set<HighlightSeverity> getUsedSeverities() {
    LOG.assertTrue(wasInitialized());
    Set<HighlightSeverity> result = new THashSet<>();
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        result.add(state.getLevel().getSeverity());
      }
    }
    return result;
  }

  @Override
  @NotNull
  public Element writeScheme() {
    return writeScheme(true);
  }

  @NotNull
  public Element writeScheme(boolean setSchemeStateToUnchanged) {
    if (myDataHolder != null) {
      return myDataHolder.read();
    }

    Element element = new Element(PROFILE);
    writeExternal(element);
    if (isProjectLevel()) {
      element.setAttribute("version", "1.0");
    }
    if (isProjectLevel() && ProjectKt.isDirectoryBased(((ProjectInspectionProfileManager)getProfileManager()).getProject())) {
      return new Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(element);
    }

    if (setSchemeStateToUnchanged) {
      schemeState = SchemeState.UNCHANGED;
    }
    return element;
  }

  public void writeExternal(@NotNull Element element) {
    // must be first - compatibility
    writeVersion(element);

    mySerializer.writeExternal(this, element);

    synchronized (myLock) {
      if (!wasInitialized()) {
        for (Element el : myUninitializedSettings.values()) {
          element.addContent(el.clone());
        }
        return;
      }
    }

    Set<String> changedToolNames = getChangedToolNames();
    if (changedToolNames == null) {
      return;
    }

    List<String> allToolNames = new ArrayList<>(myTools.keySet());
    allToolNames.addAll(myUninitializedSettings.keySet());
    allToolNames.sort(null);
    for (String toolName : allToolNames) {
      Element toolElement = myUninitializedSettings.get(toolName);
      if (toolElement != null) {
        element.addContent(toolElement.clone());
        continue;
      }

      if (!myLockedProfile && !changedToolNames.contains(toolName)) {
        markSettingsMerged(toolName, element);
        continue;
      }

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
    getPathMacroManager().collapsePaths(element);
  }

  protected static void writeVersion(@NotNull Element element) {
    element.setAttribute(VERSION_TAG, VALID_VERSION);
  }

  private void markSettingsMerged(@NotNull String toolName, @NotNull Element element) {
    //add marker if already merged but result is now default (-> empty node)
    String mergedName = InspectionElementsMergerBase.getMergedMarkerName(toolName);
    if (!myUninitializedSettings.containsKey(mergedName)) {
      InspectionElementsMergerBase merger = getMerger(toolName);
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
                                          @NotNull Set<InspectionToolWrapper<?, ?>> dependentEntries,
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
  public InspectionToolWrapper getInspectionTool(@NotNull String shortName, @Nullable PsiElement element) {
    final Tools toolList = getToolsOrNull(shortName, element == null ? null : element.getProject());
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

  public void modifyProfile(@NotNull Consumer<InspectionProfileModifiableModel> modelConsumer) {
    InspectionProfileModifiableModelKt.edit(this, it -> {
      modelConsumer.consume(it);
      return null;
    });
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
    final ToolsImpl tools = getToolsOrNull(shortName, project);
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

  @Nullable
  @Override
  public String getSingleTool() {
    return myToolShortName;
  }

  public void setSingleTool(@NotNull final String toolShortName) {
    myToolShortName = toolShortName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return getName();
  }

  public void scopesChanged() {
    if (!wasInitialized()) {
      return;
    }

    for (ToolsImpl tools : myTools.values()) {
      tools.scopesChanged();
    }

    getProfileManager().fireProfileChanged(this);
  }

  @Transient
  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  public void lockProfile(boolean isLocked) {
    myLockedProfile = isLocked;
    schemeState = SchemeState.POSSIBLY_CHANGED;
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

  public void disableToolByDefault(@NotNull Collection<String> toolShortNames, @Nullable Project project) {
    for (String toolId : toolShortNames) {
      getTools(toolId, project).setDefaultEnabled(false);
    }
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @NotNull
  public ScopeToolState getToolDefaultState(@NotNull String toolShortName, @Nullable Project project) {
    return getTools(toolShortName, project).getDefaultState();
  }

  public void enableToolsByDefault(@NotNull List<String> toolShortNames, Project project) {
    for (final String shortName : toolShortNames) {
      getTools(shortName, project).setDefaultEnabled(true);
    }
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @NotNull
  protected List<InspectionToolWrapper> createTools(@Nullable Project project) {
    return myToolSupplier.get();
  }

  @Override
  protected void initialize(@Nullable Project project) {
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

    DFSTBuilder<String> builder = new DFSTBuilder<>(GraphGenerator.generate(new InboundSemiGraph<String>() {
      @Override
      public Collection<String> getNodes() {
        return dependencies.keySet();
      }

      @Override
      public Iterator<String> getIn(String n) {
        return dependencies.get(n).iterator();
      }
    }));
    if (builder.isAcyclic()) {
      myScopesOrder = ArrayUtil.toStringArray(builder.getSortedNodes());
    }

    copyToolsConfigurations(project);

    initialized = true;
    if (dataHolder != null) {
      // should be only after set myInitialized
      dataHolder.updateDigest(this);
    }
  }

  protected void copyToolsConfigurations(@Nullable Project project) {
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

    HighlightDisplayLevel baseLevel = myBaseProfile != null && myBaseProfile.getToolsOrNull(shortName, project) != null
                                   ? myBaseProfile.getErrorLevel(key, project)
                                   : HighlightDisplayLevel.DO_NOT_SHOW;
    HighlightDisplayLevel defaultLevel = toolWrapper.getDefaultLevel();
    HighlightDisplayLevel level = baseLevel.getSeverity().compareTo(defaultLevel.getSeverity()) > 0 ? baseLevel : defaultLevel;
    boolean enabled = myBaseProfile != null ? myBaseProfile.isToolEnabled(key) : toolWrapper.isEnabledByDefault();
    final ToolsImpl toolsList = new ToolsImpl(toolWrapper, level, !myLockedProfile && enabled, enabled);
    final Element element = myUninitializedSettings.remove(shortName);
    try {
      if (element != null) {
        getPathMacroManager().expandPaths(element);
        toolsList.readExternal(element, getProfileManager(), dependencies);
      }
      else if (!myUninitializedSettings.containsKey(InspectionElementsMergerBase.getMergedMarkerName(shortName))) {
        final InspectionElementsMergerBase merger = getMerger(shortName);
        Element merged = merger == null ? null : merger.merge(myUninitializedSettings);
        if (merged != null) {
          getPathMacroManager().expandPaths(merged);
          toolsList.readExternal(merged, getProfileManager(), dependencies);
        }
        else if (isProfileLocked()) {
          // https://youtrack.jetbrains.com/issue/IDEA-158936
          toolsList.setEnabled(false);
          if (toolsList.getNonDefaultTools() == null) {
            toolsList.getDefaultState().setEnabled(false);
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
      @NotNull
      @Override
      public String getMergedToolName() {
        return merger.getMergedToolName();
      }

      @NotNull
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
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  private HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, @Nullable Project project) {
    return getTools(key.toString(), project).getLevel();
  }

  @NotNull
  @TestOnly
  public InspectionProfileModifiableModel getModifiableModel() {
    return new InspectionProfileModifiableModel(this);
  }

  public void cleanup(@NotNull Project project) {
    if (!wasInitialized()) {
      return;
    }

    for (ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        toolList.cleanupTools(project);
      }
    }
  }

  public void enableTool(@NotNull String toolShortName, @Nullable Project project) {
    setToolEnabled(toolShortName, true, project);
  }

  public void enableTool(@NotNull String inspectionTool, @NotNull NamedScope namedScope, Project project) {
    getTools(inspectionTool, project).enableTool(namedScope, project);
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void enableTools(@NotNull List<String> inspectionTools, @NotNull NamedScope namedScope, Project project) {
    for (String inspectionTool : inspectionTools) {
      enableTool(inspectionTool, namedScope, project);
    }
  }

  public void disableTools(@NotNull List<String> inspectionTools, NamedScope namedScope, @NotNull Project project) {
    for (String inspectionTool : inspectionTools) {
      getTools(inspectionTool, project).disableTool(namedScope, project);
    }
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, Project project) {
    getTools(key.toString(), project).setLevel(level);
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @Override
  public boolean isToolEnabled(@Nullable HighlightDisplayKey key, @Nullable PsiElement element) {
    if (key == null) {
      return false;
    }
    final Tools toolState = getToolsOrNull(key.toString(), element == null ? null : element.getProject());
    return toolState != null && toolState.isEnabled(element);
  }

  @Override
  public boolean isExecutable(@Nullable Project project) {
    initInspectionTools(project);
    for (Tools tools : myTools.values()) {
      if (tools.isEnabled()) return true;
    }
    return false;
  }

  @Tag
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    myDescription = StringUtil.nullize(description);
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void convert(@NotNull Element element, @NotNull Project project) {
    final Element scopes = element.getChild("scopes");
    if (scopes == null) {
      return;
    }

    initInspectionTools(project);

    for (Element scopeElement : scopes.getChildren(SCOPE)) {
      final String profile = scopeElement.getAttributeValue(PROFILE);
      InspectionProfileImpl inspectionProfile = profile == null ? null : getProfileManager().getProfile(profile);
      NamedScope scope = inspectionProfile == null ? null : getProfileManager().getScopesManager().getScope(scopeElement.getAttributeValue(NAME));
      if (scope == null) {
        continue;
      }

      for (InspectionToolWrapper toolWrapper : inspectionProfile.getInspectionTools(null)) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
        try {
          InspectionToolWrapper toolWrapperCopy = copyToolSettings(toolWrapper);
          HighlightDisplayLevel errorLevel = inspectionProfile.getErrorLevel(key, null, project);
          getTools(toolWrapper.getShortName(), project)
            .addTool(scope, toolWrapperCopy, inspectionProfile.isToolEnabled(key), errorLevel);
        }
        catch (Exception e) {
          LOG.error(e);
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
  public List<ScopeToolState> getAllTools() {
    initInspectionTools();

    List<ScopeToolState> result = new NotNullList<>();
    for (Tools tools : myTools.values()) {
      tools.collectTools(result);
    }
    return result;
  }

  @NotNull
  public List<ScopeToolState> getDefaultStates(@Nullable Project project) {
    initInspectionTools(project);
    List<ScopeToolState> result = new ArrayList<>();
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

  public void removeScope(@NotNull String toolShortName, @NotNull String scopeName, Project project) {
    getTools(toolShortName, project).removeScope(scopeName);
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void removeScopes(@NotNull List<String> shortNames, @NotNull String scopeName, Project project) {
    for (final String shortName : shortNames) {
      removeScope(shortName, scopeName, project);
    }
  }

  /**
   * @return null if it has no base profile
   */
  @Nullable
  private Set<String> getChangedToolNames() {
    if (myBaseProfile == null) return null;
    if (myChangedToolNames == null) {
      synchronized (myLock) {
        if (myChangedToolNames == null) {
          initInspectionTools(null);
          Set<String> names = myTools.keySet();
          Set<String> map = new THashSet<>(names.size());
          for (String toolId : names) {
            if (!toolSettingsAreEqual(toolId, myBaseProfile, this)) {
              map.add(toolId);
            }
          }
          myChangedToolNames = map;
          return map;
        }
      }
    }
    return myChangedToolNames;
  }

  public void profileChanged() {
    myChangedToolNames = null;
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @NotNull
  @Transient
  public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, NamedScope scope, Project project) {
    final ToolsImpl tools = getToolsOrNull(key.toString(), project);
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
    schemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void setErrorLevel(@NotNull List<HighlightDisplayKey> keys, @NotNull HighlightDisplayLevel level, String scopeName, Project project) {
    for (HighlightDisplayKey key : keys) {
      setErrorLevel(key, level, scopeName, project);
    }
  }

  @Override
  @Nullable
  public ToolsImpl getToolsOrNull(@NotNull String name, @Nullable Project project) {
    initInspectionTools(project);
    return myTools.get(name);
  }

  public void enableAllTools(Project project) {
    for (InspectionToolWrapper entry : getInspectionTools(null)) {
      enableTool(entry.getShortName(), project);
    }
  }
}
