package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.junit.ModuleBasedConfiguration;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.junit2.configuration.RunConfigurationModule;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.*;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.snapShooter.SnapShooter;
import com.intellij.util.PathUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public class ApplicationConfiguration extends SingleClassConfiguration implements RunJavaConfiguration {

  public String MAIN_CLASS_NAME;
  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;
  public String WORKING_DIRECTORY;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;
  public boolean ENABLE_SWING_INSPECTOR;

  private int myLastSnapShooterPort;
  private Runnable mySnapShooterNotifyRunnable;

  public ApplicationConfiguration(final String name, final Project project, ApplicationConfigurationType applicationConfigurationType) {
    super(name, new RunConfigurationModule(project, true), applicationConfigurationType.getConfigurationFactories()[0]);
  }

  public RunProfileState getState(final DataContext context,
                                  final RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) {
    final JavaCommandLineState state = new MyJavaCommandLineState(runnerSettings, configurationSettings);
    state.setConsoleBuilder(TextConsoleBuidlerFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ApplicationConfigurable2(getProject());
  }

  public String getGeneratedName() {
    if (MAIN_CLASS_NAME == null) {
      return null;
    }
    return ExecutionUtil.getPresentableClassName(MAIN_CLASS_NAME, getConfigurationModule());
  }

  public void setGeneratedName() {
    setName(getGeneratedName());
  }

  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    return RefactoringListeners.
      getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
  }

  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(MAIN_CLASS_NAME);
  }

  public boolean isGeneratedName() {
    if (MAIN_CLASS_NAME == null || MAIN_CLASS_NAME.length() == 0) {
      return ExecutionUtil.isNewName(getName());
    }
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(MAIN_CLASS_NAME), 6) + ".main()";
  }

  public void setMainClassName(final String qualifiedName) {
    final boolean generatedName = isGeneratedName();
    MAIN_CLASS_NAME = qualifiedName;
    if (generatedName) setGeneratedName();
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (ALTERNATIVE_JRE_PATH_ENABLED){
      if (ALTERNATIVE_JRE_PATH == null ||
          ALTERNATIVE_JRE_PATH.length() == 0 ||
          !JavaSdkImpl.checkForJre(ALTERNATIVE_JRE_PATH)){
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.mesage", ALTERNATIVE_JRE_PATH));
      }
    }
    final RunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(MAIN_CLASS_NAME, ExecutionBundle.message("no.main.class.specified.error.text"));
    if (ApplicationConfigurationType.findMainMethod(psiClass.findMethodsByName("main", true)) == null) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("main.method.not.found.in.class.error.message", MAIN_CLASS_NAME));
    }
  }

  public void setProperty(final int property, final String value) {
    switch (property) {
      case PROGRAM_PARAMETERS_PROPERTY:
        PROGRAM_PARAMETERS = value;
        break;
      case VM_PARAMETERS_PROPERTY:
        VM_PARAMETERS = value;
        break;
      case WORKING_DIRECTORY_PROPERTY:
        WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
        break;
      default:
        throw new RuntimeException("Unknown property: " + property);
    }
  }

  public String getProperty(final int property) {
    switch (property) {
      case PROGRAM_PARAMETERS_PROPERTY:
        return PROGRAM_PARAMETERS;
      case VM_PARAMETERS_PROPERTY:
        return VM_PARAMETERS;
      case WORKING_DIRECTORY_PROPERTY:
        return getWorkingDirectory();
      default:
        throw new RuntimeException("Unknown property: " + property);
    }
  }

  private String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  public Collection<Module> getValidModules() {
    return RunConfigurationModule.getModulesForClass(getProject(), MAIN_CLASS_NAME);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new ApplicationConfiguration(getName(), getProject(), ApplicationConfigurationType.getInstance());
  }

  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    readModule(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeModule(element);
  }

  public int getLastSnapShooterPort() {
    return myLastSnapShooterPort;
  }

  public void setSnapShooterNotifyRunnable(final Runnable snapShooterNotifyRunnable) {
    mySnapShooterNotifyRunnable = snapShooterNotifyRunnable;
  }

  private class MyJavaCommandLineState extends JavaCommandLineState {
    public MyJavaCommandLineState(final RunnerSettings runnerSettings, final ConfigurationPerRunnerSettings configurationSettings) {
      super(runnerSettings, configurationSettings);
    }

    protected JavaParameters createJavaParameters() throws ExecutionException {
      final JavaParameters params = new JavaParameters();
      JavaParametersUtil.configureModule(getConfigurationModule(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, ALTERNATIVE_JRE_PATH_ENABLED ? ALTERNATIVE_JRE_PATH : null);
      JavaParametersUtil.configureConfiguration(params, ApplicationConfiguration.this);

      if (ENABLE_SWING_INSPECTOR) {
        try {
          myLastSnapShooterPort = NetUtils.findAvailableSocketPort();
        }
        catch(IOException ex) {
          myLastSnapShooterPort = -1;
        }
      }

      if (ENABLE_SWING_INSPECTOR && myLastSnapShooterPort != -1) {
        params.getProgramParametersList().prepend(MAIN_CLASS_NAME);
        params.getProgramParametersList().prepend(Integer.toString(myLastSnapShooterPort));
        Set<String> paths = new TreeSet<String>();
        paths.add(PathUtil.getJarPathForClass(SnapShooter.class));         // ui-designer-impl
        paths.add(PathUtil.getJarPathForClass(BaseComponent.class));       // appcore-api
        paths.add(PathUtil.getJarPathForClass(ProjectComponent.class));    // openapi
        paths.add(PathUtil.getJarPathForClass(LwComponent.class));         // UIDesignerCore
        paths.add(PathUtil.getJarPathForClass(GridConstraints.class));     // forms_rt
        paths.add(PathUtil.getJarPathForClass(JDOMExternalizable.class));  // util
        paths.add(PathUtil.getJarPathForClass(Document.class));            // JDOM
        paths.add(PathUtil.getJarPathForClass(LafManagerListener.class));  // ui-impl
        paths.add(PathUtil.getJarPathForClass(DataProvider.class));        // action-system-openapi
        paths.add(PathUtil.getJarPathForClass(XmlUtil.class));             // idea
        paths.add(PathUtil.getJarPathForClass(Navigatable.class));         // pom
        paths.add(PathUtil.getJarPathForClass(AreaInstance.class));        // extensions
        paths.add(PathUtil.getJarPathForClass(THashMap.class));            // trove4j
        for(String path: paths) {
          params.getClassPath().addTail(path);
        }
        params.setMainClass("com.intellij.uiDesigner.snapShooter.SnapShooter");
      }
      else {
        params.setMainClass(MAIN_CLASS_NAME);
      }
      return params;
    }

    @Override
    protected OSProcessHandler startProcess() throws ExecutionException {
      final OSProcessHandler handler = super.startProcess();
      final Runnable notifyRunnable = mySnapShooterNotifyRunnable;
      if (notifyRunnable != null) {
        handler.addProcessListener(new ProcessAdapter() {
          public void startNotified(final ProcessEvent event) {
            notifyRunnable.run();
          }
        });
      }
      mySnapShooterNotifyRunnable = null;
      return handler;
    }
  }
}