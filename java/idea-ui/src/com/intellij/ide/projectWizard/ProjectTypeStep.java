// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard;

import com.intellij.diagnostic.PluginException;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.projectWizard.projectTypeStep.*;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.*;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.wizard.*;
import com.intellij.ide.wizard.LanguageNewProjectWizard;
import com.intellij.ide.wizard.language.*;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleTypeBase;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplateEP;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.*;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Dmitry Avdeev
 */
public final class ProjectTypeStep extends ModuleWizardStep implements SettingsStep, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectWizard.ProjectTypeStep");
  private static final ExtensionPointName<ProjectCategory> CATEGORY_EP =
    new ExtensionPointName<>("com.intellij.projectWizard.projectCategory");

  private static final ExtensionPointName<ProjectTemplateEP> TEMPLATE_EP = new ExtensionPointName<>("com.intellij.projectTemplate");

  private static final String EMPTY_CARD = "empty card";
  private static final String TEMPLATES_CARD = "templates card";
  private static final String FRAMEWORKS_CARD = "frameworks card";

  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private final AddSupportForFrameworksPanel myFrameworksPanel;
  private final ModuleBuilder.ModuleConfigurationUpdater myConfigurationUpdater;
  private final Map<ProjectTemplate, ModuleBuilder> myBuilders = FactoryMap.create(key -> (ModuleBuilder)key.createModuleBuilder());
  private final MultiMap<TemplatesGroup, ProjectTemplate> myTemplatesMap;
  private final Map<String, ModuleWizardStep> myCustomSteps = new HashMap<>();
  private JPanel myPanel;
  private JPanel myOptionsPanel;
  private ProjectTemplateList myTemplatesList;
  private JPanel myFrameworksPanelPlaceholder;
  private JPanel myHeaderPanel;
  private JBLabel myFrameworksLabel;
  private JPanel mySettingsPanel;
  private JPanel myProjectTypePanel;
  @Nullable
  private ModuleWizardStep mySettingsStep;
  private String myCurrentCard;

  private final ProjectTypeList myProjectTypeList;

  public ProjectTypeStep(WizardContext context, NewProjectWizard wizard, ModulesProvider modulesProvider) {
    myContext = context;
    myContext.addContextListener(new WizardContext.Listener() {
      @Override
      public void switchToRequested(@NotNull String placeId, @NotNull Consumer<? super Step> configure) {
        TemplatesGroup groupToSelect = ContainerUtil.find(myTemplatesMap.keySet(), group -> group.getId().equals(placeId));
        if (groupToSelect != null) {
          myProjectTypeList.setSelectedTemplateGroup(groupToSelect);
          try {
            configure.accept(getCustomStep());
          }
          catch (Throwable exception) {
            throw new IllegalStateException("Cannot switch on " + placeId + ", current step " + myCurrentCard, exception);
          }
        }
      }
    });

    myWizard = wizard;

    myTemplatesMap = MultiMap.createLinked();

    myProjectTypeList = new ProjectTypeList(context);
    myProjectTypeList.installFilteringListModel(getNamer(), getEmptyStatusPresenter());
    // These "fill" methods are stateful. The "fillGroupTemplateMap" method reads and modifies the "myTemplatesMap".
    myProjectTypeList.setTemplateGroupItems(fillGroupTemplateMap(context));
    myProjectTypeList.setLanguageGeneratorItems(fillLanguageGeneratorTemplateMap(context));
    myProjectTypeList.setUserTemplateGroupItems(fillUserTemplateMap(context));
    installLanguageGeneratorWatcher(context);

    GridLayoutManager layout = (GridLayoutManager)myPanel.getLayout();
    layout.setHGap(0);
    myPanel.setLayout(layout);

    myProjectTypePanel.setMinimumSize(new Dimension(240, 100));

    JBSplitter splitter = new OnePixelSplitter(false, 0.25f);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setFirstComponent(myProjectTypePanel);
    splitter.setSecondComponent(mySettingsPanel);

    myPanel.removeAll();
    myPanel.add(splitter, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH,
                                              GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_WANT_GROW,
                                              null, null, null));

    myProjectTypePanel.add(myProjectTypeList.getComponent());

    JBPanelWithEmptyText panelWithEmptyText = new JBPanelWithEmptyText()
      .withEmptyText(JavaUiBundle.message("label.select.project.type.to.configure"));
    myOptionsPanel.add(panelWithEmptyText, EMPTY_CARD);

    mySettingsPanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0));

    myModulesProvider = modulesProvider;
    Project project = context.getProject();
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(context, modulesProvider);
    FrameworkSupportModelBase model = new FrameworkSupportModelBase(project, null, container) {
      @NotNull
      @Override
      public String getBaseDirectoryForLibrariesPath() {
        var builder = getSelectedBuilder();
        var contentEntryPath = ObjectUtils.doIfNotNull(builder, it -> it.getContentEntryPath());
        return StringUtil.notNullize(contentEntryPath);
      }

      @Override
      public ModuleBuilder getModuleBuilder() {
        return getSelectedBuilder();
      }
    };
    myFrameworksPanel = new AddSupportForFrameworksPanel(Collections.emptyList(), model, true, myHeaderPanel);
    Disposer.register(this, myFrameworksPanel);
    myFrameworksPanelPlaceholder.add(myFrameworksPanel.getMainPanel());
    myFrameworksLabel.setLabelFor(myFrameworksPanel.getFrameworksTree());
    myFrameworksLabel.setBorder(JBUI.Borders.empty(3));

    myConfigurationUpdater = new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        if (isFrameworksMode()) {
          myFrameworksPanel.addSupport(module, rootModel);
        }
      }
    };

    myProjectTypeList.whenProjectTemplateGroupSelected(group -> {
      projectTypeChanged(group);
    });

    myTemplatesList.addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) {
        updateSelection();
      }
    });

    for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
      ModuleBuilder builder = templatesGroup.getModuleBuilder();
      if (builder != null) {
        myWizard.getSequence().addStepsForBuilder(builder, context, modulesProvider);
      }
      for (ProjectTemplate template : myTemplatesMap.get(templatesGroup)) {
        myWizard.getSequence().addStepsForBuilder(myBuilders.get(template), context, modulesProvider);
      }
    }

    myProjectTypeList.restoreSelection();
    myTemplatesList.restoreSelection();
  }

  private @NotNull Function<TemplateGroupItem, String> getNamer() {
    return groupItem -> {
      var keywords = new LinkedHashSet<String>();
      keywords.add(groupItem.getGroup().getName());
      var builder = groupItem.getGroup().getModuleBuilder();
      if (builder != null) {
        var step = myCustomSteps.get(builder.getBuilderId());
        if (step instanceof NewProjectWizardStep newProjectWizardStep) {
          keywords.addAll(newProjectWizardStep.getKeywords().toSet());
        }
      }
      return String.join(" ", keywords);
    };
  }

  private @NotNull Runnable getEmptyStatusPresenter() {
    return () -> {
      showCard(EMPTY_CARD);
      myWizard.updateButtons(true, false, true);
    };
  }

  private static ModuleType getModuleType(TemplatesGroup group) {
    ModuleBuilder moduleBuilder = group.getModuleBuilder();
    return moduleBuilder == null ? null : moduleBuilder.getModuleType();
  }

  private static boolean matchFramework(ProjectCategory projectCategory, FrameworkSupportInModuleProvider framework) {

    FrameworkRole[] roles = framework.getRoles();
    if (roles.length == 0) return true;

    List<FrameworkRole> acceptable = Arrays.asList(projectCategory.getAcceptableFrameworkRoles());
    return ContainerUtil.intersects(Arrays.asList(roles), acceptable);
  }

  private static MultiMap<TemplatesGroup, ProjectTemplate> getTemplatesMap(WizardContext context) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<TemplatesGroup, ProjectTemplate> groups = new MultiMap<>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        //don't add "User-defined" node for new project wizard
        if (factory instanceof ArchivedTemplatesFactory) {
          continue;
        }

        ProjectTemplate[] templates = factory.createTemplates(group, context);
        List<ProjectTemplate> values = Arrays.asList(templates);
        if (!values.isEmpty()) {
          Icon icon = factory.getGroupIcon(group);
          String parentGroup = factory.getParentGroup(group);
          TemplatesGroup templatesGroup = new TemplatesGroup(group, null, icon, factory.getGroupWeight(group), parentGroup, group, null);
          templatesGroup.setPluginInfo(PluginInfoDetectorKt.getPluginInfo(factory.getClass()));
          groups.putValues(templatesGroup, values);
        }
      }
    }
    return groups;
  }

  private boolean isFrameworksMode() {
    return FRAMEWORKS_CARD.equals(myCurrentCard) && Objects.equals(getSelectedBuilder(), myContext.getProjectBuilder());
  }

  private @NotNull List<TemplateGroupItem> fillGroupTemplateMap(@NotNull WizardContext context) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    Map<String, TemplatesGroup> groupMap = new HashMap<>();
    for (ModuleBuilder builder : builders) {
      try {
        BuilderBasedTemplate template = new BuilderBasedTemplate(builder);
        if (builder.isTemplate()) {
          TemplatesGroup group = groupMap.get(builder.getGroupName());
          if (group == null) {
            group = new TemplatesGroup(builder);
          }
          myTemplatesMap.putValue(group, template);
        }
        else {
          TemplatesGroup group = new TemplatesGroup(builder);
          groupMap.put(group.getName(), group);
          myTemplatesMap.put(group, new ArrayList<>());
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    myTemplatesMap.putAllValues(getTemplatesMap(context));

    for (ProjectCategory category : CATEGORY_EP.getExtensionList()) {
      TemplatesGroup group = new TemplatesGroup(category);
      ModuleBuilder builder = group.getModuleBuilder();
      if (builder == null || builder.isAvailable()) {
        myTemplatesMap.remove(group);
        myTemplatesMap.put(group, new ArrayList<>());
      }
    }

    if (context.isCreatingNewProject()) {
      MultiMap<String, ProjectTemplate> localTemplates = loadLocalTemplates();
      for (TemplatesGroup group : myTemplatesMap.keySet()) {
        myTemplatesMap.putValues(group, localTemplates.get(group.getId()));
      }
    }

    List<TemplatesGroup> groups = new ArrayList<>(myTemplatesMap.keySet());

    // sorting by module type popularity
    final MultiMap<ModuleType, TemplatesGroup> moduleTypes = new MultiMap<>();
    for (TemplatesGroup group : groups) {
      ModuleType type = getModuleType(group);
      moduleTypes.putValue(type, group);
    }
    groups.sort((o1, o2) -> {
      int i = o2.getWeight() - o1.getWeight();
      if (i != 0) return i;
      int i1 = moduleTypes.get(getModuleType(o2)).size() - moduleTypes.get(getModuleType(o1)).size();
      if (i1 != 0) return i1;
      return o1.compareTo(o2);
    });

    Set<String> groupNames = ContainerUtil.map2Set(groups, TemplatesGroup::getParentGroup);

    // move subgroups
    MultiMap<String, TemplatesGroup> subGroups = new MultiMap<>();
    for (ListIterator<TemplatesGroup> iterator = groups.listIterator(); iterator.hasNext(); ) {
      TemplatesGroup group = iterator.next();
      String parentGroup = group.getParentGroup();
      if (parentGroup != null && groupNames.contains(parentGroup) && !group.getName().equals(parentGroup) && groupMap.containsKey(parentGroup)) {
        subGroups.putValue(parentGroup, group);
        iterator.remove();
      }
    }
    for (ListIterator<TemplatesGroup> iterator = groups.listIterator(); iterator.hasNext(); ) {
      TemplatesGroup group = iterator.next();
      for (TemplatesGroup subGroup : subGroups.get(group.getName())) {
        iterator.add(subGroup);
      }
    }

    // remove Static Web group in IDEA Community if no specific templates found (IDEA-120593)
    if (PlatformUtils.isIdeaCommunity()) {
      for (ListIterator<TemplatesGroup> iterator = groups.listIterator(); iterator.hasNext(); ) {
        TemplatesGroup group = iterator.next();
        if (WebModuleTypeBase.WEB_MODULE.equals(group.getId()) && myTemplatesMap.get(group).isEmpty()) {
          iterator.remove();
          break;
        }
      }
    }

    return ContainerUtil.map(groups, it -> new TemplateGroupItem(it));
  }

  private @NotNull List<LanguageGeneratorItem> fillLanguageGeneratorTemplateMap(@NotNull WizardContext context) {
    var generators = new ArrayList<GeneratorNewProjectWizard>();
    //noinspection deprecation
    LanguageNewProjectWizard.EP_NAME.forEachExtensionSafe(wizard -> {
      generators.add(new LegacyLanguageGeneratorNewProjectWizard(context, wizard));
    });
    LanguageGeneratorNewProjectWizard.EP_NAME.forEachExtensionSafe(wizard -> {
      generators.add(new BaseLanguageGeneratorNewProjectWizard(context, wizard));
    });
    generators.removeIf(it -> !it.isEnabled());
    generators.sort(Comparator.comparing(it -> it.getOrdinal()));
    if (context.isCreatingNewProject()) {
      generators.add(new EmptyProjectGeneratorNewProjectWizard());
    }
    var generatorItems = ContainerUtil.map(generators, it -> new LanguageGeneratorItem(it));
    for (var generatorItem : generatorItems) {
      myTemplatesMap.put(generatorItem.getGroup(), new ArrayList<>());
    }
    return generatorItems;
  }

  private void installLanguageGeneratorWatcher(@NotNull WizardContext context) {
    //noinspection deprecation
    LanguageNewProjectWizard.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(LanguageNewProjectWizard extension, @NotNull PluginDescriptor pluginDescriptor) {
        var generator = new LegacyLanguageGeneratorNewProjectWizard(context, extension);
        var generatorItem = new LanguageGeneratorItem(generator);
        myProjectTypeList.addLanguageGeneratorItem(generatorItem);
      }

      @Override
      public void extensionRemoved(LanguageNewProjectWizard extension, @NotNull PluginDescriptor pluginDescriptor) {
        myProjectTypeList.removeLanguageGeneratorItem(extension.getName());
      }
    }, context.getDisposable());
    LanguageGeneratorNewProjectWizard.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(LanguageGeneratorNewProjectWizard extension, @NotNull PluginDescriptor pluginDescriptor) {
        var generator = new BaseLanguageGeneratorNewProjectWizard(context, extension);
        var generatorItem = new LanguageGeneratorItem(generator);
        myProjectTypeList.addLanguageGeneratorItem(generatorItem);
      }

      @Override
      public void extensionRemoved(LanguageGeneratorNewProjectWizard extension, @NotNull PluginDescriptor pluginDescriptor) {
        myProjectTypeList.removeLanguageGeneratorItem(extension.getName());
      }
    }, context.getDisposable());
  }

  private @NotNull List<UserTemplateGroupItem> fillUserTemplateMap(@NotNull WizardContext context) {
    if (!context.isCreatingNewProject()) {
      return Collections.emptyList();
    }
    var templateItems = new ArrayList<UserTemplateGroupItem>();
    var templates = new ArchivedTemplatesFactory().createTemplates(ProjectTemplatesFactory.CUSTOM_GROUP, context);
    for (var template : templates) {
      if (template instanceof ArchivedProjectTemplate archivedTemplate) {
        templateItems.add(new UserTemplateGroupItem(archivedTemplate));
      }
    }
    for (var templateItem : templateItems) {
      myTemplatesMap.put(templateItem.getGroup(), new ArrayList<>());
    }
    return templateItems;
  }

  // new TemplatesGroup selected
  private void projectTypeChanged(@NotNull TemplatesGroup group) {
    ModuleBuilder groupModuleBuilder = group.getModuleBuilder();

    mySettingsStep = null;
    myHeaderPanel.removeAll();
    if (groupModuleBuilder != null && groupModuleBuilder.getModuleType() != null) {
      mySettingsStep = groupModuleBuilder.modifyProjectTypeStep(this);
    }

    if (groupModuleBuilder == null) {
      showTemplates(group);
    }
    else if (!showCustomOptions(groupModuleBuilder)) {
      showFrameworks(group, groupModuleBuilder);
    }

    myHeaderPanel.setVisible(myHeaderPanel.getComponentCount() > 0);
    // align header labels
    List<JLabel> labels = ContainerUtil.filter(UIUtil.findComponentsOfType(myHeaderPanel, JLabel.class), label ->
      label.isVisible() && label.getLabelFor() != null
    );
    int width = 0;
    for (JLabel label : labels) {
      int width1 = label.getPreferredSize().width;
      width = Math.max(width, width1);
    }
    for (JLabel label : labels) {
      label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
    }
    myHeaderPanel.revalidate();
    myHeaderPanel.repaint();

    updateSelection();
  }

  private void showCard(String card) {
    ((CardLayout)myOptionsPanel.getLayout()).show(myOptionsPanel, card);
    myCurrentCard = card;
  }

  private void showTemplates(TemplatesGroup group) {
    setTemplatesList(group);
    showCard(TEMPLATES_CARD);
  }

  private void setTemplatesList(@NotNull TemplatesGroup group) {
    List<ProjectTemplate> list = new ArrayList<>(myTemplatesMap.get(group));
    ModuleBuilder moduleBuilder = group.getModuleBuilder();
    if (moduleBuilder != null && !(moduleBuilder instanceof TemplateModuleBuilder)) {
      list.add(0, new BuilderBasedTemplate(moduleBuilder));
    }
    myTemplatesList.setTemplates(list, false);
  }

  private boolean showCustomOptions(@NotNull ModuleBuilder builder) {
    String card = builder.getBuilderId();

    ModuleWizardStep step = myCustomSteps.get(card);
    if (step == null) {
      step = builder.getCustomOptionsStep(myContext, this);
      if (builder instanceof TemplateModuleBuilder) {
        step = new ProjectSettingsStep(myContext);
        myContext.setProjectBuilder(builder);
        NewProjectWizardCollector.logCustomTemplateSelected(myContext);
      }

      if (step == null) return false;

      myContext.setProjectBuilder(builder);
      step.updateStep();

      JComponent component = step.getComponent();
      if (!(builder instanceof PromoModuleBuilder)) {
        component = new JBScrollPane(component);
        component.setBorder(JBUI.Borders.empty());
      }

      myOptionsPanel.add(component, card);

      myCustomSteps.put(card, step);
    }

    try {
      step._init();
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    myContext.setScreen(1);

    showCard(card);

    return true;
  }

  private void showFrameworks(@NotNull TemplatesGroup group, @NotNull ModuleBuilder moduleBuilder) {
    ProjectCategory category = group.getProjectCategory();

    List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(moduleBuilder);
    if (category != null) {
      List<FrameworkSupportInModuleProvider> filtered = ContainerUtil.filter(providers, provider -> matchFramework(category, provider));
      // add associated
      Map<String, FrameworkSupportInModuleProvider> map = ContainerUtil.newMapFromValues(providers.iterator(), o -> o.getId());
      Set<FrameworkSupportInModuleProvider> set = new HashSet<>(filtered);
      for (FrameworkSupportInModuleProvider provider : filtered) {
        for (FrameworkSupportInModuleProvider.FrameworkDependency depId : provider.getDependenciesFrameworkIds()) {
          FrameworkSupportInModuleProvider dependency = map.get(depId.getFrameworkId());
          if (dependency == null) {
            if (!depId.isOptional()) {
              LOG.error("Cannot find provider '" + depId.getFrameworkId() + "' which is required for '" + provider.getId() + "'");
            }
            continue;
          }
          set.add(dependency);
        }
      }

      myFrameworksPanel.setProviders(new ArrayList<>(set),
                                     Set.of(category.getAssociatedFrameworkIds()),
                                     Set.of(category.getPreselectedFrameworkIds()));
    }
    else {
      myFrameworksPanel.setProviders(providers);
    }

    moduleBuilder.addModuleConfigurationUpdater(myConfigurationUpdater);

    showCard(FRAMEWORKS_CARD);
  }

  @Nullable
  public ModuleWizardStep getCustomStep() {
    return myCustomSteps.get(myCurrentCard);
  }


  @Nullable
  private ProjectTemplate getSelectedTemplate() {
    return TEMPLATES_CARD.equals(myCurrentCard) ? myTemplatesList.getSelectedTemplate() : null;
  }

  @Nullable
  private ModuleBuilder getSelectedBuilder() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      return myBuilders.get(template);
    }
    TemplatesGroup group = myProjectTypeList.getSelectedTemplateGroup();
    if (group == null) return null;

    return group.getModuleBuilder();
  }

  @Override
  public void onWizardFinished() throws CommitStepException {
    if (isFrameworksMode()) {
      boolean ok = myFrameworksPanel.downloadLibraries(myWizard.getContentComponent());
      if (!ok) {
        throw new CommitStepException(null);
      }
    }
    reportStatistics("finish");
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    ModuleBuilder builder = getSelectedBuilder();
    if (builder != null) {
      myWizard.getSequence().addStepsForBuilder(builder, myContext, myModulesProvider);
    }
    ModuleWizardStep step = getCustomStep();
    if (step != null) {
      step.updateDataModel();
    }
    if (mySettingsStep != null) {
      mySettingsStep.updateDataModel();
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (mySettingsStep != null) {
      if (!mySettingsStep.validate()) return false;
    }
    ModuleWizardStep step = getCustomStep();
    if (step != null && !step.validate()) {
      return false;
    }
    if (isFrameworksMode() && !myFrameworksPanel.validate()) {
      return false;
    }
    return super.validate();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    var step = getCustomStep();
    var component = ObjectUtils.doIfNotNull(step, it -> it.getPreferredFocusedComponent());
    return ObjectUtils.chooseNotNull(component, myProjectTypeList.getComponent());
  }

  @Override
  public void dispose() {
    mySettingsStep = null;
    myTemplatesMap.clear();
    myBuilders.clear();
    myCustomSteps.clear();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  private @NotNull MultiMap<String, ProjectTemplate> loadLocalTemplates() {
    MultiMap<String, ProjectTemplate> map = MultiMap.createConcurrent();
    TEMPLATE_EP.processWithPluginDescriptor((ep, pluginDescriptor) -> {
      ClassLoader classLoader = pluginDescriptor.getClassLoader();
      URL url = classLoader.getResource(StringUtil.trimStart(ep.templatePath, "/"));
      if (url == null) {
        LOG.error(new PluginException("Can't find resource for project template: " + ep.templatePath, pluginDescriptor.getPluginId()));
        return Unit.INSTANCE;
      }

      try {
        LocalArchivedTemplate template = new LocalArchivedTemplate(url, classLoader);
        if (ep.category) {
          TemplateBasedCategory category = new TemplateBasedCategory(template, ep.projectType);
          myTemplatesMap.putValue(new TemplatesGroup(category), template);
        }
        else {
          map.putValue(ep.projectType, template);
        }
      }
      catch (Exception e) {
        LOG.error(new PluginException("Error loading template from URL: " + ep.templatePath, e, pluginDescriptor.getPluginId()));
      }
      return Unit.INSTANCE;
    });
    return map;
  }

  private void updateSelection() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      myContext.setProjectTemplate(template);
    }

    ModuleBuilder builder = getSelectedBuilder();
    LOG.debug("builder=" + builder + "; template=" + template + "; group=" + myProjectTypeList.getSelectedTemplateGroup());

    myContext.setProjectBuilder(builder);
    if (builder != null) {
      StepSequence sequence = myWizard.getSequence();
      sequence.setType(builder.getBuilderId());
      sequence.setIgnoredSteps(builder.getIgnoredSteps());
    }
    myWizard.setDelegate(builder instanceof WizardDelegate ? (WizardDelegate)builder : null);
    myWizard.updateWizardButtons();

    NewProjectWizardCollector.logGeneratorSelected(myContext);
  }

  @TestOnly
  public String availableTemplateGroupsToString() {
    return myProjectTypeList.getAvailableTemplateGroups();
  }

  @TestOnly
  public boolean setSelectedTemplate(@NotNull String groupName, @Nullable String name) {
    myProjectTypeList.setSelectedTemplateGroup(groupName);
    var selectedGroup = myProjectTypeList.getSelectedTemplateGroup();
    if (selectedGroup == null || !groupName.equals(selectedGroup.getName())) {
      return false;
    }
    if (name != null) {
      setTemplatesList(selectedGroup);
      return myTemplatesList.setSelectedTemplate(name);
    }
    return true;
  }

  @TestOnly
  public static void resetGroupForTests() {
    ProjectTypeList.resetStoredSelectionForTests();
  }

  @Override
  public WizardContext getContext() {
    return myContext;
  }

  @Override
  public void addSettingsField(@NotNull @NlsContexts.Label String label, @NotNull JComponent field) {
    ProjectSettingsStep.addField(label, field, myHeaderPanel);
  }

  @Override
  public void addSettingsComponent(@NotNull JComponent component) {
  }

  @Override
  public void addExpertPanel(@NotNull JComponent panel) {

  }

  @Override
  public void addExpertField(@NotNull @NlsContexts.Label String label, @NotNull JComponent field) {

  }

  @Override
  public String getHelpId() {
    if (getCustomStep() != null && getCustomStep().getHelpId() != null) {
      return getCustomStep().getHelpId();
    }
    return myContext.isCreatingNewProject() ? "Project_Category_and_Options" : "Module_Category_and_Options";
  }

  @Override
  public void onStepLeaving() {
    reportStatistics("attempt");
  }

  private void reportStatistics(String eventId) {
    TemplatesGroup group = myProjectTypeList.getSelectedTemplateGroup();
    if (group == null) return;

    FeatureUsageData data = new FeatureUsageData("FUS");
    data.addData("projectType", group.getId());
    data.addPluginInfo(group.getPluginInfo());
    if (myCurrentCard.equals(FRAMEWORKS_CARD)) {
      myFrameworksPanel.reportSelectedFrameworks(eventId, data);
    }
    ModuleWizardStep step = getCustomStep();
    if (step instanceof StatisticsAwareModuleWizardStep) {
      ((StatisticsAwareModuleWizardStep) step).addCustomFeatureUsageData(eventId, data);
    }

    FUCounterUsageLogger.getInstance().logEvent("new.project.wizard", eventId, data);
  }
}
