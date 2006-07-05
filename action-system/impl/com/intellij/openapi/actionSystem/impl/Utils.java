package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class Utils{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.actionSystem.impl.Utils");

  private Utils() {}

  public static void handleUpdateException(AnAction action,Presentation presentation,Exception exc){
    String id=ActionManagerEx.getInstance().getId(action);
    if(id!=null){
      LOG.error("update failed for AnAction with ID="+id,exc);
    }else{
      LOG.error("update failed for ActionGroup: "+action+"["+presentation.getText()+"]",exc);
    }
  }

  /**
   * @param actionManager
   * @param list this list contains expanded actions.
   */
  public static void expandActionGroup(ActionGroup group,
                                       ArrayList<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       String place, ActionManager actionManager){
    Presentation presentation = presentationFactory.getPresentation(group);
    AnActionEvent e = new AnActionEvent(
      null,
      context,
      place,
      presentation,
      actionManager,
      0
    );
    try{
      group.update(e);
    }catch(Exception exc){
      handleUpdateException(group,presentation,exc);
      return;
    }

    if(!presentation.isVisible()){ // don't process invisible groups
      return;
    }
    AnAction[] children=group.getChildren(e);
    for(int i=0;i<children.length;i++){
      AnAction child = children[i];
      if (child == null) {
        String groupId = ActionManagerEx.getInstanceEx().getId(group);
        LOG.assertTrue(false, "action is null: i=" + i + " group=" + group + " group id=" + groupId);
        continue;
      }

      presentation=presentationFactory.getPresentation(child);
      AnActionEvent e1 = new AnActionEvent(null,context, place, presentation, actionManager, 0);
      e1.setInjectedContext(child.isInInjectedContext());
      try{
        child.update(e1);
      } catch(Exception exc){
        handleUpdateException(child,presentation,exc);
        continue;
      }
      if(!presentation.isVisible()){ // don't create invisible items in the menu
        continue;
      }
      if(child instanceof ActionGroup){
        ActionGroup actionGroup=(ActionGroup)child;
        if(actionGroup.isPopup()){ // popup menu has its own presentation
          // disable group if it contains no visible actions
          final boolean enabled = hasVisibleChildren(actionGroup, presentationFactory, context, place);
          presentation.setEnabled(enabled);
          list.add(child);
        }else{
          expandActionGroup((ActionGroup)child,list, presentationFactory, context, place, actionManager);
        }
      }else if (child instanceof Separator){
        if (list.size() > 0 && !(list.get(list.size() - 1) instanceof Separator)){
          list.add(child);
        }
      }else{
        list.add(child);
      }
    }
  }

  public static boolean hasVisibleChildren(ActionGroup group, PresentationFactory factory, DataContext context, String place) {
    AnActionEvent event = new AnActionEvent(null, context, place, factory.getPresentation(group), ActionManager.getInstance(), 0);
    event.setInjectedContext(group.isInInjectedContext());
    AnAction[] children = group.getChildren(event);
    for (AnAction anAction : children) {
      if (anAction instanceof Separator) {
        continue;
      }

      LOG.assertTrue(anAction != null);

      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          try {
            AnActionEvent event1 = new AnActionEvent(null, context, place, factory.getPresentation(childGroup), ActionManager.getInstance(), 0);
            event1.setInjectedContext(childGroup.isInInjectedContext());
            childGroup.update(event1);
          }
          catch (Exception exc) {
            handleUpdateException(childGroup, factory.getPresentation(childGroup), exc);
          }
          if (!factory.getPresentation(childGroup).isVisible()) {
            continue;
          }
        }

        if (hasVisibleChildren(childGroup, factory, context, place)) {
          return true;
        }
      }
      else {
        try {
          AnActionEvent event1 = new AnActionEvent(null, context, place, factory.getPresentation(anAction), ActionManager.getInstance(), 0);
          event1.setInjectedContext(anAction.isInInjectedContext());
          anAction.update(event1);
        }
        catch (Exception exc) {
          handleUpdateException(anAction, factory.getPresentation(anAction), exc);
        }
        if (factory.getPresentation(anAction).isVisible()) {
          return true;
        }
      }
    }

    return false;
  }


  public static void fillMenu(ActionGroup group,JComponent component, PresentationFactory presentationFactory, DataContext context, String place){
    ArrayList<AnAction> list = new ArrayList<AnAction>();
    expandActionGroup(group, list, presentationFactory, context, place, ActionManager.getInstance());

    for (int i=0; i<list.size(); i++) {
      AnAction action = list.get(i);
      if (action instanceof Separator) {
        if (i>0 && i<list.size()-1) {
          component.add(new JPopupMenu.Separator());
        }
      } else if (action instanceof ActionGroup) {
        component.add(new ActionMenu(context, place, (ActionGroup)action, presentationFactory));
      } else {
        component.add(new ActionMenuItem(action, presentationFactory.getPresentation(action), place, context));
      }
    }
  }
}
