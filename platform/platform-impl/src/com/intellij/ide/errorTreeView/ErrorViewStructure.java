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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
  
  private final List<String> myGroupNames = new ArrayList<>();
  private final Map<String, GroupingElement> myGroupNameToElementMap = new HashMap<>();
  private final Map<String, List<NavigatableMessageElement>> myGroupNameToMessagesMap = new HashMap<>();
  private final Map<ErrorTreeElementKind, List<ErrorTreeElement>> mySimpleMessages = new EnumMap<>(ErrorTreeElementKind.class);
  private final Object myLock = new Object(); 

  private static final ErrorTreeElementKind[] ourMessagesOrder = {
    ErrorTreeElementKind.INFO,
    ErrorTreeElementKind.ERROR,
    ErrorTreeElementKind.WARNING,
    ErrorTreeElementKind.NOTE,
    ErrorTreeElementKind.GENERIC
  };
  private final Project myProject;
  private final boolean myCanHideWarnings;

  public ErrorViewStructure(Project project, final boolean canHideWarnings) {
    myProject = project;
    myCanHideWarnings = canHideWarnings;
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }

  public boolean hasMessages(@NotNull Set<ErrorTreeElementKind> kinds) {
    synchronized (myLock) {
      for (Map.Entry<ErrorTreeElementKind, List<ErrorTreeElement>> entry : mySimpleMessages.entrySet()) {
        if (kinds.contains(entry.getKey())) {
          final List<ErrorTreeElement> messages = entry.getValue();
          if (messages != null && !messages.isEmpty()) {
            return true;
          }
        }
      }
      for (Map.Entry<String, List<NavigatableMessageElement>> entry : myGroupNameToMessagesMap.entrySet()) {
        final List<NavigatableMessageElement> messages = entry.getValue();
        if (messages != null && !messages.isEmpty()) {
          for (NavigatableMessageElement message : messages) {
            if (kinds.contains(message.getKind())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
  
  @Override
  public ErrorTreeElement[] getChildElements(Object element) {
    if (element == myRoot) {
      final List<ErrorTreeElement> children = new ArrayList<>();
      // simple messages
      synchronized (myLock) {
        for (final ErrorTreeElementKind kind : ourMessagesOrder) {
          if (myCanHideWarnings) {
            if (ErrorTreeElementKind.WARNING.equals(kind) || ErrorTreeElementKind.NOTE.equals(kind)) {
              if (ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
                continue;
              }
            }
          }
          final List<ErrorTreeElement> elems = mySimpleMessages.get(kind);
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
      }
      return ArrayUtil.toObjectArray(children, ErrorTreeElement.class);
    }
    
    if (element instanceof GroupingElement) {
      synchronized (myLock) {
        final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
        if (children != null && !children.isEmpty()) {
          if (myCanHideWarnings && ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
            final List<ErrorTreeElement> filtered = new ArrayList<>(children.size());
            for (final NavigatableMessageElement navigatableMessageElement : children) {
              ErrorTreeElementKind kind = navigatableMessageElement.getKind();
              if (ErrorTreeElementKind.WARNING.equals(kind) || ErrorTreeElementKind.NOTE.equals(kind)) {
                continue;
              }
              filtered.add(navigatableMessageElement);
            }
            return ArrayUtil.toObjectArray(filtered, ErrorTreeElement.class);
          }
          return ArrayUtil.toObjectArray(children, NavigatableMessageElement.class);
        }
      }
    }
    
    return ErrorTreeElement.EMPTY_ARRAY;
  }

  private boolean shouldShowFileElement(GroupingElement groupingElement) {
    if (!myCanHideWarnings || !ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return getChildCount(groupingElement) > 0;
    }
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      if (children != null) {
        for (final NavigatableMessageElement child : children) {
          ErrorTreeElementKind kind = child.getKind();
          if (!ErrorTreeElementKind.WARNING.equals(kind) && !ErrorTreeElementKind.NOTE.equals(kind)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public Object getParentElement(Object element) {
    if (element instanceof GroupingElement || element instanceof SimpleMessageElement) {
      return myRoot;
    }
    if (element instanceof NavigatableMessageElement) {
      GroupingElement result = ((NavigatableMessageElement)element).getParent();
      return result == null ? myRoot : result;
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new ErrorTreeNodeDescriptor(myProject, parentDescriptor, (ErrorTreeElement)element);
  }

  @Override
  public final void commit() {
  }

  @Override
  public final boolean hasSomethingToCommit() {
    return false;
  }

  public void addMessage(@NotNull ErrorTreeElementKind kind,
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
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupName);
      if (children == null || children.isEmpty()) {
        return Collections.emptyList();
      }
      final List<Object> result = new ArrayList<>();
      for (NavigatableMessageElement child : children) {
        final Object data = child.getData();
        if (data != null) {
          result.add(data);
        }
      }
      return result;
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
    final List<NavigatableMessageElement> elements = new ArrayList<>();
    for (SimpleErrorData child : children) {
      elements.add(new MyNavigatableWithDataElement(
        myProject, child.getKind(), group, child.getMessages(), child.getVf(), NewErrorTreeViewPanel.createExportPrefix(-1), NewErrorTreeViewPanel.createRendererPrefix(-1, -1))
      );
    }

    synchronized (myLock) {
      myGroupNames.add(text);
      myGroupNameToElementMap.put(text, group);
      myGroupNameToMessagesMap.put(text, elements);
    }
  }

  public void addMessage(@NotNull ErrorTreeElementKind kind, String[] text, Object data) {
    addSimpleMessage(kind, text, data);
  }

  public void addNavigatableMessage(@Nullable String groupName,
                                    Navigatable navigatable,
                                    @NotNull ErrorTreeElementKind kind,
                                    final String[] message,
                                    final Object data,
                                    String exportText,
                                    String rendererTextPrefix,
                                    VirtualFile file) {
    if (groupName == null) {
      addSimpleMessageElement(new NavigatableMessageElement(kind, null, message, navigatable, exportText, rendererTextPrefix));
    }
    else {
      synchronized (myLock) {
        List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
        if (elements == null) {
          elements = new ArrayList<>();
          myGroupNameToMessagesMap.put(groupName, elements);
        }
        elements.add(new NavigatableMessageElement(kind, getGroupingElement(groupName, data, file), message, navigatable, exportText, rendererTextPrefix));
      }
    }
  }

  public void addNavigatableMessage(@NotNull String groupName,
                                    @NotNull NavigatableMessageElement navigatableMessageElement) {
    synchronized (myLock) {
      List<NavigatableMessageElement> elements = myGroupNameToMessagesMap.get(groupName);
      if (elements == null) {
        elements = new ArrayList<>();
        myGroupNameToMessagesMap.put(groupName, elements);
      }
      if (!myGroupNameToElementMap.containsKey(groupName)) {
        myGroupNames.add(groupName);
        myGroupNameToElementMap.put(groupName, navigatableMessageElement.getParent());
      }
      elements.add(navigatableMessageElement);
    }
  }

  private void addSimpleMessage(@NotNull ErrorTreeElementKind kind, final String[] text, final Object data) {
    addSimpleMessageElement(new SimpleMessageElement(kind, text, data));
  }

  private void addSimpleMessageElement(ErrorTreeElement element) {
    synchronized (myLock) {
      List<ErrorTreeElement> elements = mySimpleMessages.get(element.getKind());
      if (elements == null) {
        elements = new ArrayList<>();
        mySimpleMessages.put(element.getKind(), elements);
      }
      elements.add(element);
    }
  }

  @Nullable
  public GroupingElement lookupGroupingElement(String groupName) {
    synchronized (myLock) {
      return myGroupNameToElementMap.get(groupName);
    }
  }
  
  public GroupingElement getGroupingElement(String groupName, Object data, VirtualFile file) {
    synchronized (myLock) {
      GroupingElement element = myGroupNameToElementMap.get(groupName);
      if (element != null) {
        return element;
      }
      element = new GroupingElement(groupName, data, file);
      myGroupNames.add(groupName);
      myGroupNameToElementMap.put(groupName, element);
      return element;
    }
  }

  public int getChildCount(GroupingElement groupingElement) {
    synchronized (myLock) {
      final List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(groupingElement.getName());
      return children == null ? 0 : children.size();
    }
  }

  public void clear() {
    synchronized (myLock) {
      myGroupNames.clear();
      myGroupNameToElementMap.clear();
      myGroupNameToMessagesMap.clear();
      mySimpleMessages.clear();
    }
  }

  @Nullable
  public ErrorTreeElement getFirstMessage(@NotNull ErrorTreeElementKind kind) {
    if (myCanHideWarnings &&
        (ErrorTreeElementKind.WARNING.equals(kind) || ErrorTreeElementKind.NOTE.equals(kind)) &&
        ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings()) {
      return null; // no warnings are available
    }
    synchronized (myLock) {
      final List<ErrorTreeElement> simpleMessages = mySimpleMessages.get(kind);
      if (simpleMessages != null && !simpleMessages.isEmpty()) {
        return simpleMessages.get(0);
      }
      for (final String path : myGroupNames) {
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
    @Override
    public String[] getText() {
      return null;
    }

    @Override
    public Object getData() {
      return null;
    }

    @Override
    public String getExportTextPrefix() {
      return "";
    }
  }

  public void removeGroup(final String name) {
    synchronized (myLock) {
      myGroupNames.remove(name);
      myGroupNameToElementMap.remove(name);
      myGroupNameToMessagesMap.remove(name);
    }
  }

  public void removeElement(final ErrorTreeElement element) {
    if (element == myRoot) {
      return;
    }
    if (element instanceof GroupingElement) {
      GroupingElement groupingElement = (GroupingElement)element;
      removeGroup(groupingElement.getName());
      final VirtualFile virtualFile = groupingElement.getFile();
      if (virtualFile != null) {
        ApplicationManager.getApplication().runReadAction(() -> {
          final PsiFile psiFile = virtualFile.isValid()? PsiManager.getInstance(myProject).findFile(virtualFile) : null;
          if (psiFile != null) {
            DaemonCodeAnalyzer.getInstance(myProject).restart(psiFile); // urge the daemon to re-highlight the file despite no modification has been made
          }
        });
      }
    }
    else if (element instanceof NavigatableMessageElement){
      final NavigatableMessageElement navElement = (NavigatableMessageElement)element;
      final GroupingElement parent = navElement.getParent();
      if (parent != null) {
        synchronized (myLock) {
          final List<NavigatableMessageElement> groupMessages = myGroupNameToMessagesMap.get(parent.getName());
          if (groupMessages != null) {
            groupMessages.remove(navElement);
          }
        }
      }
    }
    else {
      synchronized (myLock) {
        final List<ErrorTreeElement> simples = mySimpleMessages.get(element.getKind());
        if (simples != null) {
          simples.remove(element);
        }
      }
    }
  }

  private static class MyNavigatableWithDataElement extends NavigatableMessageElement {
    private final VirtualFile myVf;
    private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

    private MyNavigatableWithDataElement(final Project project,
                                         @NotNull ErrorTreeElementKind kind,
                                         GroupingElement parent,
                                         String[] message,
                                         @NotNull final VirtualFile vf,
                                         String exportText,
                                         String rendererTextPrefix) {
      super(kind, parent, message, new OpenFileDescriptor(project, vf, -1, -1), exportText, rendererTextPrefix);
      myVf = vf;
      myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(SimpleColoredComponent renderer,
                                          JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
          final Icon icon = myVf.getFileType().getIcon();
          renderer.setIcon(icon);
          final String[] messages = getText();
          final String text = messages == null || messages.length == 0 ? vf.getPath() : messages[0];
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
