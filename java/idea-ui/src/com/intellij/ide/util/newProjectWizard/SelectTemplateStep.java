/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.RemoteTemplatesFactory;
import com.intellij.platform.templates.TemplateModuleBuilder;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class SelectTemplateStep extends ModuleWizardStep implements SettingsStep {

  private SimpleTree myTemplatesTree;
  private JPanel mySettingsPanel;
  private SearchTextField mySearchField;
  private JTextPane myDescriptionPane;
  private JPanel myDescriptionPanel;

  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;
  private final HideableDecorator myExpertDecorator;

  private final NamePathComponent myNamePathComponent;
  private final ProjectFormatPanel myFormatPanel;

  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myModulePanel;

  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private final JBSplitter mySplitter;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;

  private final WizardContext myWizardContext;
  private final StepSequence mySequence;
  @Nullable
  private ModuleWizardStep mySettingsStep;

  private final ElementFilter.Active.Impl<SimpleNode> myFilter;
  private final FilteringTreeBuilder myTreeBuilder;
  private boolean myIsTreeUpdating;
  private MinusculeMatcher[] myMatchers;

  @Nullable
  private ModuleBuilder myModuleBuilder;

  public SelectTemplateStep(WizardContext context, StepSequence sequence, final MultiMap<TemplatesGroup, ProjectTemplate> map) {

    myWizardContext = context;
    mySequence = sequence;
    Messages.installHyperlinkSupport(myDescriptionPane);

    myFormatPanel = new ProjectFormatPanel();
    myNamePathComponent = initNamePathComponent(context);
    if (context.isCreatingNewProject()) {
      mySettingsPanel.add(myNamePathComponent, BorderLayout.NORTH);
      addExpertPanel(myModulePanel);
    }
    else {
      mySettingsPanel.add(myModulePanel, BorderLayout.NORTH);
    }
    bindModuleSettings();

    myExpertDecorator = new HideableDecorator(myExpertPlaceholder, "Mor&e Settings", false);
    myExpertPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, IdeBorderFactory.TITLED_BORDER_INDENT, 5, 0));
    myExpertDecorator.setContentComponent(myExpertPanel);

    final RemoteTemplatesFactory factory = new RemoteTemplatesFactory();
    String group = factory.getGroups()[0];
    final DynamicGroupNode dynamicGroupNode = new DynamicGroupNode(new TemplatesGroup(group, null, null), factory);
    SimpleTreeStructure.Impl structure = new SimpleTreeStructure.Impl(new SimpleNode() {
      @Override
      public SimpleNode[] getChildren() {
        SimpleNode[] nodes =
          ContainerUtil.map2Array(map.entrySet(), NO_CHILDREN, new Function<Map.Entry<TemplatesGroup, Collection<ProjectTemplate>>, SimpleNode>() {
            @Override
            public SimpleNode fun(Map.Entry<TemplatesGroup, Collection<ProjectTemplate>> entry) {
              return new GroupNode(entry.getKey(), entry.getValue());
            }
          });

        return ArrayUtil.append(nodes, dynamicGroupNode);
      }
    })
    {
      @Override
      public boolean isToBuildChildrenInBackground(Object element) {
        return getDelegate(element) instanceof DynamicGroupNode;
      }
    };

    buildMatcher();
    myFilter = new ElementFilter.Active.Impl<SimpleNode>() {
      @Override
      public boolean shouldBeShowing(SimpleNode template) {
        return template instanceof TemplateNode && matches((TemplateNode)template);
      }
    };
    final FilteringTreeStructure filteringTreeStructure = new FilteringTreeStructure(myFilter, structure);

    myTreeBuilder = new FilteringTreeBuilder(myTemplatesTree, myFilter, filteringTreeStructure, new Comparator<NodeDescriptor>() {
      @Override
      public int compare(NodeDescriptor o1, NodeDescriptor o2) {
        if (o1 instanceof FilteringTreeStructure.FilteringNode) {
          if (((FilteringTreeStructure.FilteringNode)o1).getDelegate() instanceof GroupNode) {
            String name = ((GroupNode)((FilteringTreeStructure.FilteringNode)o1).getDelegate()).getName();
          }
        }
        return AlphaComparator.INSTANCE.compare(o1, o2);
      }
    }) {

      @Override
      public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
        return myMatchers != null && myMatchers.length > 0;
      }

      @Override
      protected void runBackgroundLoading(@NotNull Runnable runnable) {
        runnable.run();
      }

      @Override
      public boolean isToEnsureSelectionOnFocusGained() {
        return false;
      }
    };

    myTemplatesTree.setRootVisible(false);
    myTemplatesTree.setShowsRootHandles(false);
    myTemplatesTree.putClientProperty("JTree.lineStyle", "None");
    myTemplatesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTemplatesTree.setCellRenderer(new NodeRenderer() {

      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

        SimpleNode node = getSimpleNode(value);
        boolean group = node instanceof GroupNode;
        if (node != null) {
          String name = node.getName();
          if (name != null) {
            append(name, group ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          setIcon(node.getIcon());
        }
        setFont(group ? UIUtil.getListFont().deriveFont(Font.BOLD) : UIUtil.getListFont());
        setBorder(group ? BorderFactory.createEmptyBorder(3, 3, 3, 3) : BorderFactory.createEmptyBorder());
      }
    });

    myTemplatesTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        ProjectTemplate template = getSelectedTemplate();
        myModuleBuilder = template == null ? null : template.createModuleBuilder();
        setupPanels(template);
        mySequence.setType(myModuleBuilder == null ? null : myModuleBuilder.getBuilderId());
        myWizardContext.requestWizardButtonsUpdate();
      }
    });

    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        doFilter();
      }
    });

    myDescriptionPanel.setVisible(false);
    if (myWizardContext.isCreatingNewProject()) {
      addField("Project \u001bformat:", myFormatPanel.getStorageFormatComboBox(), myModulePanel);
    }

    mySplitter = new JBSplitter(false, 0.3f, 0.3f, 0.6f);
    mySplitter.setSplitterProportionKey("select.template.proportion");
    myLeftPanel.setMinimumSize(new Dimension(200, 200));
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myRightPanel);
//    mySettingsPanel.setVisible(false);
//    myExpertPanel.setVisible(false);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent event = e.getInputEvent();
        if (event instanceof KeyEvent) {
          int row = myTemplatesTree.getMaxSelectionRow();
          int toSelect;
          switch (((KeyEvent)event).getKeyCode()) {
            case KeyEvent.VK_UP:
              toSelect = row == 0 ? myTemplatesTree.getRowCount() - 1 : row - 1;
              myTemplatesTree.setSelectionRow(toSelect);
              myTemplatesTree.scrollRowToVisible(toSelect);
              break;
            case KeyEvent.VK_DOWN:
              toSelect = row < myTemplatesTree.getRowCount() - 1 ? row + 1 : 0;
              myTemplatesTree.setSelectionRow(toSelect);
              myTemplatesTree.scrollRowToVisible(toSelect);
              break;
          }
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), mySearchField);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myIsTreeUpdating = true;
        FilteringTreeStructure.FilteringNode node = filteringTreeStructure.createFilteringNode(dynamicGroupNode);
        myTreeBuilder.queueUpdateFrom(node, false);

        TreeState state = SelectTemplateSettings.getInstance().getTreeState();
        if (state != null && !ApplicationManager.getApplication().isUnitTestMode()) {
          state.applyTo(myTemplatesTree, (DefaultMutableTreeNode)myTemplatesTree.getModel().getRoot());
        }
        else {
          myTreeBuilder.expandAll(new Runnable() {
            @Override
            public void run() {
              myTemplatesTree.setSelectionRow(1);
            }
          });
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      SwingUtilities.invokeLater(runnable);
    }
    else {
      myTreeBuilder.getIntialized().doWhenDone(runnable);
    }
  }

  private static NamePathComponent initNamePathComponent(WizardContext context) {
    NamePathComponent component = new NamePathComponent(
      IdeBundle.message("label.project.name"),
      IdeBundle.message("label.project.files.location"),
      IdeBundle.message("title.select.project.file.directory", IdeBundle.message("project.new.wizard.project.identification")),
      IdeBundle.message("description.select.project.file.directory", StringUtil
        .capitalize(IdeBundle.message("project.new.wizard.project.identification"))),
      true, false
    );
    final String baseDir = context.getProjectFileDirectory();
    final String projectName = context.getProjectName();
    final String initialProjectName = projectName != null ? projectName : ProjectWizardUtil.findNonExistingFileName(baseDir, "untitled", "");
    component.setPath(projectName == null ? (baseDir + File.separator + initialProjectName) : baseDir);
    component.setNameValue(initialProjectName);
    component.getNameComponent().select(0, initialProjectName.length());
    return component;
  }

  private void setupPanels(@Nullable ProjectTemplate template) {

    restorePanel(myNamePathComponent, 4);
    restorePanel(myModulePanel, myWizardContext.isCreatingNewProject() ? 8 : 6);
    restorePanel(myExpertPanel, myWizardContext.isCreatingNewProject() ? 1 : 0);
    mySettingsStep = myModuleBuilder == null ? null : myModuleBuilder.modifySettingsStep(this);

    String description = null;
    if (template != null) {
      description = template.getDescription();
      if (StringUtil.isNotEmpty(description)) {
        StringBuilder sb = new StringBuilder("<html><body><font face=\"Verdana\" ");
        sb.append(SystemInfo.isMac ? "" : "size=\"-1\"").append('>');
        sb.append(description).append("</font></body></html>");
        description = sb.toString();
        myDescriptionPane.setText(description);
      }
    }

//    mySettingsPanel.setVisible(template != null);
    myExpertPlaceholder.setVisible(!(myModuleBuilder instanceof TemplateModuleBuilder) &&
                                   !(myModuleBuilder instanceof EmptyModuleBuilder) &&
                                   myExpertPanel.getComponentCount() > 0);
    myDescriptionPanel.setVisible(StringUtil.isNotEmpty(description));

    mySettingsPanel.revalidate();
    mySettingsPanel.repaint();
  }

  private static int restorePanel(JPanel component, int i) {
    int removed = 0;
    while (component.getComponentCount() > i) {
      component.remove(component.getComponentCount() - 1);
      removed++;
    }
    return removed;
  }

  @Override
  public void updateStep() {
    myExpertDecorator.setOn(SelectTemplateSettings.getInstance().EXPERT_MODE);
  }

  @Override
  public void onStepLeaving() {
    TreeState state = TreeState.createOn(myTemplatesTree, (DefaultMutableTreeNode)myTemplatesTree.getModel().getRoot());
    SelectTemplateSettings settings = SelectTemplateSettings.getInstance();
    settings.setTreeState(state);
    settings.EXPERT_MODE = myExpertDecorator.isExpanded();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    ProjectTemplate template = getSelectedTemplate();
    if (template == null) {
      throw new ConfigurationException(StringUtil.capitalize(ProjectBundle.message("project.new.wizard.from.template.error", myWizardContext.getPresentationName())), "Error");
    }

    if (myWizardContext.isCreatingNewProject()) {
      if (!myNamePathComponent.validateNameAndPath(myWizardContext, myFormatPanel.isDefault())) return false;
    }

    if (!validateModulePaths()) return false;
    if (!myWizardContext.isCreatingNewProject()) {
      validateExistingModuleName();
    }

    ValidationInfo info = template.validateSettings();
    if (info != null) {
      throw new ConfigurationException(info.message, "Error");
    }
    if (mySettingsStep != null) {
      return mySettingsStep.validate();
    }
    return true;
  }

  private void doFilter() {
    buildMatcher();
    SimpleNode selectedNode = myTemplatesTree.getSelectedNode();
    final Ref<SimpleNode> node = new Ref<SimpleNode>();
    if (!(selectedNode instanceof TemplateNode) || !matches((TemplateNode)selectedNode)) {
      myTemplatesTree.accept(myTreeBuilder, new SimpleNodeVisitor() {
        @Override
        public boolean accept(SimpleNode simpleNode) {
          FilteringTreeStructure.FilteringNode wrapper = (FilteringTreeStructure.FilteringNode)simpleNode;
          Object delegate = wrapper.getDelegate();
          if (delegate instanceof TemplateNode && matches((TemplateNode)delegate)) {
            node.set((SimpleNode)delegate);
            return true;
          }
          return false;
        }
      });
    }

    myFilter.fireUpdate(node.get(), true, false);
  }

  private boolean matches(TemplateNode template) {
    String name = template.getName() + " " + template.getGroupName();
    String[] words = NameUtil.nameToWords(name);
    Set<Matcher> matched = new HashSet<Matcher>();
    for (String word : words) {
      for (Matcher matcher : myMatchers) {
        if (matcher.matches(word)) {
          matched.add(matcher);
          if (matched.size() == myMatchers.length) return true;
        }
      }
    }
    return false;
  }

  private void buildMatcher() {
    String text = mySearchField.getText();
    myMatchers = ContainerUtil.map2Array(text.split(" "), MinusculeMatcher.class, new Function<String, MinusculeMatcher>() {
      @Override
      public MinusculeMatcher fun(String s) {
        return NameUtil.buildMatcher(s, NameUtil.MatchingCaseSensitivity.NONE);
      }
    });
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    SimpleNode delegate = getSelectedNode();
    return delegate instanceof TemplateNode ? ((TemplateNode)delegate).myTemplate : null;
  }

  @Nullable
  private SimpleNode getSelectedNode() {
    TreePath path = myTemplatesTree.getSelectionPath();
    if (path == null) return null;
    return getSimpleNode(path.getLastPathComponent());
  }

  @Nullable
  private SimpleNode getSimpleNode(Object component) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
    Object userObject = node.getUserObject();
    return getDelegate(userObject);
  }

  private SimpleNode getDelegate(Object userObject) {
    if (!(userObject instanceof FilteringTreeStructure.FilteringNode)) //noinspection ConstantConditions
      return null;
    FilteringTreeStructure.FilteringNode object = (FilteringTreeStructure.FilteringNode)userObject;
    return (SimpleNode)object.getDelegate();
  }

  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchField;
  }

  @Override
  public void updateDataModel() {

    myWizardContext.setProjectBuilder(myModuleBuilder);
    myWizardContext.setProjectName(myNamePathComponent.getNameValue());
    myWizardContext.setProjectFileDirectory(myNamePathComponent.getPath());
    myFormatPanel.updateData(myWizardContext);

    if (myModuleBuilder != null) {
      final String moduleName = getModuleName();
      myModuleBuilder.setName(moduleName);
      myModuleBuilder.setModuleFilePath(
        FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      myModuleBuilder.setContentEntryPath(FileUtil.toSystemIndependentName(getModuleContentRoot()));
      if (myModuleBuilder instanceof TemplateModuleBuilder) {
        myWizardContext.setProjectStorageFormat(StorageScheme.DIRECTORY_BASED);
      }
    }

    if (mySettingsStep != null) {
      mySettingsStep.updateDataModel();
    }
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myTreeBuilder);
  }

  @Override
  public String getName() {
    return "Template Type";
  }

  private void createUIComponents() {
    mySearchField = new SearchTextField(false);
  }

  @Override
  public WizardContext getContext() {
    return myWizardContext;
  }

  @Override
  public void addSettingsField(@NotNull String label, @NotNull JComponent field) {

    JPanel panel = myWizardContext.isCreatingNewProject() ? myNamePathComponent : myModulePanel;
    addField(label, field, panel);
  }

  private static void addField(String label, JComponent field, JPanel panel) {
    JLabel jLabel = new JBLabel(label);
    jLabel.setLabelFor(field);
    panel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                 GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
    panel.add(field, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 0), 0, 0));
  }

  @Override
  public void addSettingsComponent(@NotNull JComponent component) {
    myNamePathComponent.add(component, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  @Override
  public void addExpertPanel(@NotNull JComponent panel) {
    myExpertPanel.add(panel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                    GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  @Override
  public void addExpertField(@NotNull String label, @NotNull JComponent field) {
    JPanel panel = myWizardContext.isCreatingNewProject() ? myModulePanel : myExpertPanel;
    addField(label, field, panel);
  }

  private class DynamicGroupNode extends GroupNode {

    private final ProjectTemplatesFactory myFactory;

    private DynamicGroupNode(TemplatesGroup group, ProjectTemplatesFactory factory) {
      super(group, Collections.<ProjectTemplate>emptyList());
      myFactory = factory;
    }

    @Override
    public SimpleNode[] getChildren() {
      if (!myIsTreeUpdating) return NO_CHILDREN;
      ProjectTemplate[] templates = myFactory.createTemplates(myName, myWizardContext);
      if (templates.length == 0) {
        return new SimpleNode[]{new NullNode() {
          @Override
          public String getName() {
            return "<no samples found>";
          }
        }};
      }
      return getNodes(Arrays.asList(templates));
    }
  }

  private static class GroupNode extends SimpleNode {
    private final TemplatesGroup myGroup;
    private final Collection<ProjectTemplate> myTemplates;

    public GroupNode(TemplatesGroup group, Collection<ProjectTemplate> templates) {
      myGroup = group;
      myTemplates = templates;
      setIcon(group.getIcon());
    }

    @Override
    public SimpleNode[] getChildren() {
      return getNodes(myTemplates);
    }

    protected SimpleNode[] getNodes(Collection<ProjectTemplate> templates) {
      List<SimpleNode> children = new ArrayList<SimpleNode>();
      for (ProjectTemplate template : templates) {
        children.add(new TemplateNode(this, template));
      }
      return children.toArray(new SimpleNode[children.size()]);
    }

    @Override
    public String getName() {
      return myGroup.getName();
    }
  }

  private static class TemplateNode extends NullNode {

    private final GroupNode myGroupNode;
    private final ProjectTemplate myTemplate;

    public TemplateNode(GroupNode groupNode, ProjectTemplate template) {
      myGroupNode = groupNode;
      myTemplate = template;
      setIcon(template.createModuleBuilder().getNodeIcon());
    }

    @Override
    public boolean isAlwaysLeaf() {
      return true;
    }

    @Override
    public String getName() {
      return myTemplate.getName();
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return new Object[] {getGroupName(), getName() };
    }

    String getGroupName() {
      return myGroupNode.getName();
    }
  }

  public void bindModuleSettings() {

    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(myNamePathComponent.getNameValue());
        }
      }
    });

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"), ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(myNamePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
        String path = getDefaultBaseDir(myWizardContext);
        final String moduleName = getModuleName();
        if (path.length() > 0 && !Comparing.strEqual(moduleName, myNamePathComponent.getNameValue())) {
          path += "/" + moduleName;
        }
        if (!myContentRootChangedByUser) {
          final boolean f = myModuleNameChangedByUser;
          myModuleNameChangedByUser = true;
          setModuleContentRoot(path);
          myModuleNameChangedByUser = f;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(path);
        }
      }
    });
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myContentRootDocListenerEnabled) {
          myContentRootChangedByUser = true;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(getModuleContentRoot());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(getModuleContentRoot());
          final int idx = path.lastIndexOf("/");

          boolean f = myContentRootChangedByUser;
          myContentRootChangedByUser = true;

          boolean i = myImlLocationChangedByUser;
          myImlLocationChangedByUser = true;

          setModuleName(idx >= 0 ? path.substring(idx + 1) : "");

          myContentRootChangedByUser = f;
          myImlLocationChangedByUser = i;
        }
      }
    });

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"), ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myNamePathComponent.getPath());
        }
      }
    });
    if (myWizardContext.isCreatingNewProject()) {
      setModuleName(myNamePathComponent.getNameValue());
      setModuleContentRoot(myNamePathComponent.getPath());
      setImlFileLocation(myNamePathComponent.getPath());
    } else {
      final Project project = myWizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), myWizardContext.getProjectName()) && !myWizardContext.isCreatingNewProject() && myWizardContext.getProjectName() != null) {
          moduleName = ProjectWizardUtil.findNonExistingFileName(myWizardContext.getProjectFileDirectory(), myWizardContext.getProjectName(), "");
          contentRoot = myWizardContext.getProjectFileDirectory();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        setImlFileLocation(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }
  }

  private void validateExistingModuleName() throws ConfigurationException {
    final String moduleName = getModuleName();
    final Module module;
    final ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(myWizardContext.getProject());
    if (fromConfigurable != null) {
      module = fromConfigurable.getModulesConfig().getModule(moduleName);
    }
    else {
      module = ModuleManager.getInstance(myWizardContext.getProject()).findModuleByName(moduleName);
    }
    if (module != null) {
      throw new ConfigurationException("Module \'" + moduleName + "\' already exist in project. Please, specify another name.");
    }
  }

  private boolean validateModulePaths() throws ConfigurationException {
    final String moduleName = getModuleName();
    final String moduleFileDirectory = myModuleFileLocation.getText();
    if (moduleFileDirectory.length() == 0) {
      throw new ConfigurationException("Enter module file location");
    }
    if (moduleName.length() == 0) {
      throw new ConfigurationException("Enter a module name");
    }

    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory,
                                                      myImlLocationChangedByUser)) {
      return false;
    }
    if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(),
                                                      myContentRootChangedByUser)) {
      return false;
    }

    File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    if (moduleFile.exists()) {
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(),
                                                              IdeBundle.message("project.new.wizard.module.identification")),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      if (answer != 0) {
        return false;
      }
    }
    return true;
  }

  protected String getModuleContentRoot() {
    return myModuleContentRoot.getText();
  }

  private String getDefaultBaseDir(WizardContext wizardContext) {
    if (wizardContext.isCreatingNewProject()) {
      return myNamePathComponent.getPath();
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
  }

  private void setImlFileLocation(final String path) {
    myImlLocationDocListenerEnabled = false;
    myModuleFileLocation.setText(FileUtil.toSystemDependentName(path));
    myImlLocationDocListenerEnabled = true;
  }

  private void setModuleContentRoot(final String path) {
    myContentRootDocListenerEnabled = false;
    myModuleContentRoot.setText(FileUtil.toSystemDependentName(path));
    myContentRootDocListenerEnabled = true;
  }

  public void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  @NotNull
  public JTextField getModuleNameField() {
    return myModuleName;
  }

  protected String getModuleName() {
    return myModuleName.getText().trim();
  }

  @TestOnly
  public boolean setSelectedTemplate(String group, String name) {
    final ComparableObject.Impl test = new ComparableObject.Impl(group, name);
    return myTemplatesTree.select(myTreeBuilder, new SimpleNodeVisitor() {
      @Override
      public boolean accept(SimpleNode simpleNode) {
        SimpleNode node = getDelegate(simpleNode);
        //noinspection EqualsBetweenInconvertibleTypes
        return test.equals(node);
      }
    }, true);
  }

  @TestOnly
  @Nullable
  public ModuleWizardStep getSettingsStep() {
    return mySettingsStep;
  }

  @Override
  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }
}
