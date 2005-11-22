package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class HighlightDisplayKey {
  private static final HashMap<String,HighlightDisplayKey> ourMap = new HashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, String>  ourKeyToDisplayNameMap = new HashMap<HighlightDisplayKey, String>();

  public static final HighlightDisplayKey UNUSED_IMPORT = register("UNUSED_IMPORT", InspectionsBundle.message("unused.import"));  //no suppress
  public static final HighlightDisplayKey UNUSED_SYMBOL = register("UNUSED_SYMBOL", InspectionsBundle.message("unused.symbol"));           
  public static final HighlightDisplayKey UNCHECKED_WARNING = register("UNCHECKED_WARNING", InspectionsBundle.message("unchecked.warning"), "unchecked");//todo


  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls String name){
    return ourMap.get(name);
  }

  public static HighlightDisplayKey register(@NonNls String name) {
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    return new HighlightDisplayKey(name);
  }

  public static HighlightDisplayKey register(@NonNls String name, String displayName, @NonNls String id){
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static HighlightDisplayKey register(@NonNls String name, String displayName){
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static String getDisplayNameByKey(HighlightDisplayKey key){
    return ourKeyToDisplayNameMap.get(key);
  }

  private HighlightDisplayKey(String name) {
    myName = name;
    myID = myName;
    ourMap.put(myName, this);
  }

  public HighlightDisplayKey(@NonNls final String name, @NonNls final String ID) {
    myName = name;
    myID = ID;
    ourMap.put(myName, this);
  }

  public String toString() {
    return myName;
  }

  public String getID(){
    return myID;
  }
}
