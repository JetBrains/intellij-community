// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

public class PostfixTemplatesCheckboxTree extends CheckboxTree implements Disposable {
  @NotNull
  private final CheckedTreeNode myRoot;
  @NotNull
  private final DefaultTreeModel myModel;
  @NotNull
  private final MultiMap<String, PostfixEditableTemplateProvider> myLanguageToEditableTemplateProviders = MultiMap.createSet();

  public PostfixTemplatesCheckboxTree() {
    super(getRenderer(), new CheckedTreeNode(null));
    myModel = (DefaultTreeModel)getModel();
    myRoot = (CheckedTreeNode)myModel.getRoot();
    TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent event) {
        selectionChanged();
      }
    };
    getSelectionModel().addTreeSelectionListener(selectionListener);
    Disposer.register(this, () -> getSelectionModel().removeTreeSelectionListener(selectionListener));

    DoubleClickListener doubleClickListener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (canEditSelectedTemplate()) {
          editSelectedTemplate();
          return true;
        }
        return false;
      }
    };
    doubleClickListener.installOn(this);
    Disposer.register(this, () -> doubleClickListener.uninstall(this));
    setRootVisible(false);
    setShowsRootHandles(true);
  }

  @Override
  public void dispose() {
    UIUtil.dispose(this);
  }

  @NotNull
  private static CheckboxTreeCellRenderer getRenderer() {
    return new CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;
        CheckedTreeNode node = (CheckedTreeNode)value;

        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        PostfixTemplateCheckedTreeNode templateNode = ObjectUtils.tryCast(node, PostfixTemplateCheckedTreeNode.class);
        SimpleTextAttributes attributes;
        if (templateNode != null) {
          Color fgColor = templateNode.isChanged() || templateNode.isNew() ? JBColor.BLUE : null;
          attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor);
        }
        else {
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        }
        getTextRenderer().append(StringUtil.notNullize(value.toString()),
                                 new SimpleTextAttributes(background, attributes.getFgColor(), JBColor.RED, attributes.getStyle()));

        if (templateNode != null) {
          String example = templateNode.getTemplate().getExample();
          if (StringUtil.isNotEmpty(example)) {
            getTextRenderer().append("  " + example, new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.GRAY), false);
          }
        }
      }
    };
  }

  protected void selectionChanged() {

  }

  public void initTree(@NotNull MultiMap<String, PostfixTemplate> langToTemplates) {
    myRoot.removeAllChildren();
    myLanguageToEditableTemplateProviders.clear();
    for (Map.Entry<String, Collection<PostfixTemplate>> entry : langToTemplates.entrySet()) {
      String languageId = entry.getKey();
      Language language = Language.findLanguageByID(languageId);
      String languageName = language != null ? language.getDisplayName() : languageId;
      CheckedTreeNode langNode = new LangTreeNode(languageName, languageId);
      myRoot.add(langNode);
      for (PostfixTemplate template : entry.getValue()) {
        CheckedTreeNode templateNode = new PostfixTemplateCheckedTreeNode(template, languageName, false);
        langNode.add(templateNode);

        PostfixTemplateProvider postfixTemplateProvider = template.getProvider();
        if (template.isEditable() && postfixTemplateProvider instanceof PostfixEditableTemplateProvider) {
          myLanguageToEditableTemplateProviders.putValue(languageId, (PostfixEditableTemplateProvider)postfixTemplateProvider);
        }
      }
    }

    myModel.nodeStructureChanged(myRoot);
    TreeUtil.expandAll(this);
  }

  @Nullable
  public PostfixTemplate getTemplate() {
    TreePath path = getSelectionModel().getSelectionPath();
    return getTemplateFromPath(path);
  }

  @Nullable
  private static PostfixTemplate getTemplateFromPath(@Nullable TreePath path) {
    if (path == null || !(path.getLastPathComponent() instanceof PostfixTemplateCheckedTreeNode)) {
      return null;
    }
    return ((PostfixTemplateCheckedTreeNode)path.getLastPathComponent()).getTemplate();
  }

  @NotNull
  public MultiMap<PostfixEditableTemplateProvider, PostfixTemplate> getEditableTemplates() {
    MultiMap<PostfixEditableTemplateProvider, PostfixTemplate> result = MultiMap.createSet();
    visitTemplateNodes(node -> {
      PostfixTemplate template = node.getTemplate();
      PostfixTemplateProvider provider = template.getProvider();
      if (template.isEditable() && provider instanceof PostfixEditableTemplateProvider) {
        result.putValue((PostfixEditableTemplateProvider)provider, template);
      }
    });
    return result;
  }

  @NotNull
  public Map<String, Set<String>> getDisabledTemplatesState() {
    final Map<String, Set<String>> result = ContainerUtil.newHashMap();
    visitTemplateNodes(template -> {
      if (!template.isChecked()) {
        Set<String> templatesForLanguage =
          ContainerUtil.getOrCreate(result, template.getLanguageName(), PostfixTemplatesSettings.SET_FACTORY);
        templatesForLanguage.add(template.getTemplate().getKey());
      }
    });

    return result;
  }

  public void setDisabledTemplatesState(@NotNull final Map<String, Set<String>> languageNameToDisabledTemplates) {
    TreeState treeState = TreeState.createOn(this, myRoot);
    visitTemplateNodes(template -> {
      Set<String> disabledTemplates = languageNameToDisabledTemplates.get(template.getLanguageName());
      String key = template.getTemplate().getKey();
      if (disabledTemplates != null && disabledTemplates.contains(key)) {
        template.setChecked(false);
        return;
      }

      template.setChecked(true);
    });

    myModel.nodeStructureChanged(myRoot);
    treeState.applyTo(this);
    TreeUtil.expandAll(this);
  }

  public void selectTemplate(@NotNull final PostfixTemplate postfixTemplate, @NotNull final String languageName) {
    visitTemplateNodes(template -> {
      if (languageName.equals(template.getLanguageName()) && postfixTemplate.getKey().equals(template.getTemplate().getKey())) {
        TreeUtil.selectInTree(template, true, this, true);
      }
    });
  }

  private void visitTemplateNodes(@NotNull Consumer<PostfixTemplateCheckedTreeNode> consumer) {
    Enumeration languages = myRoot.children();
    while (languages.hasMoreElements()) {
      CheckedTreeNode langNode = (CheckedTreeNode)languages.nextElement();
      Enumeration templates = langNode.children();
      while (templates.hasMoreElements()) {
        Object template = templates.nextElement();
        if (template instanceof PostfixTemplateCheckedTreeNode) {
          consumer.consume((PostfixTemplateCheckedTreeNode)template);
        }
      }
    }
  }

  public boolean canAddTemplate() {
    return !myLanguageToEditableTemplateProviders.isEmpty();
  }

  public void addTemplate(@NotNull AnActionButton button) {
    DefaultActionGroup group = new DefaultActionGroup();
    for (Map.Entry<String, Collection<PostfixEditableTemplateProvider>> entry : myLanguageToEditableTemplateProviders.entrySet()) {
      String languageName = entry.getKey();
      for (PostfixEditableTemplateProvider provider : entry.getValue()) {
        group.add(new DumbAwareAction(provider.getName()) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            Project project = getProject();
            PostfixTemplateEditor editor = provider.createEditor(project);
            if (editor != null) {
              //noinspection unchecked
              PostfixEditTemplateDialog dialog = new PostfixEditTemplateDialog(PostfixTemplatesCheckboxTree.this, editor, provider, null);
              if (dialog.showAndGet()) {
                DefaultMutableTreeNode languageNode = TreeUtil.findNode(myRoot, n ->
                  n instanceof LangTreeNode && languageName.equals(n.getUserObject()));
                assert languageNode != null;
                PostfixTemplate createdTemplate = editor.createTemplate(dialog.getTemplateKey());
                PostfixTemplateCheckedTreeNode createdNode = new PostfixTemplateCheckedTreeNode(createdTemplate, languageName, true);
                languageNode.add(createdNode);
                myModel.nodeStructureChanged(myRoot);
                TreeUtil.selectNode(PostfixTemplatesCheckboxTree.this, createdNode);
              }
            }
          }
        });
      }
    }
    DataContext context = DataManager.getInstance().getDataContext(button.getContextComponent());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, context,
                                                                          JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    popup.show(ObjectUtils.assertNotNull(button.getPreferredPopupPoint()));
  }

  public boolean canEditSelectedTemplate() {
    PostfixTemplate template = getTemplate();
    return template != null && template.isEditable();
  }

  public void editSelectedTemplate() {
    TreePath path = getSelectionModel().getSelectionPath();
    if (!(path.getLastPathComponent() instanceof PostfixTemplateCheckedTreeNode)) return;
    PostfixTemplateCheckedTreeNode lastPathComponent = (PostfixTemplateCheckedTreeNode)path.getLastPathComponent();
    PostfixTemplate template = lastPathComponent.getTemplate();
    PostfixTemplateProvider provider = template.getProvider();
    if (template.isEditable() && provider instanceof PostfixEditableTemplateProvider) {
      Project project = getProject();
      PostfixTemplateEditor editor = ((PostfixEditableTemplateProvider)provider).createEditor(project);
      if (editor != null) {
        //noinspection unchecked
        PostfixEditTemplateDialog dialog = new PostfixEditTemplateDialog(this, editor, (PostfixEditableTemplateProvider)provider, template);
        if (dialog.showAndGet()) {
          lastPathComponent.setTemplate(editor.createTemplate(dialog.getTemplateKey()));
        }
      }
    }
  }

  public boolean canRemoveSelectedTemplates() {
    TreePath[] paths = getSelectionModel().getSelectionPaths();
    if (paths == null) {
      return false;
    }
    for (TreePath path : paths) {
      PostfixTemplate template = getTemplateFromPath(path);
      if (template != null && template.isEditable() && !template.isBuiltin()) {
        return true;
      }
    }
    return false;
  }

  public void removeSelectedTemplates() {
    TreePath[] paths = getSelectionModel().getSelectionPaths();
    if (paths == null) {
      return;
    }
    for (TreePath path : paths) {
      PostfixTemplate template = getTemplateFromPath(path);
      if (template != null && template.isEditable() && !template.isBuiltin()) {
        TreeUtil.removeLastPathComponent(this, path);
      }
    }
  }

  @Nullable
  private Project getProject() {
    // todo: retrieve proper project
    DataProvider dataProvider = DataManager.getDataProvider(this);
    return dataProvider != null ? CommonDataKeys.PROJECT.getData(dataProvider) : null;
  }

  private static class LangTreeNode extends CheckedTreeNode {
    @NotNull private final String myLanguageId;

    public LangTreeNode(@NotNull String languageName, @NotNull String languageId) {
      super(languageName);
      myLanguageId = languageId;
    }

    @NotNull
    public String getLanguageId() {
      return myLanguageId;
    }
  }
}
