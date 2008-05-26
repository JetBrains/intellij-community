package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.WriteExternalException;

import java.util.Collection;


public abstract class SchemesManager {
  public abstract <T extends Scheme> Collection<T> loadSchemes(String fileSpec, SchemeProcessor<T> processor, final RoamingType roamingType);

  public abstract <T extends Scheme> void saveSchemes(Collection<T> schemes, String fileSpec, SchemeProcessor<T> processor,
                   final RoamingType roamingType) throws WriteExternalException;

  public static SchemesManager getInstance(){
    return ApplicationManager.getApplication().getComponent(SchemesManager.class);
  }

  public abstract <T extends Scheme> Collection<T> loadScharedSchemes(final String dirSpec, final SchemeProcessor<T> schemeProcessor);

  public abstract <T extends Scheme> void exportScheme(final T scheme, final String dirSpec, final SchemeProcessor<T> schemesProcessor)
      throws WriteExternalException;
}
