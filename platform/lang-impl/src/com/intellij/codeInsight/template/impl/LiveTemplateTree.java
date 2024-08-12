// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.Set;

class LiveTemplateTree extends CheckboxTree implements UiDataProvider, CopyProvider, PasteProvider, DeleteProvider {
  private final TemplateListPanel myConfigurable;

  LiveTemplateTree(final CheckboxTreeCellRenderer renderer, final CheckedTreeNode root, TemplateListPanel configurable) {
    super(renderer, root);
    myConfigurable = configurable;
    if (!GraphicsEnvironment.isHeadless()) {
      setDragEnabled(true);
    }
  }

  @Override
  protected void onNodeStateChanged(final CheckedTreeNode node) {
    Object obj = node.getUserObject();
    if (obj instanceof TemplateImpl) {
      ((TemplateImpl)obj).setDeactivated(!node.isChecked());
    }
  }

  @Override
  protected void installSpeedSearch() {
    TreeSpeedSearch.installOn(this, true, o -> {
      Object object = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
      if (object instanceof TemplateGroup) {
        return ((TemplateGroup)object).getName();
      }
      if (object instanceof TemplateImpl template) {
        return StringUtil.notNullize(template.getGroupName()) + " " +
               StringUtil.notNullize(template.getKey()) + " " +
               StringUtil.notNullize(template.getDescription()) + " " +
               template.getTemplateText();
      }
      return "";
    }).setComparator(new SubstringSpeedSearchComparator());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.COPY_PROVIDER, this);
    sink.set(PlatformDataKeys.PASTE_PROVIDER, this);
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, this);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    final Set<TemplateImpl> templates = myConfigurable.getSelectedTemplates().keySet();

    TemplateSettings templateSettings = TemplateSettings.getInstance();
    CopyPasteManager.getInstance().setContents(
      new StringSelection(StringUtil.join(templates,
                                          template -> JDOMUtil.writeElement(
                                            TemplateSettings.serializeTemplate(template, templateSettings.getDefaultTemplate(template), TemplateContext.getIdToType())),
                                          System.lineSeparator())));

  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return !myConfigurable.getSelectedTemplates().isEmpty();
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return isCopyEnabled(dataContext);
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    if (myConfigurable.getSingleContextGroup() == null) return false;

    String s = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return s != null && s.trim().startsWith("<template ");
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    TemplateGroup group = myConfigurable.getSingleContextGroup();
    assert group != null;

    String buffer = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assert buffer != null;

    try {
      LiveTemplateContextService ltContextService = LiveTemplateContextService.getInstance();
      for (Element templateElement : JDOMUtil.load("<root>" + buffer + "</root>").getChildren(TemplateSettings.TEMPLATE)) {
        TemplateImpl template = TemplateSettings.readTemplateFromElement(group.getName(), templateElement, getClass().getClassLoader(),
                                                                         ltContextService);
        while (group.containsTemplate(template.getKey(), template.getId())) {
          template.setKey(template.getKey() + "1");
          if (template.getId() != null) {
            template.setId(template.getId() + "1");
          }
        }
        myConfigurable.addTemplate(template);
      }
    }
    catch (Exception ignore) {
    }
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    myConfigurable.removeRows();
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return !myConfigurable.getSelectedTemplates().isEmpty();
  }

  private static final class SubstringSpeedSearchComparator extends SpeedSearchComparator {
    @Override
    public int matchingDegree(String pattern, String text) {
      return matchingFragments(pattern, text) != null ? 1 : 0;
    }

    @Override
    public @Nullable Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
      int index = StringUtil.indexOfIgnoreCase(text, pattern, 0);
      return index >= 0 ? Collections.singleton(TextRange.from(index, pattern.length())) : null;
    }
  }
}
