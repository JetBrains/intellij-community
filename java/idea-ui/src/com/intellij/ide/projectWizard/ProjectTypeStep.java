// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.diagnostic.PluginException;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.*;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.wizard.*;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleTypeBase;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplateEP;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.SingleSelectionModel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("unchecked")
public final class ProjectTypeStep extends ModuleWizardStep implements SettingsStep, Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectTypeStep.class);
  private static final ExtensionPointName<ProjectCategory> CATEGORY_EP =
    new ExtensionPointName<>("com.intellij.projectWizard.projectCategory");

  private static final ExtensionPointName<ProjectTemplateEP> TEMPLATE_EP = new ExtensionPointName<>("com.intellij.projectTemplate");

  private static final Convertor<FrameworkSupportInModuleProvider, String> PROVIDER_STRING_CONVERTOR =
    o -> o.getId();
  private static final Function<FrameworkSupportNode, String> NODE_STRING_FUNCTION = FrameworkSupportNodeBase::getId;
  private static final String TEMPLATES_CARD = "templates card";
  private static final String FRAMEWORKS_CARD = "frameworks card";
  private static final String PROJECT_WIZARD_GROUP = "project.wizard.group";
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
  private JBList<TemplatesGroup> myProjectTypeList;
  private ProjectTemplateList myTemplatesList;
  private JPanel myFrameworksPanelPlaceholder;
  private JPanel myHeaderPanel;
  private JBLabel myFrameworksLabel;
  private JPanel mySettingsPanel;
  private JPanel myProjectTypePanel;
  @Nullable
  private ModuleWizardStep mySettingsStep;
  private String myCurrentCard;
  private TemplatesGroup myLastSelectedGroup;

  public ProjectTypeStep(WizardContext context, NewProjectWizard wizard, ModulesProvider modulesProvider) {
    myContext = context;
    myContext.addContextListener(new WizardContext.Listener() {
      @Override
      public void switchToRequested(@NotNull String placeId, @NotNull Consumer<Step> configure) {
        TemplatesGroup groupToSelect = ContainerUtil.find(myTemplatesMap.keySet(), group -> group.getId().equals(placeId));
        if (groupToSelect != null) {
          myProjectTypeList.setSelectedValue(groupToSelect, true);
          configure.accept(getCustomStep());
        }
      }
    });

    myWizard = wizard;

    myTemplatesMap = isNewWizard() ? MultiMap.createLinked() : MultiMap.createConcurrent();
    final List<TemplatesGroup> groups = fillTemplatesMap(context);
    LOG.debug("groups=" + groups);

    myProjectTypeList = new JBList<>();
    myProjectTypeList.setModel(new CollectionListModel<>(groups));
    myProjectTypeList.setSelectionModel(new SingleSelectionModel());
    myProjectTypeList.addListSelectionListener(__ -> updateSelection());
    myProjectTypeList.setCellRenderer(new ProjectTypeListRenderer(groups));

    if (isNewWizard()) {
      GridLayoutManager layout = (GridLayoutManager)myPanel.getLayout();
      layout.setHGap(0);
      myPanel.setLayout(layout);

      JBSplitter splitter = new OnePixelSplitter(false, 0.25f);
      splitter.setFirstComponent(myProjectTypePanel);
      splitter.setSecondComponent(mySettingsPanel);
      myPanel.removeAll();
      myPanel.add(splitter, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH,
                                                GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_WANT_GROW,
                                                null, null, null));

      String emptyCard = "emptyCard";
      ProjectTypeListWithSearch<TemplatesGroup> listWithFilter = new ProjectTypeListWithSearch<>(
        myContext, myProjectTypeList, new JBScrollPane(myProjectTypeList), getNamer(), () -> {
          showCard(emptyCard);
          wizard.updateButtons(true, false, true);
      });

      myProjectTypePanel.add(listWithFilter);

      myOptionsPanel.add(new JBPanelWithEmptyText().withEmptyText(JavaUiBundle.message("label.select.project.type.to.configure")), emptyCard);

      Border border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0);
      listWithFilter.setBorder(border);
      mySettingsPanel.setBorder(border);
    } else {
      myProjectTypePanel.add(BorderLayout.CENTER, new JBScrollPane(myProjectTypeList));
    }

    myModulesProvider = modulesProvider;
    Project project = context.getProject();
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(context, modulesProvider);
    FrameworkSupportModelBase model = new FrameworkSupportModelBase(project, null, container) {
      @NotNull
      @Override
      public String getBaseDirectoryForLibrariesPath() {
        ModuleBuilder builder = getSelectedBuilder();
        return StringUtil.notNullize(builder.getContentEntryPath());
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

    myProjectTypeList.getSelectionModel().addListSelectionListener(__ -> projectTypeChanged());

    myTemplatesList.addListSelectionListener(__ -> updateSelection());

    for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
      ModuleBuilder builder = templatesGroup.getModuleBuilder();
      if (builder != null) {
        myWizard.getSequence().addStepsForBuilder(builder, context, modulesProvider);
      }
      for (ProjectTemplate template : myTemplatesMap.get(templatesGroup)) {
        myWizard.getSequence().addStepsForBuilder(myBuilders.get(template), context, modulesProvider);
      }
    }

    final String groupId = PropertiesComponent.getInstance().getValue(PROJECT_WIZARD_GROUP);
    LOG.debug("saved groupId=" + groupId);
    if (groupId != null) {
      TemplatesGroup group = ContainerUtil.find(groups, group1 -> groupId.equals(group1.getId()));
      if (group != null) {
        myProjectTypeList.setSelectedValue(group, true);
      }
    }
    if (myProjectTypeList.getSelectedValue() == null) {
      myProjectTypeList.setSelectedIndex(0);
    }
    myTemplatesList.restoreSelection();
  }

  @NotNull
  private Function<TemplatesGroup, String> getNamer() {
    return group -> {
      ModuleBuilder builder = group.getModuleBuilder();
      String name = group.getName();
      if (builder == null) return name;

      ModuleWizardStep step = myCustomSteps.get(builder.getBuilderId());
      if (!(step instanceof NewProjectWizardStep)) return name;

      return String.join(" ", ContainerUtil.addAll(new ArrayList<>(((NewProjectWizardStep)step).getKeywords().toSet()), name));
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

  private static List<TemplatesGroup> getUserTemplatesMap(@NotNull WizardContext context) {
    ArrayList<ProjectTemplate> result = new ArrayList<>();
    ContainerUtil.addAll(result, new ArchivedTemplatesFactory().createTemplates(ProjectTemplatesFactory.CUSTOM_GROUP, context));

    List<TemplatesGroup> templatesGroups = new ArrayList<>();
    for (ProjectTemplate template : result) {
      AbstractModuleBuilder builder = template.createModuleBuilder();
      if (template instanceof ArchivedProjectTemplate && builder instanceof ModuleBuilder) {
        templatesGroups.add(new TemplatesGroup(template.getName(), template.getDescription(), template.getIcon(), 0, null,
                                               builder.getBuilderId(), ((ModuleBuilder)builder)));
      }
    }
    return templatesGroups;
  }

  private static MultiMap<TemplatesGroup, ProjectTemplate> getTemplatesMap(WizardContext context) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<TemplatesGroup, ProjectTemplate> groups = new MultiMap<>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        //don't add "User-defined" node for new project wizard
        if (isNewWizard() && factory instanceof ArchivedTemplatesFactory) {
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
    return FRAMEWORKS_CARD.equals(myCurrentCard) && getSelectedBuilder().equals(myContext.getProjectBuilder());
  }

  private @NotNull List<TemplatesGroup> fillTemplatesMap(@NotNull WizardContext context) {
    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    if (context.isCreatingNewProject()) {
      if (!isNewWizard()) {
        builders.add(new EmptyModuleBuilder());
      }
    }

    //add them later for new wizard, after sorting
    builders.removeIf(
      it -> it instanceof NewProjectBuilder ||
            it instanceof NewEmptyProjectBuilder ||
            it instanceof NewModuleBuilder);

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

    if (isNewWizard()) {
      var newWizardGroups = new ArrayList<TemplatesGroup>();
      var newWizardUserGroups = new ArrayList<TemplatesGroup>();
      if (context.isCreatingNewProject()) {
        newWizardGroups.add(new TemplatesGroup(new NewProjectBuilder()));
        newWizardGroups.add(new TemplatesGroup(new NewEmptyProjectBuilder()));
        newWizardUserGroups.addAll(getUserTemplatesMap(context));
      }
      else {
        newWizardGroups.add(new TemplatesGroup(new NewModuleBuilder()));
      }
      groups.addAll(0, newWizardGroups);
      groups.addAll(newWizardUserGroups);
      newWizardGroups.forEach(it -> myTemplatesMap.put(it, new ArrayList<>()));
      newWizardUserGroups.forEach(it -> myTemplatesMap.put(it, new ArrayList<>()));
    }

    return groups;
  }

  private static boolean isNewWizard() {
    return Experiments.getInstance().isFeatureEnabled("new.project.wizard");
  }

  @TestOnly
  @Nullable
  ModuleWizardStep getSettingsStep() {
    return mySettingsStep;
  }

  // new TemplatesGroup selected
  private void projectTypeChanged() {
    TemplatesGroup group = getSelectedGroup();
    if (group == null || !isNewWizard() && group == myLastSelectedGroup) {
      return;
    }

    myLastSelectedGroup = group;
    PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, group.getId() );
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectTypeChanged: " + group.getId() + " " + DebugUtil.currentStackTrace());
    }
    ModuleBuilder groupModuleBuilder = group.getModuleBuilder();

    mySettingsStep = null;
    myHeaderPanel.removeAll();
    if (groupModuleBuilder != null && groupModuleBuilder.getModuleType() != null) {
      mySettingsStep = groupModuleBuilder.modifyProjectTypeStep(this);
    }

    if (groupModuleBuilder == null || (!isNewWizard() && groupModuleBuilder.isTemplateBased())) {
      showTemplates(group);
    }
    else if (!showCustomOptions(groupModuleBuilder)){
      List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(groupModuleBuilder);
      final ProjectCategory category = group.getProjectCategory();
      if (category != null) {
        List<FrameworkSupportInModuleProvider> filtered = ContainerUtil.filter(providers, provider -> matchFramework(category, provider));
        // add associated
        Map<String, FrameworkSupportInModuleProvider> map = ContainerUtil.newMapFromValues(providers.iterator(), PROVIDER_STRING_CONVERTOR);
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
                                       ContainerUtil.set(category.getAssociatedFrameworkIds()),
                                       ContainerUtil.set(category.getPreselectedFrameworkIds()));
      }
      else {
        myFrameworksPanel.setProviders(providers);
      }
      getSelectedBuilder().addModuleConfigurationUpdater(myConfigurationUpdater);

      showCard(FRAMEWORKS_CARD);
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
    Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
    setTemplatesList(group, templates, false);
    showCard(TEMPLATES_CARD);
  }

  private void setTemplatesList(TemplatesGroup group, Collection<? extends ProjectTemplate> templates, boolean preserveSelection) {
    List<ProjectTemplate> list = new ArrayList<>(templates);
    ModuleBuilder moduleBuilder = group.getModuleBuilder();
    if (moduleBuilder != null && !(moduleBuilder instanceof TemplateModuleBuilder)) {
      list.add(0, new BuilderBasedTemplate(moduleBuilder));
    }
    myTemplatesList.setTemplates(list, preserveSelection);
  }

  private boolean showCustomOptions(@NotNull ModuleBuilder builder) {
    String card = builder.getBuilderId();

    ModuleWizardStep step = myCustomSteps.get(card);
    if (step == null) {
      step = builder.getCustomOptionsStep(myContext, this);
      if (isNewWizard() && builder instanceof TemplateModuleBuilder) {
        step = new ProjectSettingsStep(myContext);
        myContext.setProjectBuilder(builder);
        NewProjectWizardCollector.logCustomTemplateSelected(myContext);
      }

      if (step == null) return false;

      myContext.setProjectBuilder(builder);
      step.updateStep();
      JComponent component = step.getComponent();
      if (isNewWizard()) {
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

    NewProjectWizardCollector.logGeneratorSelected(myContext, builder.getClass());
    NewProjectWizardCollector.logScreen(myContext, 1);

    showCard(card);

    return true;
  }

  @TestOnly
  public ModuleWizardStep getFrameworksStep() {
    return getCustomStep();
  }

  @Nullable
  public ModuleWizardStep getCustomStep() {
    return myCustomSteps.get(myCurrentCard);
  }

  private TemplatesGroup getSelectedGroup() {
    return myProjectTypeList.getSelectedValue();
  }

  @Nullable
  private ProjectTemplate getSelectedTemplate() {
    return myCurrentCard == TEMPLATES_CARD ? myTemplatesList.getSelectedTemplate() : null;
  }

  @Nullable
  private ModuleBuilder getSelectedBuilder() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      return myBuilders.get(template);
    }
    TemplatesGroup group = getSelectedGroup();
    if (group == null) return null;

    return group.getModuleBuilder();
  }

  public Collection<ProjectTemplate> getAvailableTemplates() {
    if (myCurrentCard != FRAMEWORKS_CARD) {
      return Collections.emptyList();
    }
    else {
      Collection<ProjectTemplate> templates = myTemplatesMap.get(getSelectedGroup());
      List<FrameworkSupportNode> nodes = myFrameworksPanel.getSelectedNodes();
      if (nodes.isEmpty()) return templates;
      final List<String> selectedFrameworks = ContainerUtil.map(nodes, NODE_STRING_FUNCTION);
      return ContainerUtil.filter(templates, template -> {
        if (!(template instanceof ArchivedProjectTemplate)) return true;
        List<String> frameworks = ((ArchivedProjectTemplate)template).getFrameworks();
        return frameworks.containsAll(selectedFrameworks);
      });
    }
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
    return ObjectUtils.chooseNotNull(component, myProjectTypeList);
  }

  @Override
  public void dispose() {
    myLastSelectedGroup = null;
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
        return;
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
    });
    return map;
  }

  void loadRemoteTemplates(final ChooseTemplateStep chooseTemplateStep) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      UiNotifyConnector.doWhenFirstShown(myPanel, () -> startLoadingRemoteTemplates(chooseTemplateStep));
    }
    else {
      startLoadingRemoteTemplates(chooseTemplateStep);
    }
  }

  private void startLoadingRemoteTemplates(ChooseTemplateStep chooseTemplateStep) {
    myTemplatesList.setPaintBusy(true);
    chooseTemplateStep.getTemplateList().setPaintBusy(true);
    ProgressManager.getInstance().run(new Task.Backgroundable(myContext.getProject(), JavaUiBundle.message("progress.title.loading.templates")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        RemoteTemplatesFactory factory = new RemoteTemplatesFactory();
        for (String group : factory.getGroups()) {
          ProjectTemplate[] templates = factory.createTemplates(group, myContext);
          for (ProjectTemplate template : templates) {
            String id = ((ArchivedProjectTemplate)template).getCategory();
            for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
              if (Objects.equals(id, templatesGroup.getId()) || Objects.equals(group, templatesGroup.getName())) {
                myTemplatesMap.putValue(templatesGroup, template);
              }
            }
          }
        }
      }

      @Override
      public void onSuccess() {
        super.onSuccess();
        TemplatesGroup group = getSelectedGroup();
        if (group == null) return;
        Collection<ProjectTemplate> templates = myTemplatesMap.get(group);
        setTemplatesList(group, templates, true);
        chooseTemplateStep.updateStep();
      }

      @Override
      public void onFinished() {
        myTemplatesList.setPaintBusy(false);
        chooseTemplateStep.getTemplateList().setPaintBusy(false);
      }
    });
  }

  private void updateSelection() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      myContext.setProjectTemplate(template);
    }

    ModuleBuilder builder = getSelectedBuilder();
    LOG.debug("builder=" + builder + "; template=" + template + "; group=" + getSelectedGroup() + "; groupIndex=" + myProjectTypeList.getMinSelectionIndex());

    myContext.setProjectBuilder(builder);
    if (builder != null) {
      StepSequence sequence = myWizard.getSequence();
      sequence.setType(builder.getBuilderId());
      sequence.setIgnoredSteps(builder.getIgnoredSteps());
    }
    myWizard.setDelegate(builder instanceof WizardDelegate ? (WizardDelegate)builder : null);
    myWizard.updateWizardButtons();
  }

  @TestOnly
  public String availableTemplateGroupsToString() {
    ListModel<TemplatesGroup> model = myProjectTypeList.getModel();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < model.getSize(); i++) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append(model.getElementAt(i).getName());
    }
    return builder.toString();
  }

  @TestOnly
  public boolean setSelectedTemplate(@NotNull String group, @Nullable String name) {
    ListModel<TemplatesGroup> model = myProjectTypeList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      TemplatesGroup templatesGroup = model.getElementAt(i);
      if (group.equals(templatesGroup.getName())) {
        myProjectTypeList.setSelectedIndex(i);
        if (name == null) {
          return getSelectedGroup().getName().equals(group);
        }
        else {
          setTemplatesList(templatesGroup, myTemplatesMap.get(templatesGroup), false);
          return myTemplatesList.setSelectedTemplate(name);
        }
      }
    }
    return false;
  }

  public static void resetGroupForTests() {
    PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, null);
  }

  @TestOnly
  public AddSupportForFrameworksPanel getFrameworksPanel() {
    return myFrameworksPanel;
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
  public JTextField getModuleNameField() {
    return null;
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
    TemplatesGroup group = myProjectTypeList.getSelectedValue();
    if (group == null) return;

    FeatureUsageData data = new FeatureUsageData();
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

  private static class ProjectTypeListRenderer extends GroupedItemsListRenderer<TemplatesGroup> {
    ProjectTypeListRenderer(java.util.List<TemplatesGroup> groups) {
      super(new ListItemDescriptorAdapter<>() {
        @Nullable
        @Override
        public String getTextFor(TemplatesGroup value) {
          return value.getName();
        }

        @Nullable
        @Override
        public String getTooltipFor(TemplatesGroup value) {
          return value.getDescription();
        }

        @Nullable
        @Override
        public Icon getIconFor(TemplatesGroup value) {
          return value.getIcon();
        }

        @Override
        public String getCaptionAboveOf(TemplatesGroup value) {
          return isNewWizard()
                 ? value.getModuleBuilder() instanceof TemplateModuleBuilder
                   ? UIBundle.message("list.caption.group.templates")
                   : UIBundle.message("list.caption.group.generators")
                 : super.getCaptionAboveOf(value);
        }

        @Override
        public boolean hasSeparatorAboveOf(TemplatesGroup value) {
          int index = groups.indexOf(value);
          if (index < 1) return false;
          TemplatesGroup upper = groups.get(index - 1);
          if (isNewWizard()) {
            if (value.getModuleBuilder() instanceof TemplateModuleBuilder && !(upper.getModuleBuilder() instanceof TemplateModuleBuilder)) {
              return true;
            }

            ModuleBuilder builder = upper.getModuleBuilder();
            return builder instanceof NewEmptyProjectBuilder || builder instanceof NewModuleBuilder;
          }

          if (upper.getParentGroup() == null && value.getParentGroup() == null) return true;
          return !Objects.equals(upper.getParentGroup(), value.getParentGroup()) &&
                 !Objects.equals(upper.getName(), value.getParentGroup());
        }
      });
    }

    @Override
    protected JComponent createItemComponent() {
      JComponent component = super.createItemComponent();
      myTextLabel.setBorder(!isNewWizard() ? JBUI.Borders.empty(3) : JBUI.Borders.empty(5, 0));
      return component;
    }

    @Override
    protected SeparatorWithText createSeparator() {
      if (!isNewWizard()) {
        return super.createSeparator();
      }
      SeparatorWithText separator = createSeparatorComponent();
      separator.setBorder(JBUI.Borders.empty(20, 8, 5, 0));
      separator.setCaptionCentered(false);
      separator.setFont(JBUI.Fonts.smallFont());
      return separator;
    }

    @NotNull
    private static SeparatorWithText createSeparatorComponent() {
      return new SeparatorWithText() {
        @Override
        protected void paintLinePart(Graphics g, int xMin, int xMax, int hGap, int y) { }
      };
    }

    @Override
    protected void setComponentIcon(Icon icon, Icon disabledIcon) {
      super.setComponentIcon(icon, disabledIcon);
      if (icon == null) {
        myTextLabel.setIconTextGap(0);
      }
    }
  }
}
