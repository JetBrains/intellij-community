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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.Convertor;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author peter
 */
class LiveTemplateTree extends CheckboxTree {
  LiveTemplateTree(final CheckboxTreeCellRenderer renderer, final CheckedTreeNode root) {
    super(renderer, root);
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
          return StringUtil.notNullize(template.getKey()) +
                 " " +
                 StringUtil.notNullize(template.getDescription()) +
                 " " +
                 template.getTemplateText();
        }
        return "";
      }
    }, true);
  }
}
