package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.WriteExternalException;

import java.util.Collection;
import java.io.IOException;


public abstract class SchemesManager {
  public abstract <T extends Scheme> Collection<T> loadSchemes(String fileSpec, SchemeReaderWriter<T> readerWriter, final RoamingType roamingType);

  public abstract <T extends Scheme> void saveSchemes(Collection<T> schemes, String fileSpec, SchemeReaderWriter<T> readerWriter,
                   final RoamingType roamingType) throws WriteExternalException;

  public static SchemesManager getInstance(){
    return ApplicationManager.getApplication().getComponent(SchemesManager.class);
  }

  public abstract <T extends Scheme> Collection<T> loadScharedSchemes(final String dirSpec, final SchemeReaderWriter<T>schemeProcessor);

  public abstract <T extends Scheme> void exportScheme(final T scheme, final String dirSpec, final SchemeReaderWriter<T> schemesProcessor)
      throws WriteExternalException;
}
