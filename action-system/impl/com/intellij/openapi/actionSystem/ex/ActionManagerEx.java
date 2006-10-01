package com.intellij.openapi.actionSystem.ex;



import com.intellij.openapi.actionSystem.ActionManager;

import com.intellij.openapi.actionSystem.AnAction;

import com.intellij.openapi.actionSystem.DataContext;

import com.intellij.openapi.extensions.PluginId;

import org.jetbrains.annotations.Nullable;



import javax.swing.*;

import java.util.Comparator;



public abstract class ActionManagerEx extends ActionManager{

  public static ActionManagerEx getInstanceEx(){

    return (ActionManagerEx)getInstance();

  }

  

  public abstract void addTimerListener(int delay, TimerListener listener);

  public abstract void removeTimerListener(TimerListener listener);





  public abstract void addAnActionListener(AnActionListener listener);

  public abstract void removeAnActionListener(AnActionListener listener);

  public abstract void fireBeforeActionPerformed(AnAction action, DataContext dataContext);

  public abstract void fireBeforeEditorTyping(char c, DataContext dataContext);

  /**

   * For logging purposes

   */

  public abstract String getLastPreformedActionId();

  public abstract String getPrevPreformedActionId();



  /**

   * Comparator compares action ids (String) on order of action registration.

   * @return a negative integer if action that corresponds to the first id was registered earler than the action that corresponds

   *  to the second id; zero if both ids are equal; a positive number otherwise.

   */

  public abstract Comparator<String> getRegistrationOrderComparator();



 

  /**

   * Similar to {@link KeyStroke#getKeyStroke(String)} but allows keys in lower case.

   * I.e. "control x" is accepted and interpreted as "control X".

   * @return null if string cannot be parsed.

   */

  @Nullable

  public static KeyStroke getKeyStroke(String s) {

    KeyStroke result = null;

    try {

      result = KeyStroke.getKeyStroke(s);

    } catch (Exception ex) {

      //ok

    }



    if (result == null && s != null && s.length() >= 2 && s.charAt(s.length() - 2) == ' ') {

      try {

        String s1 = s.substring(0, s.length() - 1) + Character.toUpperCase(s.charAt(s.length() - 1));

        result = KeyStroke.getKeyStroke(s1);

      } catch (Exception ex) {

        // ok

      }

    }



    return result;

  }



  public abstract String [] getPluginActions(PluginId pluginId);

}

