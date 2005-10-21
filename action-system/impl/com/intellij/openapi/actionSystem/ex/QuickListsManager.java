package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author max
 */
public class QuickListsManager implements ExportableApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.ex.QuickListsManager");

  private final List<QuickList> myQuickLists = new ArrayList<QuickList>();
  @NonNls private static final String LIST_TAG = "list";
  private ActionManager myActionManager;
  private DataManager myDataManager;

  public static QuickListsManager getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);
  }

  public QuickListsManager(ActionManagerEx actionManagerEx, DataManager dataManager) {
    myActionManager = actionManagerEx;
    myDataManager = dataManager;

    registerActions();
  }

  public String getComponentName() {
    return "QuickListsManager";
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return ActionSystemBundle.message("quick.lists.presentable.name");
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return "quicklists";
  }

  public void readExternal(Element element) throws InvalidDataException {
    List groups = element.getChildren(LIST_TAG);
    for (int i = 0; i < groups.size(); i++) {
      Element groupElement = (Element)groups.get(i);
      QuickList list = new QuickList();
      list.readExternal(groupElement);
      registerQuickList(list, true);
    }
    registerActions();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (int i = 0; i < myQuickLists.size(); i++) {
      QuickList list = myQuickLists.get(i);
      Element groupElement = new Element(LIST_TAG);
      list.writeExternal(groupElement);
      element.addContent(groupElement);
    }
  }

  public QuickList[] getAllQuickLists() {
    return myQuickLists.toArray(new QuickList[myQuickLists.size()]);
  }

  public void removeAllQuickLists() {
    myQuickLists.clear();
  }

  public void registerQuickList(ActionGroup group) {
    Presentation presentation = group.getTemplatePresentation();
    registerQuickList(group, presentation.getText(), presentation.getDescription(), false);
  }

  public void registerQuickList(ActionGroup group, String name, String description, boolean deleteable) {
    AnAction[] actions = group.getChildren(new AnActionEvent(null, myDataManager.getDataContext(), "QUICK_LIST_MANAGER", group.getTemplatePresentation(), myActionManager, 0));
    String[] ids = new String[actions.length];
    for (int i = 0; i < actions.length; i++) {
      AnAction action = actions[i];
      if (action instanceof Separator) {
        ids[i] = QuickList.SEPARATOR_ID;
      }
      else {
        ids[i] = myActionManager.getId(action);
        if (ids[i] == null) {
          LOG.error("Cannot find id for action: " + action);
          ids[i] = QuickList.SEPARATOR_ID;
        }
      }
    }

    registerQuickList(new QuickList(name, description, ids, !deleteable), false);
  }

  public void registerQuickList(QuickList list, boolean replaceExisting) {
    int replaceIdx = -1;
    for (int i = 0; i < myQuickLists.size(); i++) {
      QuickList quickList = myQuickLists.get(i);
      if (list.getActionId().equals(quickList.getActionId())) {
        replaceIdx = i;
        break;
      }
    }

    if (replaceIdx != -1) {
      if (replaceExisting) {
        myQuickLists.set(replaceIdx, list);
      }
    }
    else {
      myQuickLists.add(list);
    }
  }

  public void registerActions() {
    unregisterActions();
    HashSet registeredIds = new HashSet(); // to prevent exception if 2 or more targets have the same name

    ActionManager actionManager = myActionManager;
    for (int i = 0; i < myQuickLists.size(); i++) {
      QuickList list = myQuickLists.get(i);
      String actionId = list.getActionId();

      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        actionManager.registerAction(actionId, new InvokeQuickListAction(list));
      }
    }
  }

  public void unregisterActions() {
    ActionManagerEx actionManager = (ActionManagerEx)myActionManager;

    String[] oldIds = actionManager.getActionIds(QuickList.QUICK_LIST_PREFIX);
    for (int i = 0; i < oldIds.length; i++) {
      String oldId = oldIds[i];
      actionManager.unregisterAction(oldId);
    }
  }

  private static class InvokeQuickListAction extends QuickSwitchSchemeAction {
    private QuickList myQuickList;

    public InvokeQuickListAction(QuickList quickList) {
      myQuickList = quickList;
      getTemplatePresentation().setDescription(myQuickList.getDescription());
      getTemplatePresentation().setText(myQuickList.getDisplayName(), false);
    }

    protected void fillActions(Project project, DefaultActionGroup group) {
      String[] actionIds = myQuickList.getActionIds();
      ActionManager actionManager = ActionManagerEx.getInstance();
      for (int i = 0; i < actionIds.length; i++) {
        String actionId = actionIds[i];
        if (QuickList.SEPARATOR_ID.equals(actionId)) {
          group.addSeparator();
        }
        else {
          AnAction action = actionManager.getAction(actionId);
          if (action != null) {
            group.add(action);
          }
        }
      }
    }

    protected boolean isEnabled() {
      return true;
    }
  }
}