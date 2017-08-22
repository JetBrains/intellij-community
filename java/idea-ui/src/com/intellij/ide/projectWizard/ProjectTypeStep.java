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
package com.intellij.ide.projectWizard;

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.*;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplateEP;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SingleSelectionModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 04.09.13
 */
@SuppressWarnings("unchecked")
public class ProjectTypeStep extends ModuleWizardStep implements SettingsStep, Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectTypeStep.class);

  public static final Convertor<FrameworkSupportInModuleProvider,String> PROVIDER_STRING_CONVERTOR =
    o -> o.getId();
  public static final Function<FrameworkSupportNode, String> NODE_STRING_FUNCTION = FrameworkSupportNodeBase::getId;
  private static final String TEMPLATES_CARD = "templates card";
  private static final String FRAMEWORKS_CARD = "frameworks card";
  private static final String PROJECT_WIZARD_GROUP = "project.wizard.group";
  private final WizardContext myContext;
  private final NewProjectWizard myWizard;
  private final ModulesProvider myModulesProvider;
  private final AddSupportForFrameworksPanel myFrameworksPanel;
  private final ModuleBuilder.ModuleConfigurationUpdater myConfigurationUpdater;
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final Map<ProjectTemplate, ModuleBuilder> myBuilders = FactoryMap.createMap(key -> (ModuleBuilder)key.createModuleBuilder());
  private final Map<String, ModuleWizardStep> myCustomSteps = new THashMap<>();
  private final MultiMap<TemplatesGroup,ProjectTemplate> myTemplatesMap;
  private JPanel myPanel;
  private JPanel myOptionsPanel;
  private JBList<TemplatesGroup> myProjectTypeList;
  private ProjectTemplateList myTemplatesList;
  private JPanel myFrameworksPanelPlaceholder;
  private JPanel myHeaderPanel;
  private JBLabel myFrameworksLabel;
  @Nullable
  private ModuleWizardStep mySettingsStep;
  private String myCurrentCard;
  private TemplatesGroup myLastSelectedGroup;

  public ProjectTypeStep(WizardContext context, NewProjectWizard wizard, ModulesProvider modulesProvider) {
    myContext = context;
    myWizard = wizard;

    myTemplatesMap = new ConcurrentMultiMap<>();
    final List<TemplatesGroup> groups = fillTemplatesMap(context);
    LOG.debug("groups=" + groups);

    myProjectTypeList.setModel(new CollectionListModel<>(groups));
    myProjectTypeList.setSelectionModel(new SingleSelectionModel());
    myProjectTypeList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateSelection();
      }
    });
    myProjectTypeList.setCellRenderer(new GroupedItemsListRenderer<TemplatesGroup>(new ListItemDescriptorAdapter<TemplatesGroup>() {
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
      public boolean hasSeparatorAboveOf(TemplatesGroup value) {
        int index = groups.indexOf(value);
        if (index < 1) return false;
        TemplatesGroup upper = groups.get(index - 1);
        if (upper.getParentGroup() == null && value.getParentGroup() == null) return true;
        return !Comparing.equal(upper.getParentGroup(), value.getParentGroup()) &&
               !Comparing.equal(upper.getName(), value.getParentGroup());
      }
    }) {
      @Override
      protected JComponent createItemComponent() {
        JComponent component = super.createItemComponent();
        myTextLabel.setBorder(JBUI.Borders.empty(3));
        return component;
      }
    });

    new ListSpeedSearch(myProjectTypeList) {
      @Override
      protected String getElementText(Object element) {
        return ((TemplatesGroup)element).getName();
      }
    };

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

    myProjectTypeList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        projectTypeChanged();
      }
    });

    myTemplatesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
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

  public static MultiMap<TemplatesGroup, ProjectTemplate> getTemplatesMap(WizardContext context) {
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<TemplatesGroup, ProjectTemplate> groups = new MultiMap<>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        ProjectTemplate[] templates = factory.createTemplates(group, context);
        List<ProjectTemplate> values = Arrays.asList(templates);
        if (!values.isEmpty()) {
          Icon icon = factory.getGroupIcon(group);
          String parentGroup = factory.getParentGroup(group);
          TemplatesGroup templatesGroup = new TemplatesGroup(group, null, icon, factory.getGroupWeight(group), parentGroup, group, null);
          groups.putValues(templatesGroup, values);
        }
      }
    }
    return groups;
  }

  private boolean isFrameworksMode() {
    return FRAMEWORKS_CARD.equals(myCurrentCard) && getSelectedBuilder().equals(myContext.getProjectBuilder());
  }

  private List<TemplatesGroup> fillTemplatesMap(WizardContext context) {

    List<ModuleBuilder> builders = ModuleBuilder.getAllBuilders();
    if (context.isCreatingNewProject()) {
      builders.add(new EmptyModuleBuilder());
    }
    Map<String, TemplatesGroup> groupMap = new HashMap<>();
    for (ModuleBuilder builder : builders) {
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

    MultiMap<TemplatesGroup, ProjectTemplate> map = getTemplatesMap(context);
    myTemplatesMap.putAllValues(map);

    for (ProjectCategory category : ProjectCategory.EXTENSION_POINT_NAME.getExtensions()) {
      TemplatesGroup group = new TemplatesGroup(category);
      myTemplatesMap.remove(group);
      myTemplatesMap.put(group, new ArrayList<>());
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
    Collections.sort(groups, (o1, o2) -> {
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

    return groups;
  }

  // new TemplatesGroup selected
  public void projectTypeChanged() {
    TemplatesGroup group = getSelectedGroup();
    if (group == null || group == myLastSelectedGroup) return;
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

    if (groupModuleBuilder == null || groupModuleBuilder.isTemplateBased()) {
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
                                       new HashSet<>(Arrays.asList(category.getAssociatedFrameworkIds())),
                                       new HashSet<>(Arrays.asList(category.getPreselectedFrameworkIds())));
      }
      else {
        myFrameworksPanel.setProviders(providers);
      }
      getSelectedBuilder().addModuleConfigurationUpdater(myConfigurationUpdater);

      showCard(FRAMEWORKS_CARD);
    }

    myHeaderPanel.setVisible(myHeaderPanel.getComponentCount() > 0);
    // align header labels
    List<JLabel> labels = UIUtil.findComponentsOfType(myHeaderPanel, JLabel.class);
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

  private void setTemplatesList(TemplatesGroup group, Collection<ProjectTemplate> templates, boolean preserveSelection) {
    List<ProjectTemplate> list = new ArrayList<>(templates);
    ModuleBuilder moduleBuilder = group.getModuleBuilder();
    if (moduleBuilder != null && !(moduleBuilder instanceof TemplateModuleBuilder)) {
      list.add(0, new BuilderBasedTemplate(moduleBuilder));
    }
    myTemplatesList.setTemplates(list, preserveSelection);
  }

  private boolean showCustomOptions(@NotNull ModuleBuilder builder) {
    String card = builder.getBuilderId();
    if (!myCustomSteps.containsKey(card)) {
      ModuleWizardStep step = builder.getCustomOptionsStep(myContext, this);
      if (step == null) return false;
      step.updateStep();
      myCustomSteps.put(card, step);
      myOptionsPanel.add(step.getComponent(), card);
    }
    showCard(card);
    return true;
  }

  @Nullable
  private ModuleWizardStep getCustomStep() {
    return myCustomSteps.get(myCurrentCard);
  }

  private TemplatesGroup getSelectedGroup() {
    return myProjectTypeList.getSelectedValue();
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    return myCurrentCard == TEMPLATES_CARD ? myTemplatesList.getSelectedTemplate() : null;
  }

  private ModuleBuilder getSelectedBuilder() {
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      return myBuilders.get(template);
    }
    return getSelectedGroup().getModuleBuilder();
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

  public void onWizardFinished() throws CommitStepException {
    if (isFrameworksMode()) {
      boolean ok = myFrameworksPanel.downloadLibraries(myWizard.getContentComponent());
      if (!ok) {
        throw new CommitStepException(null);
      }
    }
    TemplatesGroup group = getSelectedGroup();
    if (group != null) {
      ProjectCategoryUsagesCollector.projectTypeUsed(group.getId());
    }
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    ModuleBuilder builder = getSelectedBuilder();
    myWizard.getSequence().addStepsForBuilder(builder, myContext, myModulesProvider);
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
    return myProjectTypeList;
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

  private MultiMap<String, ProjectTemplate> loadLocalTemplates() {
    ConcurrentMultiMap<String, ProjectTemplate> map = new ConcurrentMultiMap<>();
    ProjectTemplateEP[] extensions = ProjectTemplateEP.EP_NAME.getExtensions();
    for (ProjectTemplateEP ep : extensions) {
      ClassLoader classLoader = ep.getLoaderForClass();
      URL url = classLoader.getResource(ep.templatePath);
      if (url != null) {
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
        catch(Exception e) {
          LOG.error("Error loading template from URL " + ep.templatePath, e);
        }
      } else {
        LOG.error("Can't find resource for project template " + ep.templatePath);
      }
    }
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
    ProgressManager.getInstance().run(new Task.Backgroundable(myContext.getProject(), "Loading Templates") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        RemoteTemplatesFactory factory = new RemoteTemplatesFactory();
        for (String group : factory.getGroups()) {
          ProjectTemplate[] templates = factory.createTemplates(group, myContext);
          for (ProjectTemplate template : templates) {
            String id = ((ArchivedProjectTemplate)template).getCategory();
            for (TemplatesGroup templatesGroup : myTemplatesMap.keySet()) {
              if (Comparing.equal(id, templatesGroup.getId()) || Comparing.equal(group, templatesGroup.getName())) {
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
      myWizard.getSequence().setType(builder.getBuilderId());
    }
    myWizard.setDelegate(builder instanceof WizardDelegate ? (WizardDelegate)builder : null);
    myWizard.updateWizardButtons();
  }

  @TestOnly
  public String availableTemplateGroupsToString() {
    ListModel model = myProjectTypeList.getModel();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < model.getSize(); i++) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append(((TemplatesGroup)model.getElementAt(i)).getName());
    }
    return builder.toString();
  }

  @TestOnly
  public boolean setSelectedTemplate(@NotNull String group, @Nullable String name) {
    ListModel model = myProjectTypeList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      TemplatesGroup templatesGroup = (TemplatesGroup)model.getElementAt(i);
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

  @TestOnly
  public AddSupportForFrameworksPanel getFrameworksPanel() {
    return myFrameworksPanel;
  }

  @Override
  public WizardContext getContext() {
    return myContext;
  }

  @Override
  public void addSettingsField(@NotNull String label, @NotNull JComponent field) {
    ProjectSettingsStep.addField(label, field, myHeaderPanel);
  }

  @Override
  public void addSettingsComponent(@NotNull JComponent component) {
  }

  @Override
  public void addExpertPanel(@NotNull JComponent panel) {

  }

  @Override
  public void addExpertField(@NotNull String label, @NotNull JComponent field) {

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
}
