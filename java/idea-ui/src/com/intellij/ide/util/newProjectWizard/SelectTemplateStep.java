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
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.platform.templates.ArchivedTemplatesFactory;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.Matcher;
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

  private JPanel myPanel;
  private SimpleTree myTemplatesTree;
  private JPanel mySettingsPanel;
  private SearchTextField mySearchField;
  private JTextPane myDescriptionPane;
  private JPanel myDescriptionPanel;

  private JPanel myExpertPlaceholder;
  private JPanel myExpertPanel;
  private final HideableDecorator myExpertDecorator;

  private final NamePathComponent myNamePathComponent;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myModulePanel;

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
  private MinusculeMatcher[] myMatchers;
  @Nullable
  private ModuleBuilder myModuleBuilder;

  public SelectTemplateStep(WizardContext context, StepSequence sequence) {

    myWizardContext = context;
    mySequence = sequence;
    Messages.installHyperlinkSupport(myDescriptionPane);

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

    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    final MultiMap<String, ProjectTemplate> groups = new MultiMap<String, ProjectTemplate>();
    for (ProjectTemplatesFactory factory : factories) {
      for (String group : factory.getGroups()) {
        groups.putValues(group, Arrays.asList(factory.createTemplates(group, context)));
      }
    }
    final MultiMap<String, ProjectTemplate> sorted = new MultiMap<String, ProjectTemplate>();
    // put single leafs under "Other"
    for (Map.Entry<String, Collection<ProjectTemplate>> entry : groups.entrySet()) {
      Collection<ProjectTemplate> templates = entry.getValue();
      if (templates.size() == 1 &&
          !ArchivedTemplatesFactory.CUSTOM_GROUP.equals(entry.getKey())) {

        if (!(templates.iterator().next() instanceof ArchivedProjectTemplate)) {
          sorted.putValues(ProjectTemplatesFactory.OTHER_GROUP, templates);
          continue;
        }
      }
      sorted.put(entry.getKey(), templates);
    }

    SimpleTreeStructure.Impl structure = new SimpleTreeStructure.Impl(new SimpleNode() {
      @Override
      public SimpleNode[] getChildren() {
        return ContainerUtil.map2Array(sorted.entrySet(), NO_CHILDREN, new Function<Map.Entry<String, Collection<ProjectTemplate>>, SimpleNode>() {
          @Override
          public SimpleNode fun(Map.Entry<String, Collection<ProjectTemplate>> entry) {
            return new GroupNode(entry.getKey(), entry.getValue());
          }
        });
      }
    });

    buildMatcher();
    myFilter = new ElementFilter.Active.Impl<SimpleNode>() {
      @Override
      public boolean shouldBeShowing(SimpleNode template) {
        return matches(template);
      }
    };
    myTreeBuilder = new FilteringTreeBuilder(myTemplatesTree, myFilter, structure, new Comparator<NodeDescriptor>() {
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
        return false;
      }

      @Override
      public boolean isToEnsureSelectionOnFocusGained() {
        return false;
      }
    };

    myTemplatesTree.setRootVisible(false);
//    myTemplatesTree.setShowsRootHandles(false);
    myTemplatesTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        SimpleNode node = getSimpleNode(value);
        if (node != null) {
          String name = node.getName();
          if (name != null) {
            append(name);
          }
        }
        if (node instanceof GroupNode) {
//          setIcon(UIUtil.getTreeIcon(expanded));
        }
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

    //if (myTemplatesTree.getModel().getSize() > 0) {
    //  myTemplatesTree.setSelectedIndex(0);
    //}
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        doFilter();
      }
    });
    myDescriptionPanel.setVisible(false);
    mySettingsPanel.setVisible(false);
    myExpertPanel.setVisible(false);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent event = e.getInputEvent();
        if (event instanceof KeyEvent) {
          int row = myTemplatesTree.getMaxSelectionRow();
          switch (((KeyEvent)event).getKeyCode()) {
            case KeyEvent.VK_UP:
              myTemplatesTree.setSelectionRow(row == 0 ? myTemplatesTree.getRowCount() - 1 : row - 1);
              break;
            case KeyEvent.VK_DOWN:
              myTemplatesTree.setSelectionRow(row < myTemplatesTree.getRowCount() - 1 ? row + 1 : 0);
              break;
          }
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), mySearchField);

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
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
    });
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
    restorePanel(myModulePanel, 6);
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

    mySettingsPanel.setVisible(template != null);
    myExpertPlaceholder.setVisible(template != null && myExpertPanel.getComponentCount() > 0);
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
    myTreeBuilder.queueUpdate();
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
      throw new ConfigurationException(ProjectBundle.message("project.new.wizard.from.template.error", myWizardContext.getPresentationName()), "Error");
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
    if (!(selectedNode instanceof TemplateNode) || !matches(selectedNode)) {
      myTemplatesTree.accept(myTreeBuilder, new SimpleNodeVisitor() {
        @Override
        public boolean accept(SimpleNode simpleNode) {
          FilteringTreeStructure.FilteringNode wrapper = (FilteringTreeStructure.FilteringNode)simpleNode;
          Object delegate = wrapper.getDelegate();
          if (delegate instanceof TemplateNode && matches((SimpleNode)delegate)) {
            node.set((SimpleNode)delegate);
            return true;
          }
          return false;
        }
      });
    }

    myFilter.fireUpdate(node.get(), true, false);
  }

  private boolean matches(SimpleNode template) {
    String name = template.getName();
    if (name == null) return false;
    String[] words = NameUtil.nameToWords(name);
    for (String word : words) {
      for (Matcher matcher : myMatchers) {
        if (matcher.matches(word)) return true;
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
    return myPanel;
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

    if (myModuleBuilder != null) {
      final String moduleName = getModuleName();
      myModuleBuilder.setName(moduleName);
      myModuleBuilder.setModuleFilePath(
        FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      myModuleBuilder.setContentEntryPath(FileUtil.toSystemIndependentName(getModuleContentRoot()));
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
  public void addSettingsField(String label, JComponent field) {

    JLabel jLabel = new JBLabel(label);
    jLabel.setLabelFor(field);
    JPanel panel = myWizardContext.isCreatingNewProject() ? myNamePathComponent : myModulePanel;
    panel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST,
                                                 GridBagConstraints.NONE, new Insets(0, 0, 5, 0), 0, 0));
    panel.add(field, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 0), 0, 0));
  }

  @Override
  public void addSettingsComponent(JComponent component) {
    myNamePathComponent.add(component, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  @Override
  public void addExpertPanel(JComponent panel) {
    myExpertPanel.add(panel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                    GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

  private static class GroupNode extends SimpleNode {
    private final String myGroup;
    private final Collection<ProjectTemplate> myTemplates;

    public GroupNode(String group, Collection<ProjectTemplate> templates) {
      myGroup = group;
      myTemplates = templates;
    }

    @Override
    public SimpleNode[] getChildren() {
      List<SimpleNode> children = new ArrayList<SimpleNode>();
      for (ProjectTemplate template : myTemplates) {
        children.add(new TemplateNode(this, template));
      }
      return children.toArray(new SimpleNode[children.size()]);
    }

    @Override
    public String getName() {
      return myGroup;
    }
  }

  private static class TemplateNode extends NullNode {

    private final GroupNode myGroupNode;
    private final ProjectTemplate myTemplate;

    public TemplateNode(GroupNode groupNode, ProjectTemplate template) {
      myGroupNode = groupNode;
      myTemplate = template;
    }

    @Override
    public String getName() {
      return myTemplate.getName();
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return new Object[] { myGroupNode.getName(), getName() };
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

  protected String getModuleName() {
    return myModuleName.getText().trim();
  }

  public boolean setSelectedTemplate(String group, String name) {
    final ComparableObject.Impl test = new ComparableObject.Impl(group, name);
    return myTemplatesTree.select(myTreeBuilder, new SimpleNodeVisitor() {
      @Override
      public boolean accept(SimpleNode simpleNode) {
        SimpleNode node = getDelegate(simpleNode);
        return test.equals(node);
      }
    }, true);
  }

  @TestOnly
  @Nullable
  public ModuleWizardStep getSettingsStep() {
    return mySettingsStep;
  }

  @TestOnly
  public void dumpTree() {
    myTemplatesTree.accept(myTreeBuilder, new SimpleNodeVisitor() {
      @Override
      public boolean accept(SimpleNode simpleNode) {
        System.out.println(simpleNode.getName());
        return false;
      }
    });
  }
}
