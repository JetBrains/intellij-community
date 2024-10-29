// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.configurationStore.SchemeDataHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.Strings;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectBasedInspectionProfileManager;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.NotNullList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.NotNullLazyValue.lazy;

public class InspectionProfileImpl extends NewInspectionProfile {
  public static final NotNullLazyValue<InspectionProfileImpl> BASE_PROFILE = lazy(() -> new InspectionProfileImpl(DEFAULT_PROFILE_NAME));

  @SuppressWarnings("StaticNonFinalField") @TestOnly
  public static boolean INIT_INSPECTIONS;

  static final String INSPECTION_TOOL_TAG = "inspection_tool";
  static final String CLASS_TAG = "class";

  protected static final Logger LOG = Logger.getInstance(InspectionProfileImpl.class);

  private static final String VALID_VERSION = "1.0";
  private static final String VERSION_TAG = "version";
  private static final String USED_LEVELS = "used_levels";

  protected final @NotNull InspectionToolsSupplier myToolSupplier;
  protected final Map<String, Element> myUninitializedSettings = new TreeMap<>(); // accessed in EDT
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  protected Map<String, ToolsImpl> myTools = ConcurrentCollectionFactory.createConcurrentMap(); // `addTool` is called concurrently
  protected volatile Set<String> myChangedToolNames;
  @Attribute("is_locked")
  protected boolean myLockedProfile;
  protected final InspectionProfileImpl myBaseProfile;

  private final Object myLock = new Object();
  private BaseInspectionProfileManager myProfileManager;
  private volatile boolean myInitialized;
  private boolean myProjectLevel;
  private volatile String myToolShortName;
  private List<String> myScopeOrder = new ArrayList<>();
  private @NlsContexts.DetailedDescription String myDescription;
  private @Nullable SchemeState mySchemeState;
  private Disposable myDisposable;
  private SchemeDataHolder<? super InspectionProfileImpl> myDataHolder;

  public InspectionProfileImpl(@NotNull String profileName) {
    this(profileName, InspectionToolRegistrar.getInstance(), (BaseInspectionProfileManager)InspectionProfileManager.getInstance(), null, null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolsSupplier toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager) {
    this(profileName, toolSupplier, profileManager, BASE_PROFILE.get(), null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolsSupplier toolSupplier,
                               @Nullable InspectionProfileImpl baseProfile) {
    this(profileName, toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance(), baseProfile, null);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolsSupplier toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager,
                               @Nullable SchemeDataHolder<? super InspectionProfileImpl> dataHolder) {
    this(profileName, toolSupplier, profileManager, BASE_PROFILE.get(), dataHolder);
  }

  public InspectionProfileImpl(@NotNull String profileName,
                               @NotNull InspectionToolsSupplier toolSupplier,
                               @NotNull BaseInspectionProfileManager profileManager,
                               @Nullable InspectionProfileImpl baseProfile,
                               @Nullable SchemeDataHolder<? super InspectionProfileImpl> dataHolder) {
    super(profileName);
    myProfileManager = profileManager;
    myToolSupplier = toolSupplier;
    myBaseProfile = baseProfile;
    myDataHolder = dataHolder;
    if (dataHolder != null) {
      mySchemeState = SchemeState.UNCHANGED;
    }
  }

  private @Nullable Project getDefaultProject() {
    return myProfileManager instanceof ProjectBasedInspectionProfileManager pm ? pm.getProject() : null;
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void initInspectionTools() {
    initInspectionTools(null);
  }

  public void initInspectionTools(@Nullable Project project) {
    if (!myInitialized && forceInitInspectionTools()) {
      if (project == null) project = getDefaultProject();
      synchronized (myLock) {
        if (!myInitialized) {
          initialize(project);
        }
      }
    }
  }

  @SuppressWarnings("TestOnlyProblems")
  protected boolean forceInitInspectionTools() {
    return !ApplicationManager.getApplication().isUnitTestMode() || INIT_INSPECTIONS;
  }

  public final void copyFrom(@NotNull InspectionProfileImpl profile) {
    var element = profile.writeScheme();
    if ("component".equals(element.getName())) {
      element = element.getChild("profile");
    }
    readExternal(element);
  }

  @Transient
  public final @NotNull BaseInspectionProfileManager getProfileManager() {
    return myProfileManager;
  }

  public final void setProfileManager(@NotNull BaseInspectionProfileManager profileManager) {
    myProfileManager = profileManager;
  }

  @Transient
  public final boolean isProjectLevel() {
    return myProjectLevel;
  }

  public final void setProjectLevel(boolean projectLevel) {
    myProjectLevel = projectLevel;
  }

  public static @NotNull InspectionToolWrapper<?, ?> copyToolSettings(@NotNull InspectionToolWrapper<?,?> toolWrapper) {
    InspectionToolWrapper<?, ?> inspectionTool = toolWrapper.createCopy();
    if (toolWrapper.isInitialized()) {
      Element config = new Element("config");
      ScopeToolState.tryWriteSettings(toolWrapper.getTool(), config);
      ScopeToolState.tryReadSettings(inspectionTool.getTool(), config);
    }
    return inspectionTool;
  }

  @Override
  public @NotNull HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
    Project project = element == null ? null : element.getProject();
    ToolsImpl tools = getToolsOrNull(inspectionToolKey.getShortName(), project);
    HighlightDisplayLevel level = tools != null ? tools.getLevel(element) : HighlightDisplayLevel.WARNING;
    if (!myProfileManager.getSeverityRegistrar().isSeverityValid(level.getSeverity().getName())) {
      level = HighlightDisplayLevel.WARNING;
      if (tools != null) {
        setErrorLevel(inspectionToolKey, level, project);
      }
    }
    return level;
  }

  public void readExternal(@NotNull Element element) {
    mySerializer.readExternal(this, element);

    Element highlightElement = element.getChild(USED_LEVELS);
    if (highlightElement != null) {
      // from old profiles
      myProfileManager.getSeverityRegistrar().readExternal(highlightElement);
    }

    String version = element.getAttributeValue(VERSION_TAG);
    if (!VALID_VERSION.equals(version)) {
      List<InspectionToolWrapper<?, ?>> tools = getInspectionTools(null);
      for (Element toolElement : element.getChildren("inspection_tool")) {
        String toolClassName = toolElement.getAttributeValue(CLASS_TAG);
        String shortName = convertToShortName(toolClassName, tools);
        if (shortName == null) {
          continue;
        }
        toolElement.setAttribute(CLASS_TAG, shortName);
        myUninitializedSettings.put(shortName, JDOMUtil.internElement(toolElement));
      }
    }
    else {
      for (Element toolElement : element.getChildren(INSPECTION_TOOL_TAG)) {
        myUninitializedSettings.put(toolElement.getAttributeValue(CLASS_TAG), JDOMUtil.internElement(toolElement));
      }
    }
  }

  private static @Nullable String convertToShortName(@Nullable String displayName, @NotNull List<? extends InspectionToolWrapper<?, ?>> tools) {
    if (displayName == null) return null;
    for (InspectionToolWrapper<?, ?> tool : tools) {
      if (displayName.equals(tool.getDisplayName())) {
        return tool.getShortName();
      }
    }
    return null;
  }

  public @NotNull Set<HighlightSeverity> getUsedSeverities() {
    LOG.assertTrue(myInitialized);
    Set<HighlightSeverity> result = new HashSet<>();
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        result.add(state.getLevel().getSeverity());
      }
    }
    return result;
  }

  @Override
  public @NotNull Element writeScheme() {
    if (myDataHolder != null) {
      return myDataHolder.read();
    }

    Element element = new Element(PROFILE);
    writeExternal(element);
    if (myProjectLevel) {
      element.setAttribute("version", "1.0");
    }
    if (myProjectLevel && ProjectKt.isDirectoryBased(((ProjectBasedInspectionProfileManager)myProfileManager).getProject())) {
      return new Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(element);
    }

    mySchemeState = SchemeState.UNCHANGED;
    return element;
  }

  @Override
  public @Nullable SchemeState getSchemeState() {
    return mySchemeState;
  }

  public void writeExternal(@NotNull Element element) {
    // must be first - compatibility
    writeVersion(element);

    mySerializer.writeExternal(this, element);

    synchronized (myLock) {
      if (!myInitialized) {
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
    //add marker if already merged, but the result is now default (-> empty node)
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
    InspectionElementsMergerBase merger = getMerger(toolName);
    return merger != null && merger.areSettingsMerged(myUninitializedSettings, inspectionElement);
  }

  private PathMacroManager getPathMacroManager() {
    return PathMacroManager.getInstance(
      myProfileManager instanceof ProjectBasedInspectionProfileManager pm ? pm.getProject() : ApplicationManager.getApplication());
  }

  public void collectDependentInspections(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                          @NotNull Set<? super InspectionToolWrapper<?, ?>> dependentEntries,
                                          Project project) {
    String mainToolId = toolWrapper.getMainToolId();

    if (mainToolId != null) {
      InspectionToolWrapper<?,?> dependentEntryWrapper = getInspectionTool(mainToolId, project);
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
  public @Nullable InspectionToolWrapper<?, ?> getInspectionTool(@NotNull String shortName, @Nullable PsiElement element) {
    Tools toolList = getToolsOrNull(shortName, element == null ? null : element.getProject());
    return toolList == null ? null : toolList.getInspectionTool(element);
  }

  @Override
  public @Nullable InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element) {
    InspectionToolWrapper<?, ?> tool = getInspectionTool(shortName, element);
    return tool == null ? null : tool.getTool();
  }

  @Override
  public <T extends InspectionProfileEntry> T getUnwrappedTool(@NotNull Key<T> shortNameKey, @NotNull PsiElement element) {
    @SuppressWarnings("unchecked") T tool = (T)getUnwrappedTool(shortNameKey.toString(), element);
    return tool;
  }

  public void modifyProfile(@NotNull Consumer<? super InspectionProfileModifiableModel> modelConsumer) {
    InspectionProfileModifiableModelKt.edit(this, it -> {
      modelConsumer.consume(it);
      return null;
    });
  }

  @Override
  public <T extends InspectionProfileEntry> void modifyToolSettings(@NotNull Key<T> shortNameKey,
                                                                    @NotNull PsiElement psiElement,
                                                                    @NotNull Consumer<? super T> toolConsumer) {
    modifyProfile(model -> {
      @SuppressWarnings("unchecked") T tool = (T)model.getUnwrappedTool(shortNameKey.toString(), psiElement);
      toolConsumer.consume(tool);
    });
  }

  /**
   * Warning: Usage of this method is discouraged, as if separate tool options are defined for different scopes, it just returns
   * the options for the first scope which may lead to unexpected results. Consider using {@link #getInspectionTool(String, PsiElement)} instead.
   *
   * @param shortName an inspection short name
   * @param project   a project
   * @return an InspectionToolWrapper associated with this tool.
   */
  @Override
  public @Nullable InspectionToolWrapper<?, ?> getInspectionTool(@NotNull String shortName, @Nullable Project project) {
    ToolsImpl tools = getToolsOrNull(shortName, project);
    return tools == null ? null : tools.getTool();
  }

  public InspectionToolWrapper<?,?> getToolById(@NotNull String id, @NotNull PsiElement element) {
    initInspectionTools();
    for (Tools toolList : myTools.values()) {
      InspectionToolWrapper<?,?> tool = toolList.getInspectionTool(element);
      if (id.equals(tool.getID())) return tool;
    }
    return null;
  }

  public @NotNull List<InspectionToolWrapper<?, ?>> findToolsById(@NotNull String id, @NotNull PsiElement element) {
    initInspectionTools();
    List<InspectionToolWrapper<?, ?>> result = null;
    for (Tools toolList : myTools.values()) {
      InspectionToolWrapper<?,?> tool = toolList.getInspectionTool(element);
      if (id.equals(tool.getID())) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(tool);
      }
    }
    return ContainerUtil.notNullize(result);
  }

  @Override
  public @Nullable String getSingleTool() {
    return myToolShortName;
  }

  public void setSingleTool(@NotNull String toolShortName) {
    myToolShortName = toolShortName;
  }

  @Override
  @SuppressWarnings("HardCodedStringLiteral")
  public @NotNull String getDisplayName() {
    return getName();
  }

  public void scopesChanged() {
    if (!myInitialized) {
      return;
    }

    for (ToolsImpl tools : myTools.values()) {
      tools.scopesChanged();
    }

    myProfileManager.fireProfileChanged(this);
  }

  @Transient
  public boolean isProfileLocked() {
    return myLockedProfile;
  }

  public void lockProfile(boolean isLocked) {
    myLockedProfile = isLocked;
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public @NotNull InspectionToolsSupplier getInspectionToolsSupplier() {
    return myToolSupplier;
  }

  @Override
  public @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(@Nullable PsiElement element) {
    initInspectionTools();
    List<InspectionToolWrapper<?, ?>> result = new ArrayList<>(myTools.size());
    for (Tools toolList : myTools.values()) {
      result.add(toolList.getInspectionTool(element));
    }
    return result;
  }

  @Override
  public @NotNull List<Tools> getAllEnabledInspectionTools(Project project) {
    initInspectionTools();

    List<Tools> result = new ArrayList<>();
    Set<String> projectTypes = ProjectTypeService.getProjectTypeIds(project);
    boolean isTests = ApplicationManager.getApplication().isUnitTestMode();

    for (ToolsImpl toolList : myTools.values()) {
      if (toolList.isEnabled()) {
        InspectionToolWrapper<?, ?> toolWrapper = toolList.getTool();
        if (!isTests && !toolWrapper.isApplicable(projectTypes)) {
          continue;
        }

        result.add(toolList);
      }
    }
    return result;
  }

  public void disableToolByDefault(@NotNull Collection<String> toolShortNames, @Nullable Project project) {
    for (String toolId : toolShortNames) {
      getTools(toolId, project).setDefaultEnabled(false);
    }
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public @NotNull ScopeToolState getToolDefaultState(@NotNull String toolShortName, @Nullable Project project) {
    return getTools(toolShortName, project).getDefaultState();
  }

  public void enableToolsByDefault(@NotNull List<String> toolShortNames, Project project) {
    for (String shortName : toolShortNames) {
      getTools(shortName, project).setDefaultEnabled(true);
    }
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  protected @NotNull List<InspectionToolWrapper<?, ?>> createTools(@Nullable Project project) {
    return myToolSupplier.createTools();
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
      myBaseProfile.initInspectionTools();
    }

    List<InspectionToolWrapper<?, ?>> tools;
    try {
      tools = createTools(project);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }

    Map<String, List<String>> dependencies = new HashMap<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : tools) {
      addTool(project, toolWrapper, dependencies);

      if (toolWrapper instanceof LocalInspectionToolWrapper &&
          ((LocalInspectionToolWrapper)toolWrapper).isDynamicGroup() &&
          // some settings were read for the tool, so it must be initialized, otherwise no dynamic tools are expected
          toolWrapper.isInitialized()) {
        ToolsImpl parent = myTools.get(toolWrapper.getShortName());
        if (parent.isEnabled()) {
          var children = ((DynamicGroupTool)toolWrapper.getTool()).getChildren();
          var childNames = children.stream().map(LocalInspectionToolWrapper::getShortName).collect(Collectors.toSet());
          //noinspection SSBasedInspection
          if (tools.stream().noneMatch(tool -> childNames.contains(tool.getShortName()))) {
            boolean isLocked = myLockedProfile;
            myLockedProfile = false;
            for (LocalInspectionToolWrapper wrapper : children) {
              addTool(project, wrapper, dependencies);
              String shortName = wrapper.getShortName();
              if (InspectionElementsMerger.getMerger(shortName) == null) {
                InspectionElementsMerger.addMerger(shortName, new MyInspectionElementsMerger(shortName, wrapper));
              }
            }
            myLockedProfile = isLocked;
          }
        }
      }
    }

    // initialize is invoked in synchronized block, so we can publish the new value of myDisposable here
    myDisposable = project != null ? Disposer.newDisposable(project, "[" + getClass().getSimpleName() + "] " + getName()) : null;
    myToolSupplier.addListener(new InspectionToolsSupplier.Listener() {
      @Override
      public void toolAdded(@NotNull InspectionToolWrapper<?,?> inspectionTool) {
        addTool(project, inspectionTool, null);
        myProfileManager.fireProfileChanged(InspectionProfileImpl.this);
      }

      @Override
      public void toolRemoved(@NotNull InspectionToolWrapper<?,?> inspectionTool) {
        removeTool(inspectionTool);
        myProfileManager.fireProfileChanged(InspectionProfileImpl.this);
      }
    }, myDisposable);

    copyToolsConfigurations(project);

    myInitialized = true;
    if (dataHolder != null) {
      // should be only after set myInitialized
      dataHolder.updateDigest(this);
    }
  }

  protected void copyToolsConfigurations(@Nullable Project project) {
  }

  public final void addTool(@Nullable Project project, @NotNull InspectionToolWrapper<?, ?> toolWrapper, @Nullable Map<? super String, List<String>> dependencies) {
    String shortName = toolWrapper.getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      InspectionEP extension = toolWrapper.getExtension();
      Computable<String> computable = extension == null || extension.displayName == null && extension.key == null
                                      ? new Computable.PredefinedValueComputable<>(toolWrapper.getDisplayName())
                                      : extension::getDisplayName;
      if (toolWrapper instanceof LocalInspectionToolWrapper) {
        key = HighlightDisplayKey.register(shortName, computable, toolWrapper.getID(),
                                           ((LocalInspectionToolWrapper)toolWrapper).getAlternativeID());
      }
      else {
        key = HighlightDisplayKey.register(shortName, computable, shortName);
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
    boolean enabled = myBaseProfile != null && myBaseProfile.getToolsOrNull(shortName, project) != null ? myBaseProfile.isToolEnabled(key) : toolWrapper.isEnabledByDefault();
    ToolsImpl toolsList = new ToolsImpl(toolWrapper, level, !myLockedProfile && enabled, enabled);
    Element element = myUninitializedSettings.remove(shortName);
    try {
      if (element != null) {
        element = element.clone();
        getPathMacroManager().expandPaths(element);
        toolsList.readExternal(element, myProfileManager, dependencies);
      }
      else if (!myUninitializedSettings.containsKey(InspectionElementsMergerBase.getMergedMarkerName(shortName))) {
        InspectionElementsMergerBase merger = getMerger(shortName);
        Element merged = merger == null ? null : merger.merge(myUninitializedSettings);
        if (merged != null) {
          getPathMacroManager().expandPaths(merged);
          toolsList.readExternal(merged, myProfileManager, dependencies);
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

  public void removeTool(@NotNull InspectionToolWrapper<?, ?> inspectionTool) {
    String shortName = inspectionTool.getShortName();
    myTools.remove(shortName);
  }

  private static @Nullable InspectionElementsMergerBase getMerger(@NotNull String shortName) {
    InspectionElementsMerger merger = InspectionElementsMerger.getMerger(shortName);
    if (merger instanceof InspectionElementsMergerBase) {
      return (InspectionElementsMergerBase)merger;
    }
    return merger == null ? null : new InspectionElementsMergerBase() {
      @Override
      public @NotNull String getMergedToolName() {
        return merger.getMergedToolName();
      }

      @Override
      public String @NotNull [] getSourceToolNames() {
        return merger.getSourceToolNames();
      }
    };
  }

  public List<String> getScopesOrder() {
    return myScopeOrder;
  }

  public void setScopesOrder(List<String> scopeOrder) {
    myScopeOrder = scopeOrder;
    mySchemeState = SchemeState.POSSIBLY_CHANGED;

    for (ToolsImpl tools : myTools.values()) {
      tools.changeToolsOrder(myScopeOrder);
    }
  }

  private @NotNull HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, @Nullable Project project) {
    return getTools(key.getShortName(), project).getLevel();
  }

  @TestOnly
  public @NotNull InspectionProfileModifiableModel getModifiableModel() {
    return new InspectionProfileModifiableModel(this);
  }

  public void cleanup(@Nullable Project project) {
    if (!myInitialized) {
      return;
    }

    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }

    if (project != null) {
      for (ToolsImpl toolList : myTools.values()) {
        if (toolList.isEnabled()) {
          toolList.cleanupTools(project);
        }
      }
    }
  }

  public void enableTool(@NotNull String toolShortName, @Nullable Project project) {
    setToolEnabled(toolShortName, true, project);
  }

  public void enableTool(@NotNull String toolShortName, @NotNull NamedScope namedScope, @NotNull Project project) {
    getTools(toolShortName, project).enableTool(namedScope, project);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void enableTools(@NotNull List<String> toolShortNames, @NotNull NamedScope namedScope, @NotNull Project project) {
    for (String toolShortName : toolShortNames) {
      enableTool(toolShortName, namedScope, project);
    }
  }

  public void disableTools(@NotNull List<String> toolShortNames, @NotNull NamedScope namedScope, @NotNull Project project) {
    for (String toolShortName : toolShortNames) {
      getTools(toolShortName, project).disableTool(namedScope, project);
    }
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, Project project) {
    getTools(key.getShortName(), project).setLevel(level);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @Override
  public boolean isToolEnabled(@Nullable HighlightDisplayKey key, @Nullable PsiElement element) {
    if (key == null) {
      return false;
    }
    Tools toolState = getToolsOrNull(key.getShortName(), element == null ? null : element.getProject());
    return toolState != null && toolState.isEnabled(element);
  }

  @Override
  public @Nullable TextAttributesKey getEditorAttributes(@NotNull String shortName, @Nullable PsiElement element) {
    ToolsImpl tools = getToolsOrNull(shortName, element != null ? element.getProject() : null);
    return tools != null ? tools.getAttributesKey(element) : null;
  }

  public void setEditorAttributesKey(@NotNull String shortName, @Nullable String keyName, String scopeName, @Nullable Project project) {
    final ToolsImpl tools = getTools(shortName, project);
    final var level = tools.getLevel(scopeName, project);
    if (keyName == null) {
      keyName = SeverityRegistrar.getSeverityRegistrar(project).getHighlightInfoTypeBySeverity(level.getSeverity()).getAttributesKey().getExternalName();
    }
    String attributes = tools.getDefaultState().getTool().getDefaultEditorAttributes();
    tools.setEditorAttributesKey(Objects.equals(attributes, keyName) ? null : keyName, scopeName);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }
  
  @Override
  public boolean isExecutable(@Nullable Project project) {
    initInspectionTools();
    for (Tools tools : myTools.values()) {
      if (tools.isEnabled()) return true;
    }
    return false;
  }

  @Tag
  public @NlsContexts.DetailedDescription String getDescription() {
    return myDescription;
  }

  public void setDescription(@NlsContexts.DetailedDescription @Nullable String description) {
    myDescription = Strings.nullize(description);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void resetToBase(@NotNull String toolId, NamedScope scope, @NotNull Project project) {
    ToolsImpl tools = myBaseProfile.getToolsOrNull(toolId, project);
    if (tools == null) return;
    InspectionToolWrapper<?, ?> baseDefaultWrapper = tools.getDefaultState().getTool();
    ScopeToolState state = myTools.get(toolId).getTools().stream().filter(s -> scope == s.getScope(project)).findFirst().orElseThrow(IllegalStateException::new);
    state.setTool(copyToolSettings(baseDefaultWrapper));
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void convert(@NotNull Element element, @NotNull Project project) {
    Element scopes = element.getChild("scopes");
    if (scopes == null) return;

    initInspectionTools();

    for (Element scopeElement : scopes.getChildren(SCOPE)) {
      String profile = scopeElement.getAttributeValue(PROFILE);
      InspectionProfileImpl inspectionProfile = profile == null ? null : myProfileManager.getProfile(profile);
      NamedScope scope = null;
      if (inspectionProfile != null) {
        NamedScopesHolder scopesManager = myProfileManager.getScopesManager();
        if (scopesManager != null) {
          scope = scopesManager.getScope(scopeElement.getAttributeValue(NAME));
        }
      }
      if (scope == null) {
        continue;
      }

      for (InspectionToolWrapper<?, ?> toolWrapper : inspectionProfile.getInspectionTools(null)) {
        HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
        try {
          InspectionToolWrapper<?, ?> toolWrapperCopy = copyToolSettings(toolWrapper);
          HighlightDisplayLevel errorLevel = inspectionProfile.getErrorLevel(Objects.requireNonNull(key), null, project);
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
      ScopeToolState toolState = tools.getDefaultState();
      List<ScopeToolState> nonDefaultTools = tools.getNonDefaultTools();
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

  public @NotNull List<ScopeToolState> getAllTools() {
    initInspectionTools();

    List<ScopeToolState> result = new NotNullList<>();
    for (Tools tools : myTools.values()) {
      tools.collectTools(result);
    }
    return result;
  }

  public @NotNull List<ScopeToolState> getDefaultStates(@SuppressWarnings("unused") @Nullable Project project) {
    initInspectionTools();
    List<ScopeToolState> result = new ArrayList<>();
    for (Tools tools : myTools.values()) {
      result.add(tools.getDefaultState());
    }
    return result;
  }

  public @NotNull List<ScopeToolState> getNonDefaultTools(@NotNull String shortName, Project project) {
    List<ScopeToolState> result = new ArrayList<>();
    List<ScopeToolState> nonDefaultTools = getTools(shortName, project).getNonDefaultTools();
    if (nonDefaultTools != null) {
      result.addAll(nonDefaultTools);
    }
    return result;
  }

  public boolean isToolEnabled(@NotNull HighlightDisplayKey key, @Nullable NamedScope namedScope, @NotNull Project project) {
    return getTools(key.getShortName(), project).isEnabled(namedScope,project);
  }

  public void removeScope(@NotNull String toolShortName, @NotNull String scopeName, @NotNull Project project) {
    getTools(toolShortName, project).removeScope(scopeName);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void removeScopes(@NotNull List<String> shortNames, @NotNull String scopeName, @NotNull Project project) {
    for (String shortName : shortNames) {
      removeScope(shortName, scopeName, project);
    }
  }

  /**
   * @return null if it has no base profile
   */
  private @Nullable Set<String> getChangedToolNames() {
    if (myBaseProfile == null) {
      return null;
    }
    if (myChangedToolNames == null) {
      synchronized (myLock) {
        if (myChangedToolNames == null) {
          initInspectionTools(null);
          Set<String> names = myTools.keySet();
          Set<String> map = new HashSet<>(names.size());
          for (String toolId : names) {
            Tools toolList1 = myBaseProfile.myTools.get(toolId);
            Tools toolList2 = myTools.get(toolId);
            if (!Comparing.equal(toolList1, toolList2)) {
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
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  @Transient
  public @NotNull HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, @Nullable NamedScope scope, @NotNull Project project) {
    ToolsImpl tools = getToolsOrNull(key.getShortName(), project);
    return tools != null ? tools.getLevel(scope, project) : HighlightDisplayLevel.WARNING;
  }

  @Transient
  public @Nullable TextAttributesKey getEditorAttributesKey(@NotNull HighlightDisplayKey key, @Nullable NamedScope scope, @NotNull Project project) {
    ToolsImpl tools = getToolsOrNull(key.getShortName(), project);
    return tools != null ? tools.getEditorAttributesKey(scope, project) : null;
  }

  public ScopeToolState addScope(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                 @NotNull NamedScope scope,
                                 @NotNull HighlightDisplayLevel level,
                                 boolean enabled,
                                 @Nullable Project project) {
    return getTools(toolWrapper.getShortName(), project).prependTool(scope, toolWrapper, enabled, level);
  }

  public void setErrorLevel(@NotNull HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, @Nullable String scopeName, @NotNull Project project) {
    getTools(key.getShortName(), project).setLevel(level, scopeName, project);
    mySchemeState = SchemeState.POSSIBLY_CHANGED;
  }

  public void setErrorLevel(@NotNull List<? extends HighlightDisplayKey> keys, @NotNull HighlightDisplayLevel level, @Nullable String scopeName, @NotNull Project project) {
    for (HighlightDisplayKey key : keys) {
      setErrorLevel(key, level, scopeName, project);
    }
  }

  public void setEditorAttributesKey(@NotNull List<? extends HighlightDisplayKey> keys, @Nullable TextAttributesKey attributesKey, @Nullable String scopeName, @NotNull Project project) {
    for (HighlightDisplayKey key : keys) {
      setEditorAttributesKey(key.getShortName(), attributesKey == null ? null : attributesKey.getExternalName(), scopeName, project);
    }
  }

  public @NotNull ToolsImpl getTools(@NotNull String name, @Nullable Project project) {
    return Objects.requireNonNull(
      getToolsOrNull(name, project),
      () -> "Can't find tools for \"" + name + "\" in the profile \"" + getName() + "\"");
  }

  public @Nullable ToolsImpl getToolsOrNull(@NotNull String name, @Nullable Project project) {
    initInspectionTools();
    return myTools.get(name);
  }

  public @NotNull Collection<ToolsImpl> getTools() {
    initInspectionTools();
    return myTools.values();
  }

  public void enableAllTools(@NotNull Project project) {
    for (InspectionToolWrapper<?,?> entry : getInspectionTools(null)) {
      enableTool(entry.getShortName(), project);
    }
  }

  public void disableAllTools(@NotNull Project project) {
    for (InspectionToolWrapper<?,?> entry : getInspectionTools(null)) {
      setToolEnabled(entry.getShortName(), false, project);
    }
  }

  /** See {@link #setToolEnabled(String, boolean, Project, boolean)} */
  public final void setToolEnabled(@NotNull String toolShortName, boolean enabled) {
    setToolEnabled(toolShortName, enabled, null, true);
  }

  /** See {@link #setToolEnabled(String, boolean, Project, boolean)} */
  public final void setToolEnabled(@NotNull String toolShortName, boolean enabled, @Nullable Project project) {
    setToolEnabled(toolShortName, enabled, project, true);
  }

  /**
   * If you need to enable multiple tools, please use {@link #modifyProfile}.
   */
  public final void setToolEnabled(@NotNull String toolShortName, boolean enabled, @Nullable Project project, boolean fireEvents) {
    var tool = getTools(toolShortName, project != null ? project : getDefaultProject());

    if (enabled && tool.isEnabled() && tool.getDefaultState().isEnabled()) {
      return;
    }

    if (enabled) {
      tool.setEnabled(true);
      tool.getDefaultState().setEnabled(true);
    }
    else {
      tool.setEnabled(false);
      if (tool.getNonDefaultTools() == null) {
        tool.getDefaultState().setEnabled(false);
      }
    }

    mySchemeState = SchemeState.POSSIBLY_CHANGED;

    if (fireEvents) {
      myProfileManager.fireProfileChanged(this);
    }
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return super.equals(other) && ((InspectionProfileImpl)other).myProfileManager == myProfileManager;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myProfileManager.hashCode();
  }

  public static void setToolEnabled(boolean newState,
                                    @NotNull InspectionProfileImpl profile,
                                    @NotNull String shortName,
                                    @NotNull Project project) {
    setToolEnabled(newState, profile, shortName, false, project);
  }

  public static void setToolEnabled(boolean newState,
                                    @NotNull InspectionProfileImpl profile,
                                    @NotNull String shortName,
                                    boolean fireEvents,
                                    @NotNull Project project) {
    profile.setToolEnabled(shortName, newState, project, fireEvents);
    for (ScopeToolState scopeToolState : profile.getTools(shortName, project).getTools()) {
      scopeToolState.setEnabled(newState);
    }
  }

  public @NotNull OptionController controllerFor(@NotNull PsiElement element) {
    return OptionController.empty().onPrefixes(toolId -> containerForTool(toolId, element));
  }

  private @Nullable OptionController containerForTool(@NotNull String toolShortName, @NotNull PsiElement element) {
    var model = new InspectionProfileModifiableModel(this);
    ToolsImpl toolList = model.getToolsOrNull(toolShortName, element.getProject());
    if (toolList == null) return null;
    return OptionController.empty()
      .onPrefix("options", toolList.getInspectionTool(element).getTool().getOptionController())
      .onValueSet((bindId, value) -> {
        model.commit();
        getProfileManager().fireProfileChanged(this);
      });
  }

  private static final class MyInspectionElementsMerger extends InspectionElementsMergerBase {
    private final String myShortName;
    private final LocalInspectionToolWrapper myWrapper;

    private MyInspectionElementsMerger(@NotNull String shortName, @NotNull LocalInspectionToolWrapper wrapper) {
      myShortName = shortName;
      myWrapper = wrapper;
    }

    @Override
    public @NotNull String getMergedToolName() {
      return myShortName;
    }

    @Override
    public String @NotNull [] getSourceToolNames() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    protected boolean areSettingsMerged(@NotNull Map<String, Element> settings, @NotNull Element element) {
      // returns true when settings are default, so defaults will not be saved in profile
      boolean enabled = myWrapper.isEnabledByDefault();
      return Boolean.parseBoolean(element.getAttributeValue("enabled")) == enabled &&
             Boolean.parseBoolean(element.getAttributeValue("enabled_by_default")) == enabled &&
             myWrapper.getDefaultLevel().toString().equals(element.getAttributeValue("level")) &&
             element.getChildren("scope").isEmpty();
    }
  }
}
