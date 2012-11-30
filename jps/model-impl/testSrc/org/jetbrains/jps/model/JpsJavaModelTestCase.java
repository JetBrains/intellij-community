package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public abstract class JpsJavaModelTestCase extends JpsModelTestCase  {
  protected JpsModule addModule() {
    return addModule("m");
  }

  protected JpsModule addModule(final String name) {
    return myProject.addModule(name, JpsJavaModuleType.INSTANCE);
  }

  protected JpsLibrary addLibrary() {
    return addLibrary("l");
  }

  protected JpsLibrary addLibrary(final String name) {
    return myProject.addLibrary(name, JpsJavaLibraryType.INSTANCE);
  }

  protected JpsJavaExtensionService getJavaService() {
    return JpsJavaExtensionService.getInstance();
  }
}
