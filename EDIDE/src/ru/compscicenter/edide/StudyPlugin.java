package ru.compscicenter.edide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.EditorFactory;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: lia
 * Date: 30.01.14
 */

@State(
  name = "StudySettings",
  storages = {
    @Storage(
      id = "main",
      file = "$PROJECT_CONFIG_DIR$/study.xml"
    )}
)

class StudyPlugin implements  PersistentStateComponent<Element> {
  private final Application myApp;
  //TaskManager in this case is pair: project name - Task Manager instance
  private static HashMap<String, TaskManager> myTaskManagers = new HashMap<String, TaskManager>();
  public StudyPlugin(final Application app) {
    myApp = app;
  }

  //@Override
  public void initComponent() {
    EditorFactory.getInstance().addEditorFactoryListener(new StudyEditorFactoryListener(), myApp);
  }

 // @Override
  public void disposeComponent() {

  }

  public static void createTaskManager(String projectName) {
      myTaskManagers.put(projectName, new TaskManager());
  }

  public static TaskManager getTaskManager(String projectName) {
      return myTaskManagers.get(projectName);
  }
  @NotNull
 // @Override
  public String getComponentName() {
    return "Educational plugin";
  }

  @Nullable
  @Override
  public Element getState() {
      Element plugin = new Element("StudyPlugin");
      for(Map.Entry<String, TaskManager> entry : myTaskManagers.entrySet()) {
         plugin.addContent(entry.getValue().saveState(entry.getKey()));
      }
      return plugin;
  }

  @Override
  public void loadState(Element state) {
    List<Element> taskManagers = state.getChildren();
    for (Element el :taskManagers) {
        String name = el.getName();
        TaskManager taskManager =  new TaskManager();
        try {
            taskManager.loadState(el);
        } catch (DataConversionException e) {
            e.printStackTrace();
        }
        myTaskManagers.put(name, taskManager);
    }
  }
}
