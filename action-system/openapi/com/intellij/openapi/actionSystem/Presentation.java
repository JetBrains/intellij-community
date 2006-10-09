/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * The presentation of an action in a specific place in the user interface.
 *
 * @see AnAction
 * @see ActionPlaces
 */

public final class Presentation implements Cloneable {
  private THashMap myUserMap;
  /**
   * Defines tool tip for button at tool bar or text for element at menu
   * value: String
   */
  @NonNls public static final String PROP_TEXT = "text";
  /**
   * value: Integer
   */
  @NonNls public static final String PROP_MNEMONIC_KEY = "mnemonicKey";
  /**
   * value: Integer
   */
  @NonNls public static final String PROP_MNEMONIC_INDEX = "mnemonicIndex";
  /**
   * value: String
   */
  @NonNls public static final String PROP_DESCRIPTION = "description";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_ICON = "icon";
  /**
   * value: Icon
   */
  @NonNls public static final String PROP_DISABLED_ICON = "disabledIcon";
  /**
   * value: Boolean
   */
  @NonNls public static final String PROP_VISIBLE = "visible";
  /**
   * The actual value is a Boolean.
   */
  @NonNls public static final String PROP_ENABLED = "enabled";


  private PropertyChangeSupport myChangeSupport;
  private String myText;
  private String myDescription;
  private Icon myIcon;
  private Icon myDisabledIcon;
  private int myMnemonic;
  private int myDisplayedMnemonicIndex = -1;
  private boolean myVisible;
  private boolean myEnabled;

  Presentation(){
    myChangeSupport = new PropertyChangeSupport(this);
    myVisible = true;
    myEnabled = true;
  }

  public void addPropertyChangeListener(PropertyChangeListener l){
    myChangeSupport.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  public String getText(){
    return myText;
  }

  public void setText(String text, boolean mayContainMnemonic){
    int oldMnemonic = myMnemonic;
    String oldText = myText;
    myMnemonic = 0;
    myDisplayedMnemonicIndex = -1;

    if (text != null){

      if (text.indexOf(UIUtil.MNEMONIC) >= 0) {
        text = text.replace(UIUtil.MNEMONIC, '&');
      }

      if (mayContainMnemonic){
        StringBuffer plainText = new StringBuffer();
        for(int i = 0; i < text.length(); i++){
          char ch = text.charAt(i);
          if (myMnemonic == 0 && (ch == '_' || ch == '&')){
            i++;
            if (i >= text.length()){
              break;
            }
            ch = text.charAt(i);
            if (ch != '_' && ch != '&'){
              // Mnemonic is case insensitive.
              myMnemonic = Character.toUpperCase(ch);
              myDisplayedMnemonicIndex = i - 1;
            }
          }
          plainText.append(ch);
        }
        myText = plainText.toString();
      }
      else{
        myText = text;
      }
    }
    else{
      myText = null;
    }

    myChangeSupport.firePropertyChange(PROP_TEXT, oldText, myText);
    if (myMnemonic != oldMnemonic){
      myChangeSupport.firePropertyChange(PROP_MNEMONIC_KEY, new Integer(oldMnemonic), new Integer(myMnemonic));
    }
  }

  public void setText(String text){
    setText(text, true);
  }

  public String getTextWithMnemonic() {
    if ((myText != null) && (myDisplayedMnemonicIndex > -1)) {
      return myText.substring(0, myDisplayedMnemonicIndex) + "_" + myText.substring(myDisplayedMnemonicIndex);
    }
    return myText;
  }

  public void restoreTextWithMnemonic(Presentation presentation) {
    setText(restoreTextWithMnemonic(presentation.getText(), presentation.getMnemonic()));
  }

  public static String restoreTextWithMnemonic(final String text, final int mnemonic) {
    if (text != null) {
     for (int i = 0; i < text.length(); i++) {
       if (Character.toUpperCase(text.charAt(i)) == mnemonic) {
         return text.replaceFirst(String.valueOf(text.charAt(i)), "_" + text.charAt(i));
       }
     }
    }
    return text;
  }

  public String getDescription(){
    return myDescription;
  }

  public void setDescription(String description){
    String oldDescription = myDescription;
    myDescription = description;
    myChangeSupport.firePropertyChange(PROP_DESCRIPTION, oldDescription, myDescription);
  }

  public Icon getIcon(){
    return myIcon;
  }

  public void setIcon(Icon icon){
    Icon oldIcon = myIcon;
    myIcon = icon;
    myChangeSupport.firePropertyChange(PROP_ICON, oldIcon, myIcon);
  }

  public Icon getDisabledIcon(){
    return myDisabledIcon;
  }

  public void setDisabledIcon(Icon icon){
    Icon oldDisabledIcon = myDisabledIcon;
    myDisabledIcon = icon;
    myChangeSupport.firePropertyChange(PROP_DISABLED_ICON, oldDisabledIcon, myDisabledIcon);
  }

  public int getMnemonic(){
    return myMnemonic;
  }

  public int getDisplayedMnemonicIndex(){
    return myDisplayedMnemonicIndex;
  }

  public boolean isVisible(){
    return myVisible;
  }

  public void setVisible(boolean visible){
    boolean oldVisible = myVisible;
    myVisible = visible;
    firePropertyChange(PROP_VISIBLE, oldVisible?Boolean.TRUE:Boolean.FALSE, myVisible?Boolean.TRUE:Boolean.FALSE);
  }

  /**
   * Returns the state of this action.
   *
   * @return <code>true</code> if action is enabled, <code>false</code> otherwise
   */
  public boolean isEnabled(){
    return myEnabled;
  }

  /**
   * Sets whether the action enabled or not. If an action is disabled, {@link AnAction#actionPerformed}
   * won't be called. In case when action represents a button or a menu item, the
   * representing button or item will be greyed out.
   *
   * @param enabled <code>true</code> if you want to enable action, <code>false</code> otherwise
   */
  public void setEnabled(boolean enabled){
    boolean oldEnabled = myEnabled;
    myEnabled = enabled;
    firePropertyChange(PROP_ENABLED, oldEnabled?Boolean.TRUE:Boolean.FALSE, myEnabled?Boolean.TRUE:Boolean.FALSE);
  }

  void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public Object clone(){
    try{
      Presentation presentation = (Presentation)super.clone();
      presentation.myChangeSupport = new PropertyChangeSupport(presentation);
      return presentation;
    }
    catch(CloneNotSupportedException exc){
      throw new RuntimeException(exc.getMessage());
    }
  }

  public void copyFrom(Presentation presentation) {
    setText(presentation.getTextWithMnemonic());
    setDescription(presentation.getDescription());
    setIcon(presentation.getIcon());
    setDisabledIcon(presentation.getDisabledIcon());
    setVisible(presentation.isVisible());
    setEnabled(presentation.isEnabled());
  }

  public Object getClientProperty(@NonNls String key){
    if(myUserMap==null){
      return null;
    }else{
      return myUserMap.get(key);
    }
  }

  public void putClientProperty(@NonNls String key, Object value){
    if(myUserMap==null){
      myUserMap=new THashMap(1);
    }
    Object oldValue = myUserMap.put(key,value);
    myChangeSupport.firePropertyChange(key, oldValue, value);
  }
}
