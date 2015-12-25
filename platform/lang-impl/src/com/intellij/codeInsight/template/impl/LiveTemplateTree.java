/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.Convertor;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;

/**
 * @author peter
 */
class LiveTemplateTree extends CheckboxTree implements DataProvider, CopyProvider, PasteProvider {
  private final TemplateListPanel myConfigurable;

  LiveTemplateTree(final CheckboxTreeCellRenderer renderer, final CheckedTreeNode root, TemplateListPanel configurable) {
    super(renderer, root);
    myConfigurable = configurable;
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
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        Object object = ((DefaultMutableTreeNode)o.getLastPathComponent()).getUserObject();
        if (object instanceof TemplateGroup) {
          return ((TemplateGroup)object).getName();
        }
        if (object instanceof TemplateImpl) {
          TemplateImpl template = (TemplateImpl)object;
          return StringUtil.notNullize(template.getGroupName()) + " " +
                 StringUtil.notNullize(template.getKey()) + " " +
                 StringUtil.notNullize(template.getDescription()) + " " +
                 template.getTemplateText();
        }
        return "";
      }
    }, true);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId) || PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    final Set<TemplateImpl> templates = myConfigurable.getSelectedTemplates().keySet();


    CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(templates, new Function<TemplateImpl, String>() {
      @Override
      public String fun(TemplateImpl template) {
        TemplateContext zeroContext = new TemplateContext();
        for (TemplateContextType type : TemplateContextType.EP_NAME.getExtensions()) {
          zeroContext.setEnabled(type, false);
        }
        return JDOMUtil.writeElement(TemplateSettings.serializeTemplate(template, zeroContext));
      }
    }, SystemProperties.getLineSeparator())));
    
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
    if (myConfigurable.getSingleSelectedGroup() == null) return false;
    
    String s = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return s != null && s.startsWith("<template ");
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    TemplateGroup group = myConfigurable.getSingleSelectedGroup();
    assert group != null;

    String buffer = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assert buffer != null;

    try {
      for (Element templateElement : JDOMUtil.load(new StringReader("<root>" + buffer + "</root>")).getChildren(TemplateSettings.TEMPLATE)) {
        TemplateImpl template = TemplateSettings.readTemplateFromElement(group.getName(), templateElement, getClass().getClassLoader());
        while (group.containsTemplate(template.getKey(), template.getId())) {
          template.setKey(template.getKey() + "1");
          if (template.getId() != null) {
            template.setId(template.getId() + "1");
          }
        }
        myConfigurable.addTemplate(template);
      }
    }
    catch (JDOMException ignore) {
    }
    catch (IOException ignore) {
    }
  }
}
