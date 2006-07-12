package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public final class ToolWindowsGroup extends ActionGroup{
  private static final HashMap<String, MyDescriptor> ourId2Text;
  static{
    ourId2Text = new HashMap<String, MyDescriptor>();
    ourId2Text.put(ToolWindowId.COMMANDER, new MyDescriptor(IdeBundle.message("action.toolwindow.commander"), IconLoader.getIcon("/general/toolWindowCommander.png")));
    ourId2Text.put(ToolWindowId.MESSAGES_WINDOW, new MyDescriptor(IdeBundle.message("action.toolwindow.messages"), IconLoader.getIcon("/general/toolWindowMessages.png")));
    ourId2Text.put(ToolWindowId.PROJECT_VIEW, new MyDescriptor(IdeBundle.message("action.toolwindow.project"), IconLoader.getIcon("/general/toolWindowProject.png")));
    ourId2Text.put(ToolWindowId.STRUCTURE_VIEW, new MyDescriptor(IdeBundle.message("action.toolwindow.structure"), IconLoader.getIcon("/general/toolWindowStructure.png")));
    ourId2Text.put(ToolWindowId.ANT_BUILD, new MyDescriptor(IdeBundle.message("action.toolwindow.ant.build"), IconLoader.getIcon("/general/toolWindowAnt.png")));
    ourId2Text.put(ToolWindowId.DEBUG, new MyDescriptor(IdeBundle.message("action.toolwindow.debug"), IconLoader.getIcon("/general/toolWindowDebugger.png")));
    ourId2Text.put(ToolWindowId.RUN, new MyDescriptor(IdeBundle.message("action.toolwindow.run"), IconLoader.getIcon("/general/toolWindowRun.png")));
    ourId2Text.put(ToolWindowId.FIND, new MyDescriptor(IdeBundle.message("action.toolwindow.find"), IconLoader.getIcon("/general/toolWindowFind.png")));
    ourId2Text.put(ToolWindowId.CVS, new MyDescriptor(IdeBundle.message("action.toolwindow.cvs"), IconLoader.getIcon("/general/toolWindowCvs.png")));
    ourId2Text.put(ToolWindowId.HIERARCHY, new MyDescriptor(IdeBundle.message("action.toolwindow.hierarchy"), IconLoader.getIcon("/general/toolWindowHierarchy.png")));
    ourId2Text.put(ToolWindowId.TODO_VIEW, new MyDescriptor(IdeBundle.message("action.toolwindow.todo"), IconLoader.getIcon("/general/toolWindowTodo.png")));
    ourId2Text.put(ToolWindowId.INSPECTION, new MyDescriptor(IdeBundle.message("action.toolwindow.inspection"), IconLoader.getIcon("/general/toolWindowInspection.png")));
    ourId2Text.put(ToolWindowId.ASPECTS_VIEW, new MyDescriptor(IdeBundle.message("action.toolwindow.aspects"), IconLoader.getIcon("/general/toolWindowInspection.png")));
    ourId2Text.put(ToolWindowId.FAVORITES_VIEW, new MyDescriptor(IdeBundle.message("action.toolwindow.favorites"), IconLoader.getIcon("/general/toolWindowFavorites.png")));
  }

  private final ArrayList<AnAction> myChildren;
  private final MyToolWindowManagerListener myToolWindowManagerListener;

  public ToolWindowsGroup(ProjectManager projectManager){
    myChildren = new ArrayList<AnAction>();
    myToolWindowManagerListener=new MyToolWindowManagerListener();
    projectManager.addProjectManagerListener(new MyProjectManagerListener());
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e){
    return myChildren.toArray(new AnAction[myChildren.size()]);
  }

  /**
   * Registers action that activates tool window with specified <code>id</code>.
   */
  private void addActionForToolWindow(final String id){
    // Check that tool window with the same ID isn't already registered
    for(Iterator i=myChildren.iterator();i.hasNext();){
      ActivateToolWindowAction action=(ActivateToolWindowAction)i.next();
      if(action.getToolWindowId().equals(id)){
        return;
      }
    }
    // Register an action for activating this tool window
    final MyDescriptor descriptor = ourId2Text.get(id);

    final String text = descriptor != null ? descriptor.myText : id;
    final Icon icon = descriptor != null ? descriptor.myIcon : null;

    ActivateToolWindowAction action = new ActivateToolWindowAction(id, text, icon);
    ActionManager.getInstance().registerAction(ActivateToolWindowAction.getActionIdForToolWindow(id),action);
    myChildren.add(action);
    Collections.sort(myChildren,MyActionComparator.ourInstance);
  }

  private final class MyProjectManagerListener extends ProjectManagerAdapter{
    public void projectClosed(Project project){
      final ToolWindowManagerEx windowManagerEx = ToolWindowManagerEx.getInstanceEx(project);
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
      windowManagerEx.removeToolWindowManagerListener(myToolWindowManagerListener);
    }

    public void projectOpened(Project project){
      final ToolWindowManagerEx toolWindowManager=ToolWindowManagerEx.getInstanceEx(project);
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) return; //headless environment
      final String[] ids=toolWindowManager.getToolWindowIds();
      for(int i=0;i<ids.length;i++){
        addActionForToolWindow(ids[i]);
      }
      toolWindowManager.addToolWindowManagerListener(myToolWindowManagerListener);
    }
  }

  private static final class MyActionComparator implements Comparator{
    public static final MyActionComparator ourInstance=new MyActionComparator();

    private MyActionComparator(){}

    public int compare(Object obj1,Object obj2){
      ActivateToolWindowAction action1=(ActivateToolWindowAction)obj1;
      int mnemonic1=ActivateToolWindowAction.getMnemonicForToolWindow(action1.getToolWindowId());

      ActivateToolWindowAction action2=(ActivateToolWindowAction)obj2;
      int mnemonic2=ActivateToolWindowAction.getMnemonicForToolWindow(action2.getToolWindowId());

      if(mnemonic1!=-1&&mnemonic2==-1){
        return -1;
      }else if(mnemonic1==-1&&mnemonic2!=-1){
        return 1;
      }else if(mnemonic1!=-1&&mnemonic2!=-1){
        return mnemonic1-mnemonic2;
      }else{ // Both actions have no mnemonic, therefore they are sorted alphabetically
        return action1.getToolWindowId().compareToIgnoreCase(action2.getToolWindowId());
      }
    }
  }

  private final class MyToolWindowManagerListener extends ToolWindowManagerAdapter{
    public void toolWindowRegistered(String id){
      addActionForToolWindow(id);
    }
  }

  private static final class MyDescriptor{
    public final String myText;
    public final Icon myIcon;

    public MyDescriptor(String text, Icon icon) {
      myText = text;
      myIcon = icon;
    }
  }
}
