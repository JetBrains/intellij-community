/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.errorTreeView;

import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.MutableErrorTreeView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class ErrorViewStructure extends AbstractTreeStructure {
  private final ErrorTreeElement myRoot = new MyRootElement();
  private final List<String> myGroupNames = new ArrayList<String>();
  private final Map<String, GroupingElement> myGroupNameToElementMap = new HashMap<String, GroupingElement>();
  private final Map<String, List<NavigatableMessageElement>> myGroupNameToMessagesMap = new HashMap<String, List<NavigatableMessageElement>>();
  private final Map<ErrorTreeElementKind, List<SimpleMessageElement>> mySimpleMessages = new HashMap<ErrorTreeElementKind, List<SimpleMessageElement>>();

  private static final ErrorTreeElementKind[] ourMessagesOrder = new ErrorTreeElementKind[] {
    ErrorTreeElementKind.INFO, ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.GENERIC
  };
  private final Project myProject;
  private final boolean myCanHideWarnings;

  public ErrorViewStructure(Project project, final boolean canHideWarnings) {
    myProject = project;
    myCanHideWarnings = canHideWarnings;
  }

  public Object getRootElement() {
    return myRoot;
  }

  public Object[] getChildElements(Object element) {
    if (element == myRoot) {
      final List<Object> children = new ArrayList<Object>();
      // simple messages
      for (final ErrorTreeElementKind kind : ourMessagesOrder) {
        if (ErrorTreeElementKind.WARNING.equals(kind)) {
          if (myCanHideWarnings && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
            continue;
          }
        }
        final List<SimpleMessageElement> elems = mySimpleMessages.get(kind);
        if (elems != null) {
          children.addAll(elems);
        }
      }
      // files
      for (final String myGroupName : myGroupNames) {
        final GroupingElement groupingElement = myGroupNameToElementMap.get(myGroupName);
        if (shouldShowFileElement(groupingElement)) {
          children.add(groupingElement);
        }
      }
      return ArrayUtil.toObjectArray(children);
    }
    else if (element instanceof GroupingElement) {
      synchronized (myGroupNameToMessagesMap) {
        final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
        if (children != null && children.size() > 0) {
          if (myCanHideWarnings && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
            final List<NavigatableMessageElement> filtered = new ArrayList<NavigatableMessageElement>(children.size());
            for (final NavigatableMessageElement navigatableMessageElement : children) {
              if (ErrorTreeElementKind.WARNING.equals(navigatableMessageElement.getKind())) {
                continue;
              }
              filtered.add(navigatableMessageElement);
            }
            return filtered.toArray();
          }
          return children.toArray();
        }
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private boolean shouldShowFileElement(GroupingElement groupingElement) {
    if (!myCanHideWarnings || !ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return getChildCount(groupingElement) > 0;
    }
    synchronized (myGroupNameToMessagesMap) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      if (children != null) {
        for (final NavigatableMessageElement child : children) {
          if (!ErrorTreeElementKind.WARNING.equals(child.getKind())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Object getParentElement(Object element) {
    if (element instanceof GroupingElement || element instanceof SimpleMessageElement) {
      return myRoot;
    }
    if (element instanceof NavigatableMessageElement) {
      return ((NavigatableMessageElement)element).getParent();
    }
    return null;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new ErrorTreeNodeDescriptor(myProject, parentDescriptor, (ErrorTreeElement)element);
  }

  public final void commit() {
  }

  public final boolean hasSomethingToCommit() {
    return false;
  }

  public void addMessage(ErrorTreeElementKind kind,
                         @NotNull String[] text,
                         @Nullable VirtualFile underFileGroup,
                         @Nullable VirtualFile file,
                         int line,
                         int column,
                         @Nullable Object data) {
    if (underFileGroup != null || file != null) {
      if (file == null) line = column = -1;

      final int guiline = line < 0 ? -1 : line + 1;
      final int guicolumn = column < 0 ? -1 : column + 1;

      VirtualFile group = underFileGroup != null ? underFileGroup : file;
      VirtualFile nav = file != null ? file : underFileGroup;

      addNavigatableMessage(
        group.getPresentableUrl(),
        new OpenFileDescriptor(myProject, nav, line, column),
        kind,
        text,
        data,
        NewErrorTreeViewPanel.createExportPrefix(guiline),
        NewErrorTreeViewPanel.createRendererPrefix(guiline, guicolumn),
        group
      );
    }
    else {
      addSimpleMessage(kind, text, data);
    }
  }

  public List<Object> getGroupChildrenData(final String groupName) {
    synchronized (myGroupNameToMessagesMap) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupName);
      if (children != null && (! children.isEmpty())) {
        final List<Object> result = new ArrayList<Object>();
        for (NavigatableMessageElement child : children) {
          final Object data = child.getData();
          if (data != null) {
            result.add(data);
          }
        }
        return result;
      } else {
        return Collections.emptyList();
      }
    }
  }

  public void addFixedHotfixGroup(final String text, final List<SimpleErrorData> children) {
    final FixedHotfixGroupElement group = new FixedHotfixGroupElement(text, null, null);

    addGroupPlusElements(text, group, children);
  }

  public void addHotfixGroup(final HotfixData hotfixData, final List<SimpleErrorData> children, final MutableErrorTreeView view) {
    final String text = hotfixData.getErrorText();
    final HotfixGroupElement group = new HotfixGroupElement(text, null, null, hotfixData.getFix(), hotfixData.getFixComment(), view);

    addGroupPlusElements(text, group, children);
  }

  private void addGroupPlusElements(String text, GroupingElement group, List<SimpleErrorData> children) {
    synchronized (myGroupNameToMessagesMap) {
      myGroupNames.add(text);
      myGroupNameToElementMap.put(text, group);

      List<NavigatableMessageElement> elements =  new ArrayList<NavigatableMessageElement>();
      myGroupNameToMessagesMap.put(text, elements);

      for (SimpleErrorData child : children) {
        VirtualFile vf = child.getVf();
        elements.add(new MyNavigatableWithDataElement(myProject, child.getKind(), group, child.getMessages(), vf,
                                                   NewErrorTreeViewPanel.createExportPrefix(-1),
                                                   NewErrorTreeViewPanel.createRendererPrefix(-1, -1)));
      }
    }
  }
                                                    
  public void addMessage(ErrorTreeElementKind kind, String[] text, Object data) {
    addSimpleMessage(kind, text, data);
  }                                                         

  public void addNavigatableMessage(String groupName, Navigatable navigatable, final ErrorTreeElementKind kind, final String[] message,
                                    final Object data, String exportText, String rendererTextPrefix, VirtualFile file) {
    synchronized (myGroupNameToMessagesMap) {
      List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
      if (elements == null) {
        elements = new ArrayList<NavigatableMessageElement>();
        myGroupNameToMessagesMap.put(groupName, elements);
      }
      elements.add(new NavigatableMessageElement(
        kind,
        getGroupingElement(groupName, data, file),
        message, navigatable, exportText, rendererTextPrefix)
      );
    }
  }

  private void addSimpleMessage(final ErrorTreeElementKind kind, final String[] text, final Object data) {
    List<SimpleMessageElement> elements = mySimpleMessages.get(kind);
    if (elements == null) {
      elements = new ArrayList<SimpleMessageElement>();
      mySimpleMessages.put(kind, elements);
    }
    elements.add(new SimpleMessageElement(kind, text, data));
  }

  public GroupingElement getGroupingElement(String groupName, Object data, VirtualFile file) {
    GroupingElement element = myGroupNameToElementMap.get(groupName);
    if (element == null) {
      element = new GroupingElement(groupName, data, file);
      myGroupNames.add(groupName);
      myGroupNameToElementMap.put(groupName, element);
    }
    return element;
  }

  public int getChildCount(GroupingElement groupingElement) {
    final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
    return children == null ? 0 : children.size();
  }

  public void clear() {
    myGroupNames.clear();
    myGroupNameToElementMap.clear();
    myGroupNameToMessagesMap.clear();
    mySimpleMessages.clear();
  }

  public ErrorTreeElement getFirstMessage(ErrorTreeElementKind kind) {
    if (myCanHideWarnings && ErrorTreeElementKind.WARNING.equals(kind) && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return null; // no warnings are available
    }
    final List<SimpleMessageElement> simpleMessages = mySimpleMessages.get(kind);
    if (simpleMessages != null && simpleMessages.size() > 0) {
      return simpleMessages.get(0);
    }
    for (final String path : myGroupNames) {
      synchronized (myGroupNameToMessagesMap) {
        final List<NavigatableMessageElement> messages = myGroupNameToMessagesMap.get(path);
        if (messages != null) {
          for (final NavigatableMessageElement navigatableMessageElement : messages) {
            if (kind.equals(navigatableMessageElement.getKind())) {
              return navigatableMessageElement;
            }
          }
        }
      }
    }
    return null;
  }

  private static class MyRootElement extends ErrorTreeElement {
    public String[] getText() {
      return null;
    }

    public Object getData() {
      return null;
    }

    public String getExportTextPrefix() {
      return "";
    }  
  }

  public void removeGroup(final String name) {
    synchronized (myGroupNameToMessagesMap) {
      myGroupNames.remove(name);
      myGroupNameToElementMap.remove(name);
      myGroupNameToMessagesMap.remove(name);
    }
  }

  private static class MyNavigatableWithDataElement extends NavigatableMessageElement {
    private final static Icon ourFileIcon = IconLoader.getIcon("/fileTypes/unknown.png");
    private final VirtualFile myVf;
    private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

    private MyNavigatableWithDataElement(final Project project,
                                        ErrorTreeElementKind kind,
                                         GroupingElement parent,
                                         String[] message,
                                         @NotNull final VirtualFile vf,
                                         String exportText,
                                         String rendererTextPrefix) {
      super(kind, parent, message, new OpenFileDescriptor(project, vf, -1, -1), exportText, rendererTextPrefix);
      myVf = vf;
      myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          if (myVf != null) {
            final Icon icon = myVf.getFileType().getIcon();
            renderer.setIcon(icon);
          }
          final String[] messages = getText();
          final String text = ((messages == null) || (messages.length == 0)) ? vf.getPath() : messages[0];
          renderer.append(text);
        }
      };
    }

    @Override
    public Object getData() {
      return myVf;
    }

    @Override
    public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
      return myCustomizeColoredTreeCellRenderer;
    }
  }
}
