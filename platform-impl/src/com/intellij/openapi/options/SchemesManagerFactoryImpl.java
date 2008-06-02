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

  public <T extends Scheme> SchemesManager<T> createSchemesManager(final String fileSpec,
                                                                   final SchemeProcessor<T> processor, final RoamingType roamingType) {
    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return null;
    String baseDirPath = ((ApplicationImpl)application).getStateStore().getStateStorageManager().expandMacroses(fileSpec);

    if (baseDirPath != null) {

      SchemesManagerImpl<T> manager = new SchemesManagerImpl<T>(fileSpec, processor, roamingType,
                                                          ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(roamingType),
                                                          new File(baseDirPath));
      myRegisteredManagers.add(manager);
      return manager;
    }
    else {
      return new AbstractSchemesManager<T>(){
        public Collection<T> loadSchemes() {
          return getAllSchemes();
        }

        public Collection<T> loadScharedSchemes(final Collection<String> currentSchemeNameList) {
          return Collections.emptyList();
        }

        public void exportScheme(final T scheme) throws WriteExternalException {
        }

        public boolean isImportExportAvailable() {
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

        protected void renameScheme(final T scheme, final String newName) {
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
