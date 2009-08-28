package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;

public abstract class SchemesManagerFactory {

  public abstract <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T,E> createSchemesManager(String fileSpec, SchemeProcessor<E> processor,
                                                                            RoamingType roamingType);

  public static SchemesManagerFactory getInstance(){
    return ApplicationManager.getApplication().getComponent(SchemesManagerFactory.class);
  }


  public abstract void updateConfigFilesFromStreamProviders();
}
