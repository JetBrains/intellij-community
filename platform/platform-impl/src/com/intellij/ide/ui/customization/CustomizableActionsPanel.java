// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ide.ui.laf.darcula.ui.DarculaSeparatorUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ImageLoader;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.ide.ui.customization.ActionUrl.*;
import static com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*;

public class CustomizableActionsPanel {
  private final JPanel myPanel = new BorderLayoutPanel(5, 5);
  protected JTree myActionsTree;
  private final JPanel myTopPanel = new BorderLayoutPanel();
  protected CustomActionsSchema mySelectedSchema;

  public CustomizableActionsPanel() {
    //noinspection HardCodedStringLiteral
    @SuppressWarnings("DialogTitleCapitalization")
    Group rootGroup = new Group("root", null, null);
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    MyActionsTreeModel model = new MyActionsTreeModel(root);
    myActionsTree = new Tree(model);
    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    myActionsTree.setCellRenderer(createDefaultRenderer());
    RowsDnDSupport.install(myActionsTree, model);

    patchActionsTreeCorrespondingToSchema(root);

    TreeExpansionMonitor.install(myActionsTree);
    myTopPanel.add(setupFilterComponent(myActionsTree), BorderLayout.WEST);
    myTopPanel.add(createToolbar(), BorderLayout.CENTER);

    myPanel.add(myTopPanel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myActionsTree), BorderLayout.CENTER);
  }

  private ActionToolbarImpl createToolbar() {
    ActionGroup addGroup = new DefaultActionGroup(new AddActionActionTreeSelectionAction()/*, new AddGroupAction()*/, new AddSeparatorAction());
    addGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.add.action.group"));
    addGroup.getTemplatePresentation().setIcon(AllIcons.General.Add);
    addGroup.setPopup(true);
    ActionGroup restoreGroup = getRestoreGroup();
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(addGroup, new RemoveAction(), new EditIconAction(), new MoveUpAction(), new MoveDownAction(), restoreGroup), true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    toolbar.setTargetComponent(myTopPanel);
    return toolbar;
  }

  @NotNull
  protected ActionGroup getRestoreGroup() {
    ActionGroup restoreGroup = new DefaultActionGroup(new RestoreSelectionAction(), new RestoreAllAction());
    restoreGroup.setPopup(true);
    restoreGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.restore.action.group"));
    restoreGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
    return restoreGroup;
  }

  static FilterComponent setupFilterComponent(JTree tree) {
    final TreeSpeedSearch mySpeedSearch = new TreeSpeedSearch(tree, new TreePathStringConvertor(), true) {
      @Override
      public boolean isPopupActive() {
        return /*super.isPopupActive()*/true;
      }

      @Override
      public void showPopup(String searchText) {
        //super.showPopup(searchText);
      }

      @Override
      protected boolean isSpeedSearchEnabled() {
        return /*super.isSpeedSearchEnabled()*/false;
      }

      @Override
      public void showPopup() {
        //super.showPopup();
      }
    };
    final FilterComponent filterComponent = new FilterComponent("CUSTOMIZE_ACTIONS", 5) {
      @Override
      public void filter() {
        mySpeedSearch.findAndSelectElement(getFilter());
        mySpeedSearch.getComponent().repaint();
      }
    };
    JTextField textField = filterComponent.getTextEditor();
    int[] keyCodes = {KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_UP, KeyEvent.VK_DOWN};
    for (int keyCode : keyCodes) {
      new DumbAwareAction(){
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String filter = filterComponent.getFilter();
          if (!StringUtil.isEmpty(filter)) {
            mySpeedSearch.adjustSelection(keyCode, filter);
          }
        }
      }.registerCustomShortcutSet(keyCode, 0, textField);

    }
    return filterComponent;
  }

  private void addCustomizedAction(ActionUrl url) {
    mySelectedSchema.addAction(url);
  }

  private static boolean isMoveSupported(JTree tree, int dir) {
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      DefaultMutableTreeNode parent = null;
      for (TreePath treePath : selectionPaths)
        if (treePath.getLastPathComponent() != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          if (parent == null) {
            parent = (DefaultMutableTreeNode)node.getParent();
          }
          if (parent == null || parent != node.getParent()) {
            return false;
          }
          if (dir > 0) {
            if (parent.getIndex(node) == parent.getChildCount() - 1) {
              return false;
            }
          }
          else {
            if (parent.getIndex(node) == 0) {
              return false;
            }
          }
        }
      return true;
    }
    return false;
  }


  public JPanel getPanel() {
    return myPanel;
  }

  public void apply() throws ConfigurationException {
    final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
    if (mySelectedSchema != null) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    restorePathsAfterTreeOptimization(treePaths);
    updateGlobalSchema();
    CustomActionsSchema.getInstance().initActionIcons();
    CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
    if (SystemInfo.isMac) {
      TouchbarSupport.reloadAllActions();
    }

    CustomActionsListener.fireSchemaChanged();
  }

  protected void updateGlobalSchema() {
    CustomActionsSchema.getInstance().copyFrom(mySelectedSchema);
  }

  protected void updateLocalSchema(CustomActionsSchema localSchema) {
  }

  private void restorePathsAfterTreeOptimization(final List<? extends TreePath> treePaths) {
    for (final TreePath treePath : treePaths) {
      myActionsTree.expandPath(CustomizationUtil.getPathByUserObjects(myActionsTree, treePath));
    }
  }

  public void reset() {
    reset(true);
  }

  public void resetToDefaults() {
    reset(false);
  }

  private void reset(boolean restoreLastState) {
    List<String> expandedIds = toActionIDs(TreeUtil.collectExpandedPaths(myActionsTree));
    List<String> selectedIds = toActionIDs(TreeUtil.collectSelectedPaths(myActionsTree));
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myActionsTree.getModel().getRoot();
    TreeUtil.treeNodeTraverser(root).traverse()
      .filter(node -> node instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode)node).getUserObject() instanceof Pair)
      .forEach(node -> doSetIcon(mySelectedSchema, (DefaultMutableTreeNode)node, null, null));
    CustomActionsSchema source = restoreLastState ? CustomActionsSchema.getInstance() : new CustomActionsSchema();
    if (mySelectedSchema == null) mySelectedSchema = new CustomActionsSchema();
    mySelectedSchema.copyFrom(source);
    updateLocalSchema(mySelectedSchema);
    mySelectedSchema.initActionIcons();
    patchActionsTreeCorrespondingToSchema(root);
    if (needExpandAll()) {
      new DefaultTreeExpander(myActionsTree).expandAll();
    } else {
      TreeUtil.restoreExpandedPaths(myActionsTree, toTreePaths(root, expandedIds));
    }
    TreeUtil.selectPaths(myActionsTree, toTreePaths(root, selectedIds));
    TreeUtil.ensureSelection(myActionsTree);
  }

  private static List<String> toActionIDs(List<TreePath> paths) {
    return ContainerUtil.map(paths, path -> getActionId((DefaultMutableTreeNode)path.getLastPathComponent()));
  }

  private static List<TreePath> toTreePaths(DefaultMutableTreeNode root, List<String> actionIDs) {
    List<TreePath> result = new ArrayList<>();
    for (String actionId : actionIDs) {
      DefaultMutableTreeNode treeNode = TreeUtil.findNode(root, node -> Objects.equals(actionId, getActionId(node)));
      if (treeNode != null) result.add(TreeUtil.getPath(root, treeNode));
    }
    return result;
  }

  protected boolean needExpandAll() {
    return false;
  }

  public boolean isModified() {
    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    return CustomActionsSchema.getInstance().isModified(mySelectedSchema);
  }

  protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillCorrectedActionGroups(root);
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private static class TreePathStringConvertor implements Convertor<TreePath, String> {
    @Override
    public String convert(TreePath o) {
      Object node = o.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        Object object = ((DefaultMutableTreeNode)node).getUserObject();
        if (object instanceof Group) return ((Group)object).getName();
        if (object instanceof QuickList) return ((QuickList)object).getName();
        String actionId;
        if (object instanceof String) {
          actionId = (String)object;
        }
        else if (object instanceof Pair) {
          actionId = (String)((Pair<?, ?>)object).first;
        }
        else {
          return "";
        }
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action != null) {
          return action.getTemplatePresentation().getText();
        }
      }
      return "";
    }
  }

  static TreeCellRenderer createDefaultRenderer() {
    return new MyTreeCellRenderer();
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        CustomizationUtil.acceptObjectIconAndText(userObject, (text, description, icon) -> {
          append(text);
          if (description != null) {
            append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES, false);
            append(description, SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
          setIcon(icon);
        });
        setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      }
    }
  }

  @Nullable
  private static String getActionId(DefaultMutableTreeNode node) {
    return (String)(node.getUserObject() instanceof String ? node.getUserObject() :
                    node.getUserObject() instanceof Pair ? ((Pair<?, ?>)node.getUserObject()).first :
                    node.getUserObject() instanceof Group ? ((Group)node.getUserObject()).getId() :
                    null);
  }

  static boolean doSetIcon(@NotNull CustomActionsSchema schema,
                           DefaultMutableTreeNode node,
                           @Nullable String path,
                           @Nullable Component component) {
    String actionId = getActionId(node);
    if (actionId == null) return false;
    if (StringUtil.isEmpty(path)) {
      node.setUserObject(Pair.create(actionId, null));
      schema.removeIconCustomization(actionId);
      return true;
    }
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(actionId);
    if (action == null) return false;

    AnAction reuseFrom = actionManager.getAction(path);
    if (reuseFrom != null) {
      node.setUserObject(Pair.create(actionId, reuseFrom.getTemplatePresentation().getIcon()));
      schema.addIconCustomization(actionId, path);
    }
    else {
      Icon icon;
      try {
        icon = loadCustomIcon(path);
      }
      catch (IOException e) {
        Messages.showErrorDialog(component, e.getLocalizedMessage(),
                                 IdeBundle.message("title.choose.action.icon"));
        return false;
      }
      if (icon != null) {
        node.setUserObject(Pair.create(actionId, icon));
        schema.addIconCustomization(actionId, path);
      }
    }
    return true;
  }

  private static @Nullable Icon loadCustomIcon(@NotNull String path) throws IOException {
    String independentPath = path.replace(File.separatorChar, '/');
    String urlString = independentPath.contains(":") ? independentPath : "file:" + independentPath;
    URL url = new URL(null, urlString);
    Image image = ImageLoader.loadCustomIcon(url);
    return image != null ? new JBImageIcon(image) : null;
  }

  static class IconInfo {
    final @Nullable Icon icon;
    final @NotNull String text;
    private final @Nullable String actionId;
    private final @Nullable String iconPath;

    IconInfo(@Nullable Icon icon, @NotNull String text, @Nullable String actionId, @Nullable String iconPath) {
      this.icon = icon;
      this.text = text;
      this.actionId = actionId;
      this.iconPath = iconPath;
    }

    public @NotNull String getIconReference() {
      return actionId != null ? actionId : Objects.requireNonNull(iconPath);
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private static final IconInfo SEPARATOR = new IconInfo(null, "", "", null);

  private static List<IconInfo> getDefaultIcons() {
    List<IconInfo> icons = new ArrayList<>();
    icons.add(getIconInfo(AllIcons.Toolbar.Unknown, "Default icon"));
    icons.add(getIconInfo(AllIcons.General.Add, "Add"));
    icons.add(getIconInfo(AllIcons.General.Remove, "Remove"));
    icons.add(getIconInfo(AllIcons.Actions.Edit, "Edit"));
    icons.add(getIconInfo(AllIcons.General.Filter, "Filter"));
    icons.add(getIconInfo(AllIcons.Actions.Find, "Find"));
    icons.add(getIconInfo(AllIcons.General.GearPlain, "Gear plain"));
    icons.add(getIconInfo(AllIcons.Actions.ListFiles, "List files"));
    icons.add(getIconInfo(AllIcons.ToolbarDecorator.Export, "Export"));
    icons.add(getIconInfo(AllIcons.ToolbarDecorator.Import, "Import"));
    return ContainerUtil.skipNulls(icons);
  }

  private static @Nullable IconInfo getIconInfo(Icon icon, String text) {
    URL iconUrl = ((IconLoader.CachedImageIcon)icon).getURL();
    if (iconUrl != null) {
      return new IconInfo(icon, text, null, iconUrl.toString());
    }
    return null;
  }

  private static List<IconInfo> getAvailableIcons() {
    ActionManager actionManager = ActionManager.getInstance();
    return ContainerUtil.mapNotNull(actionManager.getActionIdList(""), actionId -> {
      AnAction action = Objects.requireNonNull(actionManager.getActionOrStub(actionId));
      Icon icon = action.getTemplatePresentation().getIcon();
      if (icon == null) return null;
      return new IconInfo(icon, Objects.requireNonNullElse(StringUtil.nullize(action.getTemplateText()), actionId), actionId, null);
    });
  }

  private static List<IconInfo> getAllIcons() {
    List<IconInfo> defaultIcons = getDefaultIcons();
    List<IconInfo> icons = new ArrayList<>(defaultIcons);
    icons.add(SEPARATOR);
    List<IconInfo> availableIcons = getAvailableIcons();
    availableIcons.sort((a, b) -> a.text.compareToIgnoreCase(b.text));
    for (IconInfo info : availableIcons) {
      if (!ContainerUtil.exists(defaultIcons, it -> it.icon == info.icon)) {
        icons.add(info);
      }
    }
    return icons;
  }

  static ComboBox<IconInfo> createBrowseIconsComboBox() {
    List<IconInfo> icons = getAllIcons();
    // Overriding of selecting items required to prohibit selecting of SEPARATOR items
    ComboBox<IconInfo> comboBox = new ComboBox<>(icons.toArray(new IconInfo[0])) {
      @Override
      public void setSelectedIndex(int anIndex) {
        if (anIndex == -1) {
          setSelectedItem(null);
        }
        else if (anIndex < -1 || anIndex >= dataModel.getSize()) {
          throw new IllegalArgumentException("setSelectedIndex: " + anIndex + " out of bounds");
        }
        else {
          Object item = dataModel.getElementAt(anIndex);
          if (item != SEPARATOR) {
            setSelectedItem(item);
          }
        }
      }

      @Override
      public void updateUI() {
        setUI(new DarculaComboBoxUI() {
          @Override
          protected void selectNextPossibleValue() {
            int curInd = comboBox.isPopupVisible() ? listBox.getSelectedIndex() : comboBox.getSelectedIndex();
            selectPossibleValue(curInd, true);
          }

          @Override
          protected void selectPreviousPossibleValue() {
            int curInd = comboBox.isPopupVisible() ? listBox.getSelectedIndex() : comboBox.getSelectedIndex();
            selectPossibleValue(curInd, false);
          }

          private void selectPossibleValue(int curInd, boolean next) {
            if (next && curInd < comboBox.getModel().getSize() - 1) {
              trySelectValue(curInd + 1, true);
            }
            else if (!next && curInd > 0) {
              trySelectValue(curInd - 1, false);
            }
          }

          private void trySelectValue(int ind, boolean next) {
            Object item = comboBox.getItemAt(ind);
            if (item != SEPARATOR) {
              listBox.setSelectedIndex(ind);
              listBox.ensureIndexIsVisible(ind);
              if (comboBox.isPopupVisible()) {
                comboBox.setSelectedIndex(ind);
              }
              comboBox.repaint();
            }
            else {
              selectPossibleValue(ind, next);
            }
          }
        });
      }
    };
    comboBox.setSwingPopup(false); // in this case speed search will filter the list of items
    comboBox.setEditable(true);

    comboBox.setEditor(new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        ExtendableTextField textField = new ExtendableTextField() {
          @Override
          public void requestFocus() {
            // it is required to move focus back to comboBox because otherwise speed search will not work
            comboBox.requestFocus();
          }
        };
        textField.setBorder(null);
        textField.setEditable(false);
        textField.addBrowseExtension(() -> {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("svg");
          descriptor.setTitle(IdeBundle.message("title.browse.icon"));
          descriptor.setDescription(IdeBundle.message("prompt.browse.icon.for.selected.action"));
          VirtualFile iconFile = FileChooser.chooseFile(descriptor, null, null);
          if (iconFile != null) {
            Icon icon = null;
            try {
              icon = loadCustomIcon(iconFile.getPath());
            }
            catch (IOException ex) {
              Logger.getInstance(CustomizableActionsPanel.class).warn("Failed to load icon from disk, path: " + iconFile.getPath(), ex);
            }
            if (icon == null) {
              icon = IconManager.getInstance().getStubIcon();
            }
            IconInfo info = new IconInfo(icon, iconFile.getName(), null, iconFile.getPath());
            DefaultComboBoxModel<IconInfo> model = (DefaultComboBoxModel<IconInfo>)comboBox.getModel();
            int separatorInd = model.getIndexOf(SEPARATOR);
            model.insertElementAt(info, separatorInd + 1);
            comboBox.setSelectedIndex(separatorInd + 1);
          }
        }, null);
        textField.addExtension(new ExtendableTextComponent.Extension() {
          @Override
          public Icon getIcon(boolean hovered) {
            Object selectedItem = comboBox.getSelectedItem();
            if (selectedItem instanceof IconInfo) {
              return ((IconInfo)selectedItem).icon;
            }
            return null;
          }

          @Override
          public boolean isIconBeforeText() {
            return true;
          }
        });
        return textField;
      }
    });

    comboBox.setRenderer(new ColoredListCellRenderer<IconInfo>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends IconInfo> list,
                                                    IconInfo value,
                                                    int index,
                                                    boolean selected,
                                                    boolean hasFocus) {
        if (value == SEPARATOR) {
          return new JSeparator(SwingConstants.HORIZONTAL) {
            @Override
            public void updateUI() {
              setUI(new DarculaSeparatorUI() {
                @Override
                protected int getStripeIndent() {
                  return 0;
                }

                @Override
                public Dimension getPreferredSize(JComponent c) {
                  return JBUI.size(0, 1);
                }
              });
            }
          };
        }
        else {
          return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
        }
      }

      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends IconInfo> list,
                                           IconInfo value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value != null) {
          setIcon(value.icon);
          append(value.text);
        }
      }
    });

    ComboboxSpeedSearch.installSpeedSearch(comboBox, actionInfo -> actionInfo.text);
    return comboBox;
  }

  private static TextFieldWithBrowseButton createBrowseField() {
    TextFieldWithBrowseButton textField = new TextFieldWithBrowseButton();
    textField.setPreferredSize(new Dimension(200, textField.getPreferredSize().height));
    textField.setMinimumSize(new Dimension(200, textField.getPreferredSize().height));
    final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return file != null && (file.getName().endsWith(".png") || file.getName().endsWith(".svg"));
      }
    };
    textField.addBrowseFolderListener(IdeBundle.message("title.browse.icon"), IdeBundle.message("prompt.browse.icon.for.selected.action"),
                                      null,
                                      fileChooserDescriptor);
    InsertPathAction.addTo(textField.getTextField(), fileChooserDescriptor);
    if (textField.getTextField() instanceof ExtendableTextComponent) {
      ExtendableTextComponent.Extension extension = ExtendableTextComponent.Extension.create(
        AllIcons.General.CopyHovered, AllIcons.Actions.Copy, null, () -> {
          ActionManager actionManager = ActionManager.getInstance();
          List<Trinity<Icon, String, String>> list = ContainerUtil.mapNotNull(actionManager.getActionIdList(""), o -> {
            AnAction action = Objects.requireNonNull(actionManager.getActionOrStub(o));
            Icon icon = action.getTemplatePresentation().getIcon();
            if (icon == null) return null;
            return Trinity.create(icon, Objects.requireNonNullElse(StringUtil.nullize(action.getTemplateText()), o), o);
          });
          JBPopupFactory.getInstance().createPopupChooserBuilder(list)
            .setRenderer(new ColoredListCellRenderer<Trinity<Icon, String, String>>() {
              @Override
              protected void customizeCellRenderer(@NotNull JList<? extends Trinity<Icon, String, String>> list,
                                                   Trinity<Icon, String, String> value,
                                                   int index,
                                                   boolean selected,
                                                   boolean hasFocus) {
                setIcon(value.first);
                //noinspection HardCodedStringLiteral
                append(value.second);
              }
            })
            .setNamerForFiltering(value -> value.second)
            .setItemChosenCallback(value -> textField.setText(value.third))
            .createPopup()
            .showUnderneathOf(textField);
        });
      ((ExtendableTextComponent)textField.getTextField()).addExtension(extension);
    }
    return textField;
  }

  private class EditIconDialog extends DialogWrapper {
    private final DefaultMutableTreeNode myNode;
    protected TextFieldWithBrowseButton myTextField;

    protected EditIconDialog(DefaultMutableTreeNode node) {
      super(false);
      setTitle(IdeBundle.message("title.choose.action.icon"));
      init();
      myNode = node;
      final String actionId = getActionId(node);
      if (actionId != null) {
        final String iconPath = mySelectedSchema.getIconPath(actionId);
        myTextField.setText(FileUtil.toSystemDependentName(iconPath));
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myTextField.getChildComponent();
    }

    @Override
    protected String getDimensionServiceKey() {
      return getClass().getName();
    }

    @Override
    protected JComponent createCenterPanel() {
      myTextField = createBrowseField();
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myTextField, BorderLayout.NORTH);
      return northPanel;
    }

    @Override
    protected void doOKAction() {
      if (myNode != null) {
        if (!doSetIcon(mySelectedSchema, myNode, myTextField.getText(), getContentPane())) {
          return;
        }
        myActionsTree.repaint();
      }
      CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
      super.doOKAction();
    }
  }


  private abstract class TreeSelectionAction extends DumbAwareAction {
    private TreeSelectionAction(@NotNull Supplier<String> text) {
      super(text);
    }

    private TreeSelectionAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths == null) {
        e.getPresentation().setEnabled(false);
        return;
      }
      for (TreePath path : selectionPaths) {
        if (path.getPath().length <= 2) {
          e.getPresentation().setEnabled(false);
          return;
        }
      }
    }

    protected final boolean isSingleSelection() {
      final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      return selectionPaths != null && selectionPaths.length == 1;
    }
  }

  private final class AddActionActionTreeSelectionAction extends TreeSelectionAction {
    private AddActionActionTreeSelectionAction() {
      super(IdeBundle.messagePointer("button.add.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      int row = myActionsTree.getRowForPath(selectionPath);
      if (selectionPath != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        AddActionDialog dlg = new AddActionDialog(mySelectedSchema);
        if (dlg.showAndGet()) {
          Object actionInfo = dlg.getAddedActionInfo();
          if (actionInfo != null) {
            ActionUrl url = new ActionUrl(getGroupPath(new TreePath(node.getPath())), actionInfo, ADDED,
                                          node.getParent().getIndex(node) + 1);
            addCustomizedAction(url);
            changePathInActionsTree(myActionsTree, url);
            if (actionInfo instanceof String) {
              DefaultMutableTreeNode current = new DefaultMutableTreeNode(url.getComponent());
              current.setParent((DefaultMutableTreeNode)node.getParent());
            }
            ((DefaultTreeModel)myActionsTree.getModel()).reload();
            TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
            myActionsTree.setSelectionRow(row + 1);
          }
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }
  }

  private final class AddSeparatorAction extends TreeSelectionAction {
    private AddSeparatorAction() {
      super(IdeBundle.messagePointer("button.add.separator"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      if (selectionPath != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        final ActionUrl url = new ActionUrl(getGroupPath(selectionPath), Separator.getInstance(), ADDED,
                                            node.getParent().getIndex(node) + 1);
        changePathInActionsTree(myActionsTree, url);
        addCustomizedAction(url);
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
      }
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      myActionsTree.setSelectionRow(myActionsTree.getRowForPath(selectionPath) + 1);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }
  }

  private class MyActionsTreeModel extends DefaultTreeModel implements EditableModel, RowsDnDSupport.RefinedDropSupport {
    private MyActionsTreeModel(TreeNode root) {
      super(root);
    }

    @Override
    public void addRow() {
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return myActionsTree.getPathForRow(oldIndex).getPath().length > 2 && myActionsTree.getPathForRow(newIndex).getPath().length > 2;
    }

    @Override
    public void removeRow(int idx) {
    }

    @Override
    public boolean isDropInto(JComponent component, int oldIndex, int newIndex) {
      TreePath path = myActionsTree.getPathForRow(newIndex);
      return path.getPath().length>1 && !myActionsTree.getModel().isLeaf(path.getLastPathComponent());
    }

    @Override
    public boolean canDrop(int oldIndex, int newIndex, @NotNull Position position) {
      TreePath target = myActionsTree.getPathForRow(newIndex);
      TreePath sourcePath = myActionsTree.getPathForRow(oldIndex);
      if (sourcePath.getParentPath().equals(target.getParentPath())) {
        if (oldIndex == newIndex - 1 && position == ABOVE) return false;
        if (oldIndex == newIndex + 1 && position == BELOW) return false;
      }

      if (sourcePath.getParentPath().equals(target) && position == INTO) return false;

      return sourcePath.getPath().length > 2 &&
             (target.getPath().length > 1 ||
              (target.getPath().length > 1 && target.getLastPathComponent() instanceof Group)) ;
    }

    @Override
    public void drop(int oldIndex, int newIndex, @NotNull Position position) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath path = myActionsTree.getPathForRow(oldIndex);
      TreePath targetPath = myActionsTree.getPathForRow(newIndex);
      if (Objects.equals(path.getParentPath(),
                         targetPath.getParentPath()) && position != INTO) {
        ActionUrl url = CustomizationUtil.getActionUrl(path, MOVE);
        url.setInitialPosition(url.getAbsolutePosition());
        int shift = position == ABOVE && oldIndex < newIndex ? -1: position == BELOW && oldIndex > newIndex ? 1 : 0;
        url.setAbsolutePosition(url.getInitialPosition() + newIndex - oldIndex + shift);
        changePathInActionsTree(myActionsTree, url);
        addCustomizedAction(url);
      } else {
        ActionUrl removeUrl = CustomizationUtil.getActionUrl(path, DELETED);
        changePathInActionsTree(myActionsTree, removeUrl);
        addCustomizedAction(removeUrl);
        ActionUrl addUrl = CustomizationUtil.getActionUrl(targetPath, ADDED);
        if (position == INTO) {
          addUrl.setAbsolutePosition(((DefaultMutableTreeNode)targetPath.getLastPathComponent()).getChildCount());
          ObjectUtils.consumeIfCast(TreeUtil.getUserObject(targetPath.getLastPathComponent()), Group.class, group -> {
            addUrl.getGroupPath().add(group.getName());
          });
        }
        addUrl.setComponent(removeUrl.getComponent());
        changePathInActionsTree(myActionsTree, addUrl);
        addCustomizedAction(addUrl);
      }

      ((DefaultTreeModel)myActionsTree.getModel()).reload();
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      Object[] arr = Arrays.copyOf(targetPath.getParentPath().getPath(), targetPath.getPathCount());
      arr[arr.length - 1] = path.getLastPathComponent();
      TreePath pathToSelect = new TreePath(arr);
      TreeUtil.selectPath(myActionsTree, pathToSelect);
      TreeUtil.scrollToVisible(myActionsTree, path, false);
    }
  }


  private final class RemoveAction extends TreeSelectionAction {
    private RemoveAction() {
      super(IdeBundle.messagePointer("button.remove"), Presentation.NULL_STRING, AllIcons.General.Remove);
      ShortcutSet shortcutSet = KeymapUtil.filterKeyStrokes(CommonShortcuts.getDelete(),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
      if (shortcutSet != null) {
        registerCustomShortcutSet(shortcutSet, myPanel);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      removePaths(myActionsTree.getSelectionPaths());
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
    }
  }

  private void removePaths(TreePath... paths) {
    if (paths == null) return;
    for (TreePath treePath : paths) {
      final ActionUrl url = CustomizationUtil.getActionUrl(treePath, DELETED);
      changePathInActionsTree(myActionsTree, url);
      addCustomizedAction(url);
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private final class EditIconAction extends TreeSelectionAction {
    private EditIconAction() {
      super(IdeBundle.messagePointer("button.edit.action.icon"), Presentation.NULL_STRING, AllIcons.Actions.Edit);
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      if (selectionPath != null) {
        EditIconDialog dlg = new EditIconDialog((DefaultMutableTreeNode)selectionPath.getLastPathComponent());
        if (dlg.showAndGet()) {
          myActionsTree.repaint();
        }
      }
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        final ActionManager actionManager = ActionManager.getInstance();
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myActionsTree.getLeadSelectionPath().getLastPathComponent();
        String actionId = getActionId(node);
        if (actionId != null) {
          final AnAction action = actionManager.getAction(actionId);
          e.getPresentation().setEnabled(action != null);
        }
        else {
          e.getPresentation().setEnabled(false);
        }

      }
    }
  }

  private final class MoveUpAction extends TreeSelectionAction {
    private MoveUpAction() {
      super(IdeBundle.messagePointer("button.move.up"), Presentation.NULL_STRING, AllIcons.Actions.MoveUp);
      registerCustomShortcutSet(CommonShortcuts.MOVE_UP, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
      if (selectionPath != null) {
        for (TreePath treePath : selectionPath) {
          final ActionUrl url = CustomizationUtil.getActionUrl(treePath, MOVE);
          final int absolutePosition = url.getAbsolutePosition();
          url.setInitialPosition(absolutePosition);
          url.setAbsolutePosition(absolutePosition - 1);
          changePathInActionsTree(myActionsTree, url);
          addCustomizedAction(url);
        }
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
        for (TreePath path : selectionPath) {
          myActionsTree.addSelectionPath(path);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, -1));
    }
  }

  private final class MoveDownAction extends TreeSelectionAction {
    private MoveDownAction() {
      super(IdeBundle.messagePointer("button.move.down"), Presentation.NULL_STRING, AllIcons.Actions.MoveDown);
      registerCustomShortcutSet(CommonShortcuts.MOVE_DOWN, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
      if (selectionPath != null) {
        for (int i = selectionPath.length - 1; i >= 0; i--) {
          TreePath treePath = selectionPath[i];
          final ActionUrl url = CustomizationUtil.getActionUrl(treePath, MOVE);
          final int absolutePosition = url.getAbsolutePosition();
          url.setInitialPosition(absolutePosition);
          url.setAbsolutePosition(absolutePosition + 1);
          changePathInActionsTree(myActionsTree, url);
          addCustomizedAction(url);
        }
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
        for (TreePath path : selectionPath) {
          myActionsTree.addSelectionPath(path);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, 1));
    }
  }

  private final class RestoreSelectionAction extends DumbAwareAction {
    private RestoreSelectionAction() {
      super(IdeBundle.messagePointer("button.restore.selected.groups"));
    }

    private Pair<TreeSet<String>, List<ActionUrl>> findActionsUnderSelection() {
      ArrayList<ActionUrl> actions = new ArrayList<>();
      TreeSet<String> selectedNames = new TreeSet<>();
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths != null) {
        for (TreePath path : selectionPaths) {
          ActionUrl selectedUrl = CustomizationUtil.getActionUrl(path, MOVE);
          ArrayList<String> selectedGroupPath = new ArrayList<>(selectedUrl.getGroupPath());
          Object component = selectedUrl.getComponent();
          if (component instanceof Group) {
            selectedGroupPath.add(((Group)component).getName());
            selectedNames.add(((Group)component).getName());
            for (ActionUrl action : mySelectedSchema.getActions()) {
              ArrayList<String> groupPath = action.getGroupPath();
              int idx = Collections.indexOfSubList(groupPath, selectedGroupPath);
              if (idx > -1) {
                actions.add(action);
              }
            }
          }
        }
      }
      return Pair.create(selectedNames, actions);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<ActionUrl> otherActions = new ArrayList<>(mySelectedSchema.getActions());
      otherActions.removeAll(findActionsUnderSelection().second);
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      for (ActionUrl otherAction : otherActions) {
        mySelectedSchema.addAction(otherAction);
      }
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
      restorePathsAfterTreeOptimization(treePaths);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Pair<TreeSet<String>, List<ActionUrl>> selection = findActionsUnderSelection();
      e.getPresentation().setEnabled(!selection.second.isEmpty());
      if (selection.first.size() != 1) {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selected.groups"));
      }
      else {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selection", selection.first.iterator().next()));
      }
    }
  }

  private final class RestoreAllAction extends DumbAwareAction {
    private RestoreAllAction() {
      super(IdeBundle.messagePointer("button.restore.all"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySelectedSchema.isModified(new CustomActionsSchema()));
    }
  }
}
