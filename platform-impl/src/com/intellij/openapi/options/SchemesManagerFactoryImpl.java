package com.intellij.openapi.options;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SchemesManagerFactoryImpl extends SchemesManagerFactory implements SettingsSavingComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.SchemesManagerFactoryImpl");

  private final Collection<SchemesManager> myRegisteredManagers = new ArrayList<SchemesManager>();

  public <T extends Scheme, E extends ExternalizableScheme> SchemesManager<T, E> createSchemesManager(final String fileSpec,
                                                                   final SchemeProcessor<E> processor, final RoamingType roamingType) {
    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return null;
    String baseDirPath = ((ApplicationImpl)application).getStateStore().getStateStorageManager().expandMacroses(fileSpec);

    if (baseDirPath != null) {

      SchemesManagerImpl<T, E> manager = new SchemesManagerImpl<T,E>(fileSpec, processor, roamingType,
                                                          ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(roamingType),
                                                          new File(baseDirPath));
      myRegisteredManagers.add(manager);
      return manager;
    }
    else {
      return new AbstractSchemesManager<T,E>(){
        public Collection<E> loadSchemes() {
          return Collections.emptyList();
        }

        public Collection<E> loadScharedSchemes(final Collection<T> currentSchemeList) {
          return Collections.emptyList();
        }

        public void exportScheme(final E scheme, final String name, final String description) throws WriteExternalException {
        }

        public boolean isImportAvailable() {
          return false;
        }

        public boolean isShared(final Scheme scheme) {
          return false;
        }

        public void save() throws WriteExternalException {
        }

        protected void onSchemeDeleted(final Scheme toDelete) {
        }

        protected void onSchemeAdded(final T scheme) {
        }

        public boolean isExportAvailable() {
          return false;
        }
      };
    }
  }

  public void save() {
    for (SchemesManager registeredManager : myRegisteredManagers) {
      try {
        registeredManager.save();
      }
      catch (Throwable e) {
        LOG.info("Cannot save settings for " + registeredManager.getClass().getName(), e);
      }
    }
  }
}
