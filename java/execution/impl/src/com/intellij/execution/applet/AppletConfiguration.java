/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.applet;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

public class AppletConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> implements SingleClassConfiguration, RefactoringListenerProvider,
                                                                                                         PersistentStateComponent<Element> {
  public AppletConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(new JavaRunConfigurationModule(project, false), factory);
  }

  @Override
  public AppletConfigurationOptions getOptions() {
    return (AppletConfigurationOptions)super.getOptions();
  }

  @Override
  protected Class<AppletConfigurationOptions> getOptionsClass() {
    return AppletConfigurationOptions.class;
  }

  @Override
  public void setMainClass(final PsiClass psiClass) {
    final Module originalModule = getConfigurationModule().getModule();
    setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(psiClass));
    setModule(JavaExecutionUtil.findModule(psiClass));
    restoreOriginalModule(originalModule);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new JavaCommandLineState(env) {
      private AppletHtmlFile myHtmlURL = null;

      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = new JavaParameters();
        myHtmlURL = getHtmlURL();
        final int classPathType = myHtmlURL.isHttp() ? JavaParameters.JDK_ONLY : JavaParameters.JDK_AND_CLASSES;
        final RunConfigurationModule runConfigurationModule = getConfigurationModule();
        JavaParametersUtil.configureModule(runConfigurationModule, params, classPathType, getOptions().isAlternativeJrePathEnabled() ? getOptions().getAlternativeJrePath() : null);
        final String policyFileParameter = getPolicyFileParameter();
        if (policyFileParameter != null) {
          params.getVMParametersList().add(policyFileParameter);
        }
        params.getVMParametersList().addParametersString(getOptions().getVmParameters());
        params.setMainClass("sun.applet.AppletViewer");
        params.getProgramParametersList().add(myHtmlURL.getUrl());
        return params;
      }

      @Override
      @NotNull
      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        final AppletHtmlFile htmlUrl = myHtmlURL;
        if (htmlUrl != null) {
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              htmlUrl.deleteFile();
            }
          });
        }
        return handler;
      }
    };
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AppletConfigurable(getProject());
  }

  private String getPolicyFileParameter() {
    if (!StringUtil.isEmpty(getOptions().getPolicyFile())) {
      //noinspection SpellCheckingInspection
      return "-Djava.security.policy=" + getPolicyFile();
    }
    return null;
  }

  @Transient
  public String getPolicyFile() {
    return ExternalizablePath.localPathValue(getOptions().getPolicyFile());
  }

  public void setPolicyFile(final String localPath) {
    getOptions().setPolicyFile(ExternalizablePath.urlValue(localPath));
  }

  @Override
  public Collection<Module> getValidModules() {
    return JavaRunConfigurationModule.getModulesForClass(getProject(), getOptions().getMainClassName());
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    super.writeExternal(element);
    return element;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    if (getOptions().getHtmlUsed()) {
      return null;
    }
    return RefactoringListeners.getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
  }

  @Override
  @Transient
  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(getOptions().getMainClassName());
  }

  @Override
  public String suggestedName() {
    if (getOptions().getMainClassName() == null) {
      return null;
    }
    return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(getOptions().getMainClassName()), 0);
  }

  @Override
  public void setMainClassName(@Nullable String qualifiedName) {
    getOptions().setMainClassName(qualifiedName);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getOptions().isAlternativeJrePathEnabled() && (StringUtil.isEmptyOrSpaces(getOptions().getAlternativeJrePath()) || !JdkUtil.checkForJre(getOptions().getAlternativeJrePath()))) {
      throw new RuntimeConfigurationWarning(ExecutionBundle.message("jre.not.valid.error.message", getOptions().getAlternativeJrePath()));
    }
    getConfigurationModule().checkForWarning();
    if (getOptions().getHtmlUsed()) {
      if (getOptions().getHtmlFileName() == null) {
        throw new RuntimeConfigurationWarning(ExecutionBundle.message("html.file.not.specified.error.message"));
      }
      try {
        new URL(getHtmlURL().getUrl());
      }
      catch (CantRunException | MalformedURLException ex) {
        checkUrlIsValid(ex);
      }
    }
    else {
      getConfigurationModule().checkClassName(getOptions().getMainClassName(), ExecutionBundle.message("no.applet.class.specified.error.message"));
    }
  }

  private void checkUrlIsValid(Exception ex) throws RuntimeConfigurationWarning {
    throw new RuntimeConfigurationWarning("URL " + getOptions().getHtmlFileName() + " is not valid: " + ex.getLocalizedMessage());
  }

  private AppletHtmlFile getHtmlURL() throws CantRunException {
    if (getOptions().getHtmlUsed()) {
      if (getOptions().getHtmlFileName() == null) {
        throw new CantRunException(ExecutionBundle.message("html.file.not.specified.error.message"));
      }
      return new AppletHtmlFile(getOptions().getHtmlFileName(), null);
    }
    else {
      if (getOptions().getMainClassName() == null) {
        throw new CantRunException(ExecutionBundle.message("class.not.specified.error.message"));
      }

      // generate html
      try {
        return generateAppletTempPage();
      }
      catch (IOException ignored) {
        throw new CantRunException(ExecutionBundle.message("failed.to.generate.wrapper.error.message"));
      }
    }
  }

  @NotNull
  private AppletHtmlFile generateAppletTempPage() throws IOException {
    final File tempFile = FileUtil.createTempFile("AppletPage", ".html");
    @NonNls final FileWriter writer = new FileWriter(tempFile);
    try {
      writer.write("<html>\n" +
                   "<head>\n" +
                   "<title>" + getOptions().getMainClassName() + "</title>\n" +
                   "</head>\n" +
                   "<applet codebase=\".\"\n" +
                   "code=\"" + getOptions().getMainClassName() + "\"\n" +
                   "name=\"" + getOptions().getMainClassName() + "\"\n" +
                   "width=" + getOptions().getWidth() + "\n" +
                   "height=" + getOptions().getHeight() + "\n" +
                   "align=top>\n");
      for (AppletParameter parameter : getOptions().getAppletParameters()) {
        writer.write("<param name=\"" + parameter.getName() + "\" value=\"" + parameter.getValue() + "\">\n");
      }
      writer.write("</applet>\n</body>\n</html>\n");
    }
    finally {
      writer.close();
    }
    return new AppletHtmlFile(tempFile.getAbsolutePath(), tempFile);
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
          //noinspection deprecation
          return new File(myHtmlFile).toURL().toString();
        }
        catch (MalformedURLException ignored) {
        }
      }
      return myHtmlFile;
    }

    public boolean isHttp() {
      return StringUtil.startsWithIgnoreCase(myHtmlFile, HTTP_PREFIX) || StringUtil.startsWithIgnoreCase(myHtmlFile, HTTPS_PREFIX);
    }

    public void deleteFile() {
      if (myFileToDelete != null) {
        //noinspection ResultOfMethodCallIgnored
        myFileToDelete.delete();
      }
    }
  }
}
