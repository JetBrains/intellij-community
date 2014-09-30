package com.intellij.compiler;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class CompilerTestUtil {
  private CompilerTestUtil() {
  }

  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.projectOpened();
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());

    JpsJavaCompilerOptions javacSettings = JavacConfiguration.getOptions(project, JavacConfiguration.class);
    javacSettings.setTestsUseExternalCompiler(true);
  }

  public static void scanSourceRootsToRecompile(Project project) {
    // need this to emulate project opening
    final List<VirtualFile> roots = ProjectRootManager.getInstance(project).getModuleSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    // todo: forced source roots scan is not needed?
    //TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(new TranslatingCompilerFilesMonitor.ProjectRef(project), roots, roots.size(), true);
  }

  public static void saveApplicationSettings() {
    saveApplicationComponent(ProjectJdkTable.getInstance());
    saveApplicationComponent(FileTypeManager.getInstance());
  }

  public static void saveApplicationComponent(Object appComponent) {
    try {
      final File file;
      String componentName;
      State state = appComponent.getClass().getAnnotation(State.class);
      if (state != null) {
        componentName = state.name();
        Storage lastStorage = state.storages()[state.storages().length - 1];
        StateStorageManager storageManager = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager();
        file = new File(storageManager.expandMacros(lastStorage.file()));
      }
      else if (appComponent instanceof ExportableApplicationComponent && appComponent instanceof NamedJDOMExternalizable) {
        componentName = ((ExportableApplicationComponent)appComponent).getComponentName();
        file = PathManager.getOptionsFile((NamedJDOMExternalizable)appComponent);
      }
      else {
        throw new AssertionError( appComponent.getClass() + " doesn't have @State annotation and doesn't implement ExportableApplicationComponent");
      }

      Element root = new Element("application");
      Element element = JDomSerializationUtil.createComponentElement(componentName);
      if (appComponent instanceof JDOMExternalizable) {
        ((JDOMExternalizable)appComponent).writeExternal(element);
      }
      else {
        element.addContent(((PersistentStateComponent<Element>)appComponent).getState().cloneContent());
      }
      root.addContent(element);
      FileUtil.createParentDirs(file);
      JDOMUtil.writeDocument(new Document(root), file, SystemProperties.getLineSeparator());
    }
    catch (WriteExternalException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void enableExternalCompiler() {
    new WriteAction() {
      protected void run(final Result result) {
        ApplicationManagerEx.getApplicationEx().doNotSave(false);
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        table.addJdk(table.getInternalJdk());
      }
    }.execute();
  }

  public static void disableExternalCompiler(final Project project) {
    new WriteAction() {
      protected void run(final Result result) {
        ApplicationManagerEx.getApplicationEx().doNotSave(true);
        Module[] modules = ModuleManager.getInstance(project).getModules();
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        Sdk internalJdk = table.getInternalJdk();
        List<Module> modulesToRestore = new ArrayList<Module>();
        for (Module module : modules) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.equals(internalJdk)) {
            modulesToRestore.add(module);
          }
        }
        table.removeJdk(internalJdk);
        for (Module module : modulesToRestore) {
          ModuleRootModificationUtil.setModuleSdk(module, internalJdk);
        }
        BuildManager.getInstance().clearState(project);
      }
    }.execute();
  }
}
