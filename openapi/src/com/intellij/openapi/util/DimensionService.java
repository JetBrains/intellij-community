package com.intellij.openapi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.WindowManager;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class represents map between strings and rectangles. It's intended to store
 * sizes of window, dialogs, etc.
 */
public class DimensionService implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.DimensionService");

  private final HashMap myKey2Location;
  private final HashMap myKey2Size;
  private final TObjectIntHashMap myKey2ExtendedState;
  private static final String EXTENDED_STATE = "extendedState";
  private static final String KEY = "key";
  private static final String STATE = "state";

  public static DimensionService getInstance() {
    return ApplicationManager.getApplication().getComponent(DimensionService.class);
  }

  /** Invoked by reflection */
  private DimensionService(){
    myKey2Location=new HashMap();
    myKey2Size=new HashMap();
    myKey2ExtendedState = new TObjectIntHashMap();
  }

  public void initComponent() {}

  public void disposeComponent() {}

  /**
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>. If point
   * is outside of current screen bounds then the method returns <code>null</code>. It
   * properly works in multimonitor configuration.
   * @exception java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized Point getLocation(String key) {
    if(key==null){
      throw new IllegalArgumentException("key cannot be null");
    }
    Point point=(Point)myKey2Location.get(key);
    if(point!=null){
      WindowManager windowManager=WindowManager.getInstance();
      if(!windowManager.isInsideScreenBounds(point.x,point.y)){
        point=null;
      }
    }
    if(point!=null){
      return (Point)point.clone();
    }else{
      return null;
    }
  }

  /**
   * Store specified <code>point</code> under the <code>key</code>. If <code>point</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   * @exception java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized void setLocation(String key, Point point){
    if(key==null){
      throw new IllegalArgumentException("key cannot be null");
    }
    if (point != null) {
      myKey2Location.put(key, point.clone());
    }else {
      myKey2Location.remove(key);
    }
  }

  /**
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>.
   * @exception java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized Dimension getSize(String key) {
    if(key==null){
      throw new IllegalArgumentException("key cannot be null");
    }
    Dimension size=(Dimension)myKey2Size.get(key);
    if(size!=null){
      return (Dimension)size.clone();
    }else{
      return null;
    }
  }

  /**
   * Store specified <code>size</code> under the <code>key</code>. If <code>size</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   * @exception java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized void setSize(String key, Dimension size){
    if(key==null){
      throw new IllegalArgumentException("key cannot be null");
    }
    if (size != null) {
      myKey2Size.put(key, size.clone());
    }else {
      myKey2Size.remove(key);
    }
  }

  public synchronized void readExternal(Element element) throws InvalidDataException {
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element e = (Element)i.next();
      if("location".equals(e.getName())){
        try{
          myKey2Location.put(
            e.getAttributeValue(KEY),
            new Point(
              Integer.parseInt(e.getAttributeValue("x")),
              Integer.parseInt(e.getAttributeValue("y"))
            )
          );
        }catch (NumberFormatException ignored){}
      }else if("size".equals(e.getName())){
        try{
          myKey2Size.put(
            e.getAttributeValue(KEY),
            new Dimension(
              Integer.parseInt(e.getAttributeValue("width")),
              Integer.parseInt(e.getAttributeValue("height"))
            )
          );
        }catch (NumberFormatException ignored){}
      }else if(EXTENDED_STATE.equals(e.getName())) {
        try {
          myKey2ExtendedState.put(e.getAttributeValue(KEY), Integer.parseInt(e.getAttributeValue(STATE)));
        } catch(NumberFormatException ignored){}
      }
    }
  }

  public synchronized void writeExternal(Element element) throws WriteExternalException {
    // Save locations
    for(Iterator i=myKey2Location.keySet().iterator();i.hasNext();){
      String key=(String)i.next();
      Point point=(Point)myKey2Location.get(key);
      LOG.assertTrue(point!=null);
      Element e=new Element("location");
      e.setAttribute(KEY,key);
      e.setAttribute("x", String.valueOf(point.x));
      e.setAttribute("y", String.valueOf(point.y));
      element.addContent(e);
    }
    // Save sizes
    for (Iterator i = myKey2Size.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      Dimension size = (Dimension)myKey2Size.get(key);
      LOG.assertTrue(size!=null);
      Element e = new Element("size");
      e.setAttribute(KEY,key);
      e.setAttribute("width", String.valueOf(size.width));
      e.setAttribute("height", String.valueOf(size.height));
      element.addContent(e);
    }
    // Save extended states
    for (int i = 0; i < myKey2ExtendedState.keys().length; i++) {
      String key = (String)myKey2ExtendedState.keys()[i];
      Element e = new Element(EXTENDED_STATE);
      e.setAttribute(KEY, key);
      e.setAttribute(STATE, String.valueOf(myKey2ExtendedState.get(key)));
      element.addContent(e);
    }
  }

  public String getComponentName() {
    return "DimensionService";
  }

  public void setExtendedState(String key, int extendedState) {
    myKey2ExtendedState.put(key, extendedState);
  }

  public int getExtendedState(String key) {
    if (!myKey2ExtendedState.containsKey(key)) return -1;
    return myKey2ExtendedState.get(key);
  }
}
