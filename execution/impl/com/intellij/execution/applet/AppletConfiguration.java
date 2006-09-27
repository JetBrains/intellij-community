package com.intellij.execution.applet;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class AppletConfiguration extends ModuleBasedConfiguration implements SingleClassConfiguration {

  public String MAIN_CLASS_NAME;
  public String HTML_FILE_NAME;
  public boolean HTML_USED;
  public int WIDTH;
  public int HEIGHT;
  public String POLICY_FILE;
  public String VM_PARAMETERS;
  private AppletParameter[] myAppletParameters;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;
  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String VALUE_ATTR = "value";
  @NonNls
  protected static final String PARAMETER_ELEMENT_NAME = "parameter";

  public AppletConfiguration(final String name, final Project project, ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project, false), factory);
  }

  public void setMainClass(final PsiClass psiClass) {
    setMainClassName(ExecutionUtil.getRuntimeQualifiedName(psiClass));
    setModule(ExecutionUtil.findModule(psiClass));
  }

  public RunProfileState getState(final DataContext context,
                                  final RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) {
    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      private AppletHtmlFile myHtmlURL = null;

      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = new JavaParameters();
        myHtmlURL = getHtmlURL();
        if (myHtmlURL != null) {
          final int classPathType = myHtmlURL.isHttp() ? JavaParameters.JDK_ONLY : JavaParameters.JDK_AND_CLASSES_AND_TESTS;
          final RunConfigurationModule runConfigurationModule = getConfigurationModule();
          JavaParametersUtil.configureModule(runConfigurationModule, params, classPathType, ALTERNATIVE_JRE_PATH_ENABLED ? ALTERNATIVE_JRE_PATH : null);
          final String policyFileParameter = getPolicyFileParameter();
          if (policyFileParameter != null) {
            params.getVMParametersList().add(policyFileParameter);
          }
          params.getVMParametersList().addParametersString(VM_PARAMETERS);
          params.setMainClass("sun.applet.AppletViewer");
         /* if (params.getJdk().getVersionString().indexOf("1.1") > -1) {
            params.getClassPath().add(params.getJdkPath() + File.separator + "lib" + File.separator + "classes.zip");
          }
          else {
            params.getClassPath().add(params.getJdkPath() + File.separator + "lib" + File.separator + "tools.jar");
          }*/
          params.getProgramParametersList().add(myHtmlURL.getUrl());
        }
        return params;
      }

      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        final AppletHtmlFile htmlUrl = myHtmlURL;
        if (htmlUrl != null) {
          handler.addProcessListener(new ProcessAdapter() {
            public void processTerminated(ProcessEvent event) {
              htmlUrl.deleteFile();
            }
          });
        }
        return handler;
      }
    };
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AppletConfigurable(getProject());
  }

  @NonNls private String getPolicyFileParameter() {
    if (POLICY_FILE != null && POLICY_FILE.length() > 0) {
      return "-Djava.security.policy=" + getPolicyFile();
    }
    return null;
  }

  public void setPolicyFile(final String localPath) {
    POLICY_FILE = ExternalizablePath.urlValue(localPath);
  }

  public String getPolicyFile() {
    return ExternalizablePath.localPathValue(POLICY_FILE);
  }

  public static class AppletParameter {
    public String myName;
    public String myValue;

    public AppletParameter(@NonNls final String name, final String value) {
      myName = name;
      myValue = value;
    }

    public String getName() {
      return myName;
    }

    public void setName(final String name) {
      myName = name;
    }

    public String getValue() {
      return myValue;
    }

    public void setValue(final String value) {
      myValue = value;
    }

    public boolean equals(final Object obj) {
      if (!(obj instanceof AppletParameter)) return false;
      final AppletParameter second = (AppletParameter)obj;
      return Comparing.equal(myName, second.myName) && Comparing.equal(myValue, second.myValue);
    }

    public int hashCode() {
      return Comparing.hashcode(myName, myValue);
    }
  }

  public Collection<Module> getValidModules() {
    return RunConfigurationModule.getModulesForClass(getProject(), MAIN_CLASS_NAME);
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    readModule(parentNode);
    final ArrayList<AppletParameter> parameters = new ArrayList<AppletParameter>();
    for (
      Iterator iterator = parentNode.getChildren(PARAMETER_ELEMENT_NAME).iterator(); iterator.hasNext();) {
      final Element element = (Element)iterator.next();
      final String name = element.getAttributeValue(NAME_ATTR);
      final String value = element.getAttributeValue(VALUE_ATTR);
      parameters.add(new AppletParameter(name, value));
    }
    myAppletParameters = parameters.toArray(new AppletParameter[parameters.size()]);
  }

  public void writeExternal(final Element parentNode) throws WriteExternalException {
    writeModule(parentNode);
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    if (myAppletParameters != null) {
      for (int i = 0; i < myAppletParameters.length; i++) {
        final Element element = new Element(PARAMETER_ELEMENT_NAME);
        parentNode.addContent(element);
        element.setAttribute(NAME_ATTR, myAppletParameters[i].getName());
        element.setAttribute(VALUE_ATTR, myAppletParameters[i].getValue());
      }
    }
  }

  protected ModuleBasedConfiguration createInstance() {
    return new AppletConfiguration(getName(), getProject(), AppletConfigurationType.getInstance().getConfigurationFactories()[0]);
  }

  public String getGeneratedName() {
    if (MAIN_CLASS_NAME == null) return null;
    return ExecutionUtil.getPresentableClassName(MAIN_CLASS_NAME, getConfigurationModule());
  }

  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    if (HTML_USED) return super.getRefactoringElementListener(element);
    return RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
  }

  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(MAIN_CLASS_NAME);
  }

  public void setGeneratedName() {
    setName(getGeneratedName());
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(MAIN_CLASS_NAME), 0);
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
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("jre.not.valid.error.message", ALTERNATIVE_JRE_PATH));
      }
    }
    getConfigurationModule().checkForWarning();
    if (HTML_USED) {
      if (HTML_FILE_NAME == null || HTML_FILE_NAME.length() == 0) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("html.file.not.specified.error.message"));
      }
    }
    else {
      getConfigurationModule().checkClassName(MAIN_CLASS_NAME, ExecutionBundle.message("no.applet.class.specified.error.message"));
    }
  }

  public AppletParameter[] getAppletParameters() {
    return myAppletParameters;
  }

  public void setAppletParameters(final AppletParameter[] appletParameters) {
    myAppletParameters = appletParameters;
  }

  public void setAppletParameters(final List<AppletParameter> parameters) {
    setAppletParameters(parameters.toArray(new AppletParameter[parameters.size()]));
  }

  private AppletHtmlFile getHtmlURL() throws CantRunException {
    if (HTML_USED) {
      if (HTML_FILE_NAME == null || HTML_FILE_NAME.length() == 0) {
        throw new CantRunException(ExecutionBundle.message("html.file.not.specified.error.message"));
      }
      return new AppletHtmlFile(HTML_FILE_NAME, null);
    }
    else {
      if (MAIN_CLASS_NAME == null || MAIN_CLASS_NAME.length() == 0) {
        throw new CantRunException(ExecutionBundle.message("class.not.specified.error.message"));
      }

      // generate html
      try {
        return generateAppletTempPage();
      }
      catch (IOException e) {
        throw new CantRunException(ExecutionBundle.message("failed.to.generate.wrapper.error.message"));
      }
    }
  }

  private AppletHtmlFile generateAppletTempPage() throws IOException {
    //noinspection HardCodedStringLiteral
    final File tempFile = File.createTempFile("AppletPage", ".html");
    @NonNls final FileWriter writer = new FileWriter(tempFile);
    try {
      writer.write("<html>\n" +
                   "<head>\n" +
                   "<title>" + MAIN_CLASS_NAME + "</title>\n" +
                   "</head>\n" +
                   "<applet codebase=\".\"\n" +
                   "code=\"" + MAIN_CLASS_NAME + "\"\n" +
                   "name=\"" + MAIN_CLASS_NAME + "\"\n" +
                   "width=" + WIDTH + "\n" +
                   "height=" + HEIGHT + "\n" +
                   "align=top>\n");
      final AppletParameter[] appletParameters = getAppletParameters();
      if (appletParameters != null) {
        for (final AppletParameter parameter : appletParameters) {
          writer.write("<param name=\"" + parameter.getName() + "\" value=\"" + parameter.getValue() + "\">\n");
        }
      }
      writer.write("</applet>\n</body>\n</html>\n");
    }
    finally {
      writer.close();
    }
    final String htmlFile = tempFile.getAbsolutePath();
    return new AppletHtmlFile(htmlFile, tempFile);
  }

  private static class AppletHtmlFile {
    private final String myHtmlFile;
    private final File myFileToDelete;
    @NonNls
    protected static final String FILE_PREFIX = "file:/";
    @NonNls
    protected static final String HTTP_PREFIX = "http:/";
    @NonNls
    protected static final String HTTPS_PREFIX = "https:/";

    protected AppletHtmlFile(final String htmlFile, final File fileToDelete) {
      myHtmlFile = htmlFile;
      myFileToDelete = fileToDelete;
    }

    public String getUrl() {
      if (!StringUtil.startsWithIgnoreCase(myHtmlFile, FILE_PREFIX) && !isHttp()) {
        try {
          return new File(myHtmlFile).toURL().toString();
        }
        catch (MalformedURLException ex) {
        }
      }
      return myHtmlFile;
    }

    public boolean isHttp() {
      return StringUtil.startsWithIgnoreCase(myHtmlFile, HTTP_PREFIX) || StringUtil.startsWithIgnoreCase(myHtmlFile, HTTPS_PREFIX);
    }

    public void deleteFile() {
      if (myFileToDelete != null) {
        myFileToDelete.delete();
      }
    }
  }
}