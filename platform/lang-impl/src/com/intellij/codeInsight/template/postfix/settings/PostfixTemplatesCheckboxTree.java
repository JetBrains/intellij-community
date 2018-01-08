// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.Language;
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
  private static final class PostfixTemplateCheckedTreeNode extends CheckedTreeNode {
    @NotNull
    private final String myLanguageName;
    @NotNull
    private final PostfixTemplate myTemplate;
    @NotNull
    private final PostfixTemplatesSettings mySettings;

    @NotNull
    public PostfixTemplate getTemplate() {
      return myTemplate;
    }

    @NotNull
    public String getLanguageName() {
      return myLanguageName;
    }

    PostfixTemplateCheckedTreeNode(@NotNull PostfixTemplate template, @NotNull String languageId, @NotNull String languageName) {
      super(template.getKey().replaceFirst("\\.", ""));
      PostfixTemplatesSettings templatesSettings = PostfixTemplatesSettings.getInstance();
      assert templatesSettings != null;
      mySettings = templatesSettings;

      setChecked(mySettings.isTemplateEnabled(template, languageId));
      myLanguageName = languageName;
      myTemplate = template;
    }
  }

  @NotNull
  private final CheckedTreeNode myRoot;
  @NotNull
  private final DefaultTreeModel myModel;

  public PostfixTemplatesCheckboxTree() {
    super(new CheckboxTreeCellRenderer() {
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
          getTextRenderer().append(" (" + ((PostfixTemplateCheckedTreeNode)node).getTemplate().getExample() + ")",
                                   SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    }, new CheckedTreeNode(null));


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
        CheckedTreeNode templateNode = new PostfixTemplateCheckedTreeNode(template, languageId, languageName);
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
    if (path == null || !(path.getLastPathComponent() instanceof PostfixTemplateCheckedTreeNode)) {
      return null;
    }
    return ((PostfixTemplateCheckedTreeNode)path.getLastPathComponent()).getTemplate();
  }

  public Map<String, Set<String>> getState() {
    final Map<String, Set<String>> result = ContainerUtil.newHashMap();
    Consumer<PostfixTemplateCheckedTreeNode> consumer = template -> {
      if (!template.isChecked()) {
        Set<String> templatesForLanguage = ContainerUtil.getOrCreate(result, template.getLanguageName(), PostfixTemplatesSettings.SET_FACTORY);
        templatesForLanguage.add(template.getTemplate().getKey());
      }
    };
    visit(consumer);

    return result;
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


  public void setState(@NotNull final Map<String, Set<String>> langToDisabledTemplates) {
    final TreeState treeState = TreeState.createOn(this, myRoot);
    Consumer<PostfixTemplateCheckedTreeNode> consumer = template -> {
      Set<String> disabledTemplates = langToDisabledTemplates.get(template.getLanguageName());
      String key = template.getTemplate().getKey();
      if (disabledTemplates != null && disabledTemplates.contains(key)) {
        template.setChecked(false);
        return;
      }

      template.setChecked(true);
    };

    visit(consumer);

    myModel.nodeStructureChanged(myRoot);
    treeState.applyTo(this);
    TreeUtil.expandAll(this);
  }

  public void selectTemplate(@NotNull final PostfixTemplate postfixTemplate, @NotNull final String lang) {
    Consumer<PostfixTemplateCheckedTreeNode> consumer = template -> {
      if (lang.equals(template.getLanguageName()) && postfixTemplate.getKey().equals(template.getTemplate().getKey())) {
        TreeUtil.selectInTree(template, true, this, true);
      }
    };
    visit(consumer);
  }

  private static class LangTreeNode extends CheckedTreeNode {
    public LangTreeNode(@NotNull String langName) {
      super(langName);
    }
  }
}
