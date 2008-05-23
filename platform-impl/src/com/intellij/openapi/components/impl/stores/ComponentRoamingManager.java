package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;

import java.util.HashMap;
import java.util.Map;

public class ComponentRoamingManager {

  private final static ComponentRoamingManager OUR_INSTANCE = new ComponentRoamingManager();

  private final Map<String, RoamingType> myRoamingTypeMap = new HashMap<String, RoamingType>();

  public static ComponentRoamingManager getInstance(){
    return OUR_INSTANCE;
  }

  public RoamingType getRoamingType(String name){
    if (myRoamingTypeMap.containsKey(name)) {
      return myRoamingTypeMap.get(name);
    }

    return RoamingType.PER_USER;
  }

  public void setRoamingType(final String name, final RoamingType roamingType) {
     myRoamingTypeMap.put(name, roamingType);
  }
}
