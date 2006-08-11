package com.intellij.codeInsight.daemon;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HighlightDisplayKey {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.HighlightDisplayKey");

  private static final HashMap<String,HighlightDisplayKey> ourMap = new HashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, String> ourKeyToDisplayNameMap = new HashMap<HighlightDisplayKey, String>();

  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls String name){
    return ourMap.get(name);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name) {
    if (find(name) != null) {
      LOG.info("Key with name \'" + name + "\' already registered");
      return null;
    }
    return new HighlightDisplayKey(name);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name, String displayName, @NonNls String id){
    if (find(name) != null) {
      LOG.info("Key with name \'" + name + "\' already registered");
      return null;
    }
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name, String displayName) {
    return register(name, displayName, name);
  }

  public static String getDisplayNameByKey(HighlightDisplayKey key){
    return ourKeyToDisplayNameMap.get(key);
  }

  private HighlightDisplayKey(String name) {
    this(name, name);
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
