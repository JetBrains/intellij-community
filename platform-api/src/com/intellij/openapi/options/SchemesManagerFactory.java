package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;

public abstract class SchemesManagerFactory {

  public abstract <T extends Scheme> SchemesManager<T> createSchemesManager(String fileSpec, SchemeProcessor<T> processor,
                                                                            RoamingType roamingType);

  public static SchemesManagerFactory getInstance(){
    return ApplicationManager.getApplication().getComponent(SchemesManagerFactory.class);
  }

  
}
