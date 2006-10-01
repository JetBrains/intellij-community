package com.intellij.openapi.actionSystem.ex;



import com.intellij.ide.actions.QuickSwitchSchemeAction;

import com.intellij.openapi.actionSystem.ActionManager;

import com.intellij.openapi.actionSystem.ActionSystemBundle;

import com.intellij.openapi.actionSystem.AnAction;

import com.intellij.openapi.actionSystem.DefaultActionGroup;

import com.intellij.openapi.application.ApplicationManager;

import com.intellij.openapi.application.PathManager;

import com.intellij.openapi.components.ExportableApplicationComponent;

import com.intellij.openapi.project.Project;

import com.intellij.openapi.util.InvalidDataException;

import com.intellij.openapi.util.NamedJDOMExternalizable;

import com.intellij.openapi.util.WriteExternalException;

import org.jdom.Element;

import org.jetbrains.annotations.NonNls;

import org.jetbrains.annotations.NotNull;



import java.io.File;

import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;



/**

 * @author max

 */

public class QuickListsManager implements ExportableApplicationComponent, NamedJDOMExternalizable {

  private final List<QuickList> myQuickLists = new ArrayList<QuickList>();

  @NonNls private static final String LIST_TAG = "list";

  private ActionManager myActionManager;



  public static QuickListsManager getInstance() {

    return ApplicationManager.getApplication().getComponent(QuickListsManager.class);

  }



  public QuickListsManager(ActionManagerEx actionManagerEx) {

    myActionManager = actionManagerEx;



    registerActions();

  }



  @NotNull

  public String getComponentName() {

    return "QuickListsManager";

  }



  @NotNull

  public File[] getExportFiles() {

    return new File[]{PathManager.getOptionsFile(this)};

  }



  @NotNull

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

    for (Object group : element.getChildren(LIST_TAG)) {

      Element groupElement = (Element)group;

      QuickList list = new QuickList();

      list.readExternal(groupElement);

      registerQuickList(list, true);

    }

    registerActions();

  }



  public void writeExternal(Element element) throws WriteExternalException {

    for (QuickList list : myQuickLists) {

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

    HashSet<String> registeredIds = new HashSet<String>(); // to prevent exception if 2 or more targets have the same name



    ActionManager actionManager = myActionManager;

    for (QuickList list : myQuickLists) {

      String actionId = list.getActionId();



      if (!registeredIds.contains(actionId)) {

        registeredIds.add(actionId);

        actionManager.registerAction(actionId, new InvokeQuickListAction(list));

      }

    }

  }



  private void unregisterActions() {

    ActionManagerEx actionManager = (ActionManagerEx)myActionManager;



    for (String oldId : actionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {

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

      ActionManager actionManager = ActionManagerEx.getInstance();

      for (String actionId : myQuickList.getActionIds()) {

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