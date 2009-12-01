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

package com.intellij.ide.todo;

import com.intellij.AppTopics;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Vladimir Kondratyev
 */
@State(
  name="TodoView",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class TodoView implements PersistentStateComponent<Element>, Disposable {
  private final Project myProject;
  private final ProjectLevelVcsManager myVCSManager;

  private ContentManager myContentManager;
  private CurrentFileTodosPanel myCurrentFileTodos;
  private TodoPanel myAllTodos;
  private ChangeListTodosPanel myChangeListTodos;

  private int mySelectedIndex;
  private final TodoPanelSettings myCurrentPanelSettings;
  private final TodoPanelSettings myAllPanelSettings;
  private final TodoPanelSettings myChangeListTodosPanelSettings;

  @NonNls private static final String ATTRIBUTE_SELECTED_INDEX = "selected-index";
  @NonNls private static final String ELEMENT_TODO_PANEL = "todo-panel";
  @NonNls private static final String ATTRIBUTE_ID = "id";
  @NonNls private static final String VALUE_SELECTED_FILE = "selected-file";
  @NonNls private static final String VALUE_ALL = "all";
  @NonNls private static final String VALUE_DEFAULT_CHANGELIST = "default-changelist";
  private Content myChangeListTodosContent;

  private final MyVcsListener myVcsListener = new MyVcsListener();

  /* Invoked by reflection */
  TodoView(Project project, ProjectLevelVcsManager manager){
    myProject=project;
    myVCSManager = manager;
    myCurrentPanelSettings=new TodoPanelSettings();
    myAllPanelSettings=new TodoPanelSettings();
    myChangeListTodosPanelSettings = new TodoPanelSettings();

    myVCSManager.addVcsListener(myVcsListener);

    final MyPropertyChangeListener myPropertyChangeListener = new MyPropertyChangeListener();
    TodoConfiguration.getInstance().addPropertyChangeListener(myPropertyChangeListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        TodoConfiguration.getInstance().removePropertyChangeListener(myPropertyChangeListener);
      }
    });

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(AppTopics.FILE_TYPES, new MyFileTypeListener());
  }

  public void loadState(Element element) {
    mySelectedIndex=0;
    try{
      mySelectedIndex=Integer.parseInt(element.getAttributeValue(ATTRIBUTE_SELECTED_INDEX));
    }catch(NumberFormatException ignored){
      //nothing to be done
    }

    //noinspection unchecked
    for (Element child : (Iterable<Element>)element.getChildren()) {
      if (ELEMENT_TODO_PANEL.equals(child.getName())) {
        String id = child.getAttributeValue(ATTRIBUTE_ID);
        if (VALUE_SELECTED_FILE.equals(id)) {
          myCurrentPanelSettings.readExternal(child);
        }
        else if (VALUE_ALL.equals(id)) {
          myAllPanelSettings.readExternal(child);
        }
        else if (VALUE_DEFAULT_CHANGELIST.equals(id)) {
          myChangeListTodosPanelSettings.readExternal(child);
        }
        else {
          throw new IllegalArgumentException("unknown id: " + id);
        }
      }
    }
  }

  public Element getState() {
    Element element = new Element("TodoView");
    if(myContentManager!=null){ // all panel were constructed
      Content content=myContentManager.getSelectedContent();
      element.setAttribute(ATTRIBUTE_SELECTED_INDEX,Integer.toString(myContentManager.getIndexOfContent(content)));
    }

    Element selectedFileElement=new Element(ELEMENT_TODO_PANEL);
    selectedFileElement.setAttribute(ATTRIBUTE_ID,VALUE_SELECTED_FILE);
    myCurrentPanelSettings.writeExternal(selectedFileElement);
    element.addContent(selectedFileElement);

    Element allElement=new Element(ELEMENT_TODO_PANEL);
    allElement.setAttribute(ATTRIBUTE_ID, VALUE_ALL);
    myAllPanelSettings.writeExternal(allElement);
    element.addContent(allElement);

    Element changeListElement=new Element(ELEMENT_TODO_PANEL);
    changeListElement.setAttribute(ATTRIBUTE_ID, VALUE_DEFAULT_CHANGELIST);
    myChangeListTodosPanelSettings.writeExternal(changeListElement);
    element.addContent(changeListElement);
    return element;
  }

  public void dispose() {
    myVCSManager.removeVcsListener(myVcsListener);
  }

  public void initToolWindow(ToolWindow toolWindow) {
    // Create panels

    Content allTodosContent= ContentFactory.SERVICE.getInstance().createContent(null, IdeBundle.message("title.project"),false);
    myAllTodos=new TodoPanel(myProject,myAllPanelSettings,false,allTodosContent){
      protected TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project){
        AllTodosTreeBuilder builder=new AllTodosTreeBuilder(tree,treeModel,project);
        builder.init();
        return builder;
      }
    };
    allTodosContent.setComponent(myAllTodos);
    Disposer.register(this, myAllTodos);

    Content currentFileTodosContent=
      ContentFactory.SERVICE.getInstance().createContent(null,IdeBundle.message("title.todo.current.file"),false);
    myCurrentFileTodos=new CurrentFileTodosPanel(myProject,myCurrentPanelSettings,currentFileTodosContent){
      protected TodoTreeBuilder createTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
        CurrentFileTodosTreeBuilder builder=new CurrentFileTodosTreeBuilder(tree,treeModel,project);
        builder.init();
        return builder;
      }
    };
    Disposer.register(this, myCurrentFileTodos);
    currentFileTodosContent.setComponent(myCurrentFileTodos);

    myChangeListTodosContent = ContentFactory.SERVICE.getInstance()
      .createContent(null, IdeBundle.message("changelist.todo.title",
                                             ChangeListManager.getInstance(myProject).getDefaultChangeList().getName()),
                           false);
    myChangeListTodos = new ChangeListTodosPanel(myProject, myCurrentPanelSettings, myChangeListTodosContent) {
      protected TodoTreeBuilder createTreeBuilder(JTree tree, DefaultTreeModel treeModel, Project project) {
        ChangeListTodosTreeBuilder builder = new ChangeListTodosTreeBuilder(tree, treeModel, project);
        builder.init();
        return builder;
      }
    };
    Disposer.register(this, myChangeListTodos);
    myChangeListTodosContent.setComponent(myChangeListTodos);

    myContentManager=toolWindow.getContentManager();

    myContentManager.addContent(allTodosContent);
    myContentManager.addContent(currentFileTodosContent);
    if (myVCSManager.getAllActiveVcss().length > 0) {
      myVcsListener.myIsVisible = true;
      myContentManager.addContent(myChangeListTodosContent);
    }

    Content content=myContentManager.getContent(mySelectedIndex);
    content = content == null ? allTodosContent : content;
    myContentManager.setSelectedContent(content);
  }

  private final class MyVcsListener implements VcsListener {
    private boolean myIsVisible;

    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          if (myContentManager == null) return; //was not initialized yet

          if (myProject.isDisposed()) return; //already disposed

          final AbstractVcs[] vcss = myVCSManager.getAllActiveVcss();
          if (myIsVisible && vcss.length == 0) {
            myContentManager.removeContent(myChangeListTodosContent, false);
            myIsVisible = false;
          }
          else if (!myIsVisible && vcss.length > 0) {
            myContentManager.addContent(myChangeListTodosContent);
            myIsVisible = true;
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private final class MyPropertyChangeListener implements PropertyChangeListener{
    public void propertyChange(PropertyChangeEvent e){
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(e.getPropertyName()) || TodoConfiguration.PROP_TODO_FILTERS.equals(e.getPropertyName())) {
        _updateFilters();
      }
    }

    private void _updateFilters() {
      try {
        updateFilters();
      }
      catch (ProcessCanceledException e) {
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
          public void run() {
            _updateFilters();
          }
        }, ModalityState.NON_MODAL);
      }
    }

    private void updateFilters(){
      myCurrentFileTodos.updateTodoFilter();
      myAllTodos.updateTodoFilter();
      myChangeListTodos.updateTodoFilter();
    }
  }

  private final class MyFileTypeListener implements FileTypeListener{
    public void beforeFileTypesChanged(FileTypeEvent event) {
    }

    public void fileTypesChanged(FileTypeEvent e){
      // this invokeLater guaranties that this code will be invoked after
      // PSI gets the same event.
      DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;

          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable(){
            public void run(){
              if (myAllTodos == null) return;

              ApplicationManager.getApplication().runReadAction(
                new Runnable(){
                  public void run(){
                    myAllTodos.rebuildCache();
                    myCurrentFileTodos.rebuildCache();
                    myChangeListTodos.rebuildCache();
                  }
                }
              );
              ApplicationManager.getApplication().invokeLater(new Runnable(){
                public void run(){
                  myAllTodos.updateTree();
                  myCurrentFileTodos.updateTree();
                  myChangeListTodos.updateTree();
                }
              }, ModalityState.NON_MODAL);
            }
          }, IdeBundle.message("progress.looking.for.todos"), false, myProject);
        }
      });
    }
  }
}
