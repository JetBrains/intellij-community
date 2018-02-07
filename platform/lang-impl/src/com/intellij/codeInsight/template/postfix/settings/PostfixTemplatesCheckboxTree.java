// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

public class PostfixTemplatesCheckboxTree extends CheckboxTree {
  @NotNull
  private final CheckedTreeNode myRoot;
  @NotNull
  private final DefaultTreeModel myModel;

  public PostfixTemplatesCheckboxTree() {
    super(getRenderer(), new CheckedTreeNode(null));
    myModel = (DefaultTreeModel)getModel();
    myRoot = (CheckedTreeNode)myModel.getRoot();
    getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent event) {
        selectionChanged();
      }
    });
    setRootVisible(false);
    setShowsRootHandles(true);
  }

  public boolean canUpdateSelectedTemplate() {
    PostfixTemplate template = getTemplate();
    return template != null && template.isEditable();
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

  @NotNull
  public static CheckboxTreeCellRenderer getRenderer() {
    return new CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;
        CheckedTreeNode node = (CheckedTreeNode)value;

        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        boolean isPostfixTemplate = node instanceof PostfixTemplateCheckedTreeNode;
        SimpleTextAttributes attributes = isPostfixTemplate ? SimpleTextAttributes.REGULAR_ATTRIBUTES
                                                            : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        getTextRenderer().append(StringUtil.notNullize(value.toString()),
                                 new SimpleTextAttributes(background, attributes.getFgColor(), JBColor.RED, attributes.getStyle()));
        if (isPostfixTemplate) {
          String example = ((PostfixTemplateCheckedTreeNode)node).getTemplate().getExample();
          if (StringUtil.isNotEmpty(example)) {
            getTextRenderer().append(" (" + example + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    };
  }

  protected void selectionChanged() {

  }

  public void initTree(@NotNull MultiMap<String, PostfixTemplate> langToTemplates) {
    myRoot.removeAllChildren();
    for (Map.Entry<String, Collection<PostfixTemplate>> entry : langToTemplates.entrySet()) {
      String languageId = entry.getKey();
      Language language = Language.findLanguageByID(languageId);
      String languageName = language != null ? language.getDisplayName() : languageId;
      CheckedTreeNode langNode = new LangTreeNode(languageName);
      myRoot.add(langNode);
      for (PostfixTemplate template : entry.getValue()) {
        CheckedTreeNode templateNode = new PostfixTemplateCheckedTreeNode(template, languageName);
        langNode.add(templateNode);
      }
    }

    myModel.nodeStructureChanged(myRoot);
    TreeUtil.expandAll(this);
    setSelectionRow(0);
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
  public MultiMap<String, PostfixTemplate> getEditableTemplates() {
    MultiMap<String, PostfixTemplate> result = MultiMap.createSmart();
    visit(node -> {
      PostfixTemplate template = node.getTemplate();
      PostfixTemplateProvider provider = template.getProvider();
      if (template.isEditable() && provider instanceof PostfixEditableTemplateProvider) {
        result.putValue(((PostfixEditableTemplateProvider)provider).getId(), template);
      }
    });
    return result;
  }

  @NotNull
  public Map<String, Set<String>> getDisabledTemplatesState() {
    final Map<String, Set<String>> result = ContainerUtil.newHashMap();
    visit(template -> {
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
    visit(template -> {
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
    visit(template -> {
      if (languageName.equals(template.getLanguageName()) && postfixTemplate.getKey().equals(template.getTemplate().getKey())) {
        TreeUtil.selectInTree(template, true, this, true);
      }
    });
  }

  private void visit(@NotNull Consumer<PostfixTemplateCheckedTreeNode> consumer) {
    Enumeration languages = myRoot.children();
    while (languages.hasMoreElements()) {
      final CheckedTreeNode langNode = (CheckedTreeNode)languages.nextElement();
      Enumeration templates = langNode.children();
      while (templates.hasMoreElements()) {
        final PostfixTemplateCheckedTreeNode template = (PostfixTemplateCheckedTreeNode)templates.nextElement();
        consumer.consume(template);
      }
    }
  }

  private static class LangTreeNode extends CheckedTreeNode {
    public LangTreeNode(@NotNull String langName) {
      super(langName);
    }
  }

  public void editSelectedTemplate() {
    PostfixTemplate template = getTemplate();
    PostfixTemplateProvider provider = template != null ? template.getProvider() : null;
    if (template != null && template.isEditable() && provider instanceof PostfixEditableTemplateProvider) {
      editTemplate((PostfixEditableTemplateProvider)provider, template);
    }
  }

  private void editTemplate(@NotNull PostfixEditableTemplateProvider provider, @Nullable PostfixTemplate template) {
    Project project = getProject();
    PostfixTemplateEditor editor = provider.createEditor(project);
    if (editor != null && template != null) {
      //noinspection unchecked
      PostfixEditTemplateDialog wrapper = new PostfixEditTemplateDialog(this, editor, provider, template);
      if (wrapper.showAndGet()) {
        PostfixTemplate editedTemplate = editor.createTemplate(wrapper.getTemplateKey());
        // todo: apply to tree
      }
    }
  }

  @Nullable
  private Project getProject() {
    // todo: retrieve proper project
    DataProvider dataProvider = DataManager.getDataProvider(this);
    return dataProvider != null ? CommonDataKeys.PROJECT.getData(dataProvider) : null;
  }
}
