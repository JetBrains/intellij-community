package com.intellij.testFramework;

import com.intellij.j2ee.j2eeDom.J2EEModuleProperties;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.j2ee.make.ModuleBuildPropertiesEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public abstract class ModuleTestCase extends IdeaTestCase {
  protected final Collection<Module> myModulesToDispose = new ArrayList<Module>();

  protected void setUp() throws Exception {
    super.setUp();
    myModulesToDispose.clear();
  }

  protected void tearDown() throws Exception {
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Iterator<Module> iterator = myModulesToDispose.iterator(); iterator.hasNext();) {
          Module module = iterator.next();
          String moduleName = module.getName();
          if (moduleManager.findModuleByName(moduleName) != null) {
            moduleManager.disposeModule(module);
          }
        }
      }
    });
    myModulesToDispose.clear();
    super.tearDown();
  }

  protected Module createModule(final File moduleFile) {
    return createModule(moduleFile, ModuleType.JAVA);
  }

  protected Module createModule(final File moduleFile, final ModuleType moduleType) {
    final String path = moduleFile.getAbsolutePath();
    return createModule(path, moduleType);
  }

  protected Module createModule(final String path) {
    return createModule(path, ModuleType.JAVA);
  }

  protected Module createModule(final String path, final ModuleType moduleType) {
    Module module = ApplicationManager.getApplication().runWriteAction(
      new Computable<Module>() {
        public Module compute() {
          return ModuleManager.getInstance(myProject).newModule(path, moduleType);
        }
      }
    );

    myModulesToDispose.add(module);
    return module;
  }

  protected Module loadModule(final File moduleFile) {
    Module module = ApplicationManager.getApplication().runWriteAction(
      new Computable<Module>() {
        public Module compute() {
          try {
            return ModuleManager.getInstance(myProject).loadModule(moduleFile.getAbsolutePath());
          }
          catch (Exception e) {
            LOG.error(e);
            return null;
          }
        }
      }
    );

    myModulesToDispose.add(module);
    return module;
  }

  protected ModuleImpl loadAllModulesUnder(VirtualFile rootDir) throws Exception {
    ModuleImpl module = null;
    final VirtualFile[] children = rootDir.getChildren();
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      if (child.isDirectory()) {
        final ModuleImpl childModule = loadAllModulesUnder(child);
        if (module == null) module = childModule;
      }
      else if (child.getName().endsWith(".iml")) {
        String modulePath = child.getPath();
        module = (ModuleImpl)loadModule(new File(modulePath));
        readJdomExternalizables(module);
      }
    }
    return module;
  }

  private static void readJdomExternalizables(ModuleImpl module) {
    final ModuleRootManagerImpl moduleRootManager = (ModuleRootManagerImpl)ModuleRootManager.getInstance(module);
    module.doInitJdomExternalizable(ModuleRootManager.class, moduleRootManager);

    ModuleBuildPropertiesEx moduleBuildProperties = (ModuleBuildPropertiesEx)ModuleBuildProperties.getInstance(module);
    if (moduleBuildProperties != null) {
      module.doInitJdomExternalizable(ModuleBuildPropertiesEx.class, moduleBuildProperties);
    }

    J2EEModuleProperties moduleProperties = J2EEModuleProperties.getInstance(module);
    if (moduleProperties != null){
      module.doInitJdomExternalizable(J2EEModuleProperties.class, moduleProperties);
    }
  }
}
