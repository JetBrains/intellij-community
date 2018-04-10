/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.help.impl;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.sun.java.help.impl.JHelpPrintHandler;
import org.jetbrains.annotations.NotNull;

import javax.help.*;
import javax.help.Map.ID;
import javax.help.UnsupportedOperationException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 * This is copy-pasted version of DefaultHelpBroker class.
 *
 * @author Vladimir Kondratyev
 */
class IdeaHelpBroker extends DefaultHelpBroker implements KeyListener{

  private HelpSet myHelpSet=null;
  private JFrame myFrame=null;
  private JHelp jhelp=null;
  private Locale myLocale=null;
  private Font myFont=null;
  /**
   * The container for modally activated help
   * @since 1.1
   */
  private JDialog myDialog=null;
  /**
   * The modal Window that activated help
   * @since 1.1
   */
  private Window myOwnerWindow=null;
  /**
   * The flag for modally activated help. If true, help was activated from
   * a modal dialog. Can not be set to true for V1.1.
   * @since 1.1
   */
  private boolean myModallyActivated=false;

  /**
   * Constructor
   */

  public IdeaHelpBroker(HelpSet hs){
    setHelpSet(hs);
  }

  /**
   * Returns the default HelpSet
   */
  public HelpSet getHelpSet(){
    return myHelpSet;
  }

  /**
   * Changes the HelpSet for this broker.
   * @param hs The HelpSet to set for this broker.
   * A null hs is valid parameter.
   */
  public void setHelpSet(HelpSet hs){
    // If we already have a jhelp check if the HelpSet has changed.
    // If so change the model on the jhelp viewer.
    // This could be made smarter to cache the helpmodels per HelpSet
    if(hs!=null&&myHelpSet!=hs){
      if(jhelp!=null){
        TextHelpModel model=new DefaultHelpModel(hs);
        jhelp.setModel(model);
      }
      myHelpSet=hs;

    }
  }


  /**
   * Gets the locale of this component.
   * @return This component's locale. If this component does not
   * have a locale, the defaultLocale is returned.
   * @see #setLocale
   */
  public Locale getLocale(){
    if(myLocale==null){
      return Locale.getDefault();
    }
    return myLocale;
  }

  /**
   * Sets the locale of this HelpBroker. The locale is propagated to
   * the presentation.
   * @param l The locale to become this component's locale. A null locale
   * is the same as the defaultLocale.
   * @see #getLocale
   */
  public void setLocale(Locale l){
    myLocale=l;
    if(jhelp!=null){
      jhelp.setLocale(myLocale);
    }
  }

  /**
   * Gets the font for this HelpBroker.
   */
  public Font getFont(){
    createHelpWindow();

    if(myFont==null){
      return jhelp.getFont();
    }
    return myFont;
  }

  /**
   * Sets the font for this this HelpBroker.
   * @param f The font.
   */
  public void setFont(Font f){
    myFont=f;
    if(jhelp!=null){
      jhelp.setFont(myFont);
    }
  }

  /**
   * Set the currentView to the navigator with the same
   * name as the <tt>name</tt> parameter.
   *
   * @param name The name of the navigator to set as the
   * current view. If nav is null or not a valid Navigator
   * in this HelpBroker then an
   * IllegalArgumentException is thrown.
   * @throws IllegalArgumentException if nav is null or not a valid Navigator.
   */
  public void setCurrentView(String name){
    createHelpWindow();
    JHelpNavigator nav=null;

    for(Enumeration e=jhelp.getHelpNavigators();
        e.hasMoreElements();){
      nav=(JHelpNavigator)e.nextElement();
      if(nav.getNavigatorName().equals(name)){
        break;
      }
      nav=null;
    }

    if(nav==null){
      throw new IllegalArgumentException("Invalid view name");
    }
    jhelp.setCurrentNavigator(nav);
  }

  /**
   * Determines the current navigator.
   */
  public String getCurrentView(){
    createHelpWindow();
    return jhelp.getCurrentNavigator().getNavigatorName();
  }


  /**
   * Initializes the presentation.
   * This method allows the presentation to be initialized but not displayed.
   * Typically this would be done in a separate thread to reduce the
   * intialization time.
   */
  public void initPresentation(){
    createHelpWindow();
  }

  /**
   * Displays the presentation to the user.
   */
  public void setDisplayed(boolean visible){
    createHelpWindow();
    if(myModallyActivated){
      myDialog.setVisible(visible);
      if(visible){
        myDialog.setLocationRelativeTo(myDialog.getOwner());
      }
    } else{
      //myFrame.setLocationRelativeTo(null);
      myFrame.setVisible(visible);
      myFrame.setState(JFrame.NORMAL);
      IdeFocusManager focusManager = IdeFocusManager.findInstance();
      JComponent target = focusManager.getFocusTargetFor(myFrame.getRootPane());
      focusManager.requestFocus(target != null ? target : myFrame, true);
    }
  }

  /**
   * Determines if the presentation is displayed.
   */
  public boolean isDisplayed(){
    if(myModallyActivated){
      if(myDialog!=null){
        return myDialog.isShowing();
      } else{
        return false;
      }
    } else{
      if(myFrame!=null){
        if(!myFrame.isVisible()){
          return false;
        } else{
          return myFrame.getState() == JFrame.NORMAL;
        }
      } else{
        return false;
      }
    }
  }

  /**
   * Requests the presentation be located at a given position.
   * This operation may throw an UnsupportedOperationException if the
   * underlying implementation does not allow this.
   */
  public void setLocation(Point p) throws UnsupportedOperationException{
    createHelpWindow();
    if(myModallyActivated){
      myDialog.setLocation(p);
    } else{
      myFrame.setLocation(p);
    }
  }

  /**
   * Requests the location of the presentation.
   * @throws UnsupportedOperationException If the underlying implementation
   * does not allow this.
   * @throws IllegalComponentStateException If the presentation is not
   * displayed.
   * @return Point the location of the presentation.
   */
  public Point getLocation() throws UnsupportedOperationException{
    if(jhelp==null){
      throw new java.awt.IllegalComponentStateException("presentation not displayed");
    }
    if(myModallyActivated){
      if(myDialog!=null){
        return myDialog.getLocation();
      }
    } else{
      if(myFrame!=null){
        return myFrame.getLocation();
      }
    }
    return null;

  }

  /**
   * Requests the presentation be set to a given size.
   * This operation may throw an UnsupportedOperationException if the
   * underlying implementation does not allow this.
   */
  public void setSize(Dimension d) throws UnsupportedOperationException{
    HELP_WIDTH=d.width;
    HELP_HEIGHT=d.height;
    createHelpWindow();
    if(myModallyActivated){
      myDialog.setSize(d);
      myDialog.validate();
    } else{
      myFrame.setSize(d);
      myFrame.validate();
    }
  }

  /**
   * Requests the size of the presentation.
   * @throws UnsupportedOperationException If the underlying implementation
   * does not allow this.
   * @throws IllegalComponentStateException If the presentation is not
   * displayed.
   * @return Point the location of the presentation.
   */
  public Dimension getSize() throws UnsupportedOperationException{
    if(jhelp==null){
      throw new java.awt.IllegalComponentStateException("presentation not displayed");
    }
    if(myModallyActivated){
      if(myDialog!=null){
        return myDialog.getSize();
      }
    } else{
      if(myFrame!=null){
        return myFrame.getSize();
      }
    }
    return null;
  }

  /**
   * Hides/Shows view.
   */
  public void setViewDisplayed(boolean displayed){
    createHelpWindow();
    jhelp.setNavigatorDisplayed(displayed);
  }

  /**
   * Determines if the current view is visible.
   */
  public boolean isViewDisplayed(){
    createHelpWindow();
    return jhelp.isNavigatorDisplayed();
  }

  /**
   * Shows this ID as content relative to the (top) HelpSet for the HelpBroker
   * instance--HelpVisitListeners are notified.
   *
   * @param id A string that identifies the topic to show for the loaded (top) HelpSet
   * @exception BadIDException The ID is not valid for the HelpSet
   */
  public void setCurrentID(String id) throws BadIDException{
    try{
      setCurrentID(ID.create(id,myHelpSet));
    } catch(InvalidHelpSetContextException ex){
      // this should not happen
      throw new Error("internal error?");
    }
  }

  /**
   * Displays this ID--HelpVisitListeners are notified.
   *
   * @param id a Map.ID indicating the URL to display
   * @exception InvalidHelpSetContextException if the current helpset does not contain
   * id.helpset
   */
  public void setCurrentID(ID id) throws InvalidHelpSetContextException{
    createJHelp();
    jhelp.getModel().setCurrentID(id);
  }

  /**
   * Determines which ID is displayed (if any).
   */
  public ID getCurrentID(){
    if(jhelp!=null){
      return jhelp.getModel().getCurrentID();
    } else{
      return null;
    }
  }

  /**
   * Displays this URL.
   * HelpVisitListeners are notified.
   * The currentID changes if there is a mathing ID for this URL
   * @param url The url to display. A null URL is a valid url.
   */
  public void setCurrentURL(URL url){
    createHelpWindow();

    jhelp.getModel().setCurrentURL(url);
    if(myModallyActivated){
      myDialog.setVisible(true);
      myDialog.setVisible(true);
    } else{
      myFrame.setVisible(true);
    }
  }

  /**
   * Determines which URL is displayed.
   */
  public URL getCurrentURL(){
    return jhelp.getModel().getCurrentURL();
  }


  // Context-Senstive methods
  /**
   * Enables the Help key on a Component. This method works best when
   * the component is the
   * rootPane of a JFrame in Swing implementations, or a java.awt.Window
   * (or subclass thereof) in AWT implementations.
   * This method sets the default
   * helpID and HelpSet for the Component and registers keyboard actions
   * to trap the "Help" keypress. When the "Help" key is pressed, if the
   * object with the current focus has a helpID, the helpID is displayed.
   * otherwise the default helpID is displayed.
   *
   * @param comp the Component to enable the keyboard actions on.
   * @param id the default HelpID to be displayed
   * @param hs the default HelpSet to be displayed. If hs is null the default HelpSet
   * will be assumed.
   */
  public void enableHelpKey(Component comp,@NotNull String id,HelpSet hs){
    CSH.setHelpIDString(comp,id);
    if(hs!=null){
      CSH.setHelpSet(comp,hs);
    }
    if(comp instanceof JComponent){
      JComponent root=(JComponent)comp;
      root.registerKeyboardAction(getDisplayHelpFromFocus(),
        KeyStroke.getKeyStroke(KeyEvent.VK_HELP,0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      root.registerKeyboardAction(getDisplayHelpFromFocus(),
        KeyStroke.getKeyStroke(KeyEvent.VK_F1,0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    } else{
      comp.addKeyListener(this);
    }
  }

  /**
   * Invoked when a key is typed. This event occurs when a
   * key press is followed by a key release.  Not intended to be overridden or extended.
   */
  public void keyTyped(KeyEvent e){
    //ignore
  }

  /**
   * Invoked when a key is pressed. Not intended to be overridden or extended.
   */
  public void keyPressed(KeyEvent e){
    //ingore
  }


  /**
   * Invoked when a key is released.  Not intended to be overridden or extended.
   */
  public void keyReleased(KeyEvent e){
    // simulate what is done in JComponents registerKeyboardActions.
    int code=e.getKeyCode();
    if(code==KeyEvent.VK_F1||code==KeyEvent.VK_HELP){
      ActionListener al=getDisplayHelpFromFocus();
      al.actionPerformed(new ActionEvent(e.getComponent(),
        ActionEvent.ACTION_PERFORMED,
        null));
    }

  }

  /**
   * Enables help for a Component. This method sets a
   * component's helpID and HelpSet.
   *
   * @param comp the Component to set the id and hs on.
   * @param id the String value of an Map.ID.
   * @param hs the HelpSet the id is in. If hs is null the default HelpSet
   * will be assumed.
   */
  public void enableHelp(Component comp,@NotNull String id,HelpSet hs){
    CSH.setHelpIDString(comp,id);
    if(hs!=null){
      CSH.setHelpSet(comp,hs);
    }
  }

  /**
   * Enables help for a MenuItem. This method sets a
   * component's helpID and HelpSet.
   *
   * @param comp the MenuItem to set the id and hs on.
   * @param id the String value of an Map.ID.
   * @param hs the HelpSet the id is in. If hs is null the default HelpSet
   * will be assumed.
   */
  public void enableHelp(MenuItem comp,@NotNull String id,HelpSet hs){
    CSH.setHelpIDString(comp,id);
    if(hs!=null){
      CSH.setHelpSet(comp,hs);
    }
  }

  /**
   * Enables help for a Component. This method sets a
   * Component's helpID and HelpSet and adds an ActionListener.
   * When an action is performed
   * it displays the Component's helpID and HelpSet in the default viewer.
   *
   * @param comp the Component to set the id and hs on. If the Component is not
   * a javax.swing.AbstractButton or a
   * java.awt.Button an IllegalArgumentException is thrown.
   * @param id the String value of an Map.ID.
   * @param hs the HelpSet the id is in. If hs is null the default HelpSet
   * will be assumed.
   *
   * @see javax.swing.AbstractButton
   * @see java.awt.Button
   * @throws IllegalArgumentException if comp is not a button.
   */
  public void enableHelpOnButton(Component comp,@NotNull String id,HelpSet hs){
    if(!(comp instanceof AbstractButton)&&!(comp instanceof Button)){
      throw new IllegalArgumentException("Invalid Component. comp must be either a javax.swing.AbstractButton or a java.awt.Button");
    }
    CSH.setHelpIDString(comp,id);
    if(hs!=null){
      CSH.setHelpSet(comp,hs);
    }
    if(comp instanceof AbstractButton){
      AbstractButton button=(AbstractButton)comp;
      button.addActionListener(getDisplayHelpFromSource());
    }else{
      Button button=(Button)comp;
      button.addActionListener(getDisplayHelpFromSource());
    }
  }

  /**
   * Enables help for a MenuItem. This method sets a
   * Component's helpID and HelpSet and adds an ActionListener.
   * When an action is performed
   * it displays the Component's helpID and HelpSet in the default viewer.
   *
   * @param comp the MenuItem to set the id and hs on. If comp is null
   * an IllegalAgrumentException is thrown.
   * @param id the String value of an Map.ID.
   * @param hs the HelpSet the id is in. If hs is null the default HelpSet
   * will be assumed.
   * @see java.awt.MenuItem
   * @throws IllegalArgumentException if comp is null.
   */
  public void enableHelpOnButton(@NotNull MenuItem comp,@NotNull String id,HelpSet hs){
    CSH.setHelpIDString(comp,id);
    if(hs!=null){
      CSH.setHelpSet(comp,hs);
    }
    comp.addActionListener(getDisplayHelpFromSource());
  }

  /**
   * Returns the default DisplayHelpFromFocus listener.
   */
  protected ActionListener getDisplayHelpFromFocus(){
    if(displayHelpFromFocus==null){
      displayHelpFromFocus=new CSH.DisplayHelpFromFocus(this);
    }
    return displayHelpFromFocus;
  }

  /**
   * Returns the default DisplayHelpFromSource listener.
   */
  protected ActionListener getDisplayHelpFromSource(){
    if(displayHelpFromSource==null){
      displayHelpFromSource=new CSH.DisplayHelpFromSource(this);
    }
    return displayHelpFromSource;
  }

  /**
   * Set the activation window. If the window is an instance of a
   * Dialog and the is modal, modallyActivated help is set to true and
   * ownerDialog is set to the window. In all other instances
   * modallyActivated is set to false and ownerDialog is set to null.
   * @param window the activating window
   * @since 1.1
   */
  public void setActivationWindow(Window window){
    if (window instanceof Dialog) {
      Dialog tmpDialog = (Dialog)window;
      if (tmpDialog.isModal()) {
        myOwnerWindow = window;
        myModallyActivated = true;
      }
      else {
        myOwnerWindow = null;
        myModallyActivated = false;
      }
    }
    else {
      myOwnerWindow = null;
      myModallyActivated = false;
    }
  }


  /**
   * Private methods.
   */
  private int HELP_WIDTH = (int)(ScreenUtil.getMainScreenBounds().width * 0.8);
  private int HELP_HEIGHT = (int)(ScreenUtil.getMainScreenBounds().height * 0.8);

  private synchronized void createJHelp(){
    if(jhelp==null){
      jhelp=new IdeaJHelp(myHelpSet);
      if(myFont!=null){
        jhelp.setFont(myFont);
      }
      if(myLocale!=null){
        jhelp.setLocale(myLocale);
      }
    }
  }

  private WindowListener dl;
  private boolean modalDeactivated=true;

  private synchronized void createHelpWindow(){
    JDialog tmpDialog=null;
    Dimension size=null;
    Point pos=null;
    boolean resize = false;

    createJHelp();
    // Get the title from the HelpSet
    String helpTitle=myHelpSet.getTitle();

    if(myModallyActivated){
      // replace dialog.getOwner() with the following code
      Window owner=null;
      try{
        Method m = Window.class.getMethod("getOwner", ArrayUtil.EMPTY_CLASS_ARRAY);

        if(m!=null&&myDialog!=null){
          owner = (Window)m.invoke(myDialog, ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
      } catch(NoSuchMethodError | NoSuchMethodException ex){
        // as in JDK1.1
      } catch(InvocationTargetException | IllegalAccessException ex){
        //
      }

      if(myDialog==null||owner!=myOwnerWindow||modalDeactivated){
        if(myFrame!=null){
          pos=myFrame.getLocation();
          size=myFrame.getSize();
          myFrame.dispose();
        }
        if(myDialog!=null){
          pos=myDialog.getLocation();
          size=myDialog.getSize();
          tmpDialog=myDialog;
        }

        myDialog=new JDialog((Dialog)myOwnerWindow,helpTitle);

        // Modal dialogs are really tricky. When the modal dialog
        // is dismissed the JDialog will be dismissed as well.
        // When that happens we need to make sure the ownerWindow
        // is set to null so that a new dialog will be created so
        // that events aren't blocked in the HelpViewer.
        dl=new WindowAdapter(){
          public void windowClosing(WindowEvent e){
            // JDK1.2.1 bug not closing owned windows
            if(myDialog.isShowing()){
              myDialog.hide();
            }
            if (myOwnerWindow != null) {
              myOwnerWindow.removeWindowListener(dl);
            }
            myOwnerWindow=null;
            modalDeactivated=true;
          }
        };
        myOwnerWindow.addWindowListener(dl);
        modalDeactivated=false;

        if(size!=null){
          myDialog.setSize(size);
        } else{
          myDialog.setSize(HELP_WIDTH,HELP_HEIGHT);
        }
        if(pos!=null){
          myDialog.setLocation(pos);
        }
        myDialog.getContentPane().add(jhelp);
        if(tmpDialog!=null){
          tmpDialog.dispose();
        }
      }
    } else{
      if (myFrame == null) {
        myFrame = new JFrame(helpTitle);
        resize = true;
        AppUIUtil.updateWindowIcon(myFrame);
        WindowListener l = new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            myFrame.dispose();
            WeakHashMap handlers = ReflectionUtil.getField(JHelpPrintHandler.class, null, WeakHashMap.class, "handlers");
            if (handlers != null) {
              // even though jHelp is a weak key in the map, corresponding map entry will never be removed, as it's also referenced 
              // from the mapped value
              handlers.remove(jhelp);
            }
          }
        };
        myFrame.addWindowListener(l);
      }
      else {
        pos = myFrame.getLocation();
      }
      if(myDialog!=null){
        pos=myDialog.getLocation();
        size=myDialog.getSize();
        myDialog.dispose();
        myDialog=null;
        myOwnerWindow=null;
      }
      if(size!=null){
        myFrame.setSize(size);
      } else if (resize) {
        myFrame.setSize(HELP_WIDTH,HELP_HEIGHT);
      }
      if(pos!=null){
        myFrame.setLocation(pos);
      }
      myFrame.getContentPane().add(jhelp);
      myFrame.setTitle(myHelpSet.getTitle());
    }

  }

  // the listeners.
  private ActionListener displayHelpFromFocus;
  private ActionListener displayHelpFromSource;
}

