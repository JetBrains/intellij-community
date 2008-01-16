/*
 * @author max
 */
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;

import java.nio.charset.Charset;
import java.util.Map;

public class CommandLineBuilder {
  public static GeneralCommandLine createFromJavaParameters(final JavaParameters javaParameters) throws CantRunException {
    try {
      return ApplicationManager.getApplication().runReadAction(new Computable<GeneralCommandLine>() {
        public GeneralCommandLine compute() {
          try {
            final GeneralCommandLine commandLine = new GeneralCommandLine();
            final Sdk jdk = javaParameters.getJdk();
            if(jdk == null) {
              throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
            }

            final SdkType sdkType = jdk.getSdkType();
            if (!(sdkType instanceof JavaSdkType)) {
              throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
            }
            
            final String exePath = ((JavaSdkType)sdkType).getVMExecutablePath(jdk);
            if(exePath == null) {
              throw new CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
            }
            commandLine.setExePath(exePath);
            ParametersList parametersList = javaParameters.getVMParametersList();
            commandLine.addParameters(parametersList.getList());
            if (!parametersList.hasProperty("file.encoding")) {
              Charset charset = javaParameters.getCharset();
              if (charset == null) charset = EncodingManager.getInstance().getDefaultCharset();
              if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset();
              commandLine.setCharset(charset);
            }

            if(!parametersList.hasParameter("-classpath") && !parametersList.hasParameter("-cp")){
              commandLine.addParameter("-classpath");
              commandLine.addParameter(javaParameters.getClassPath().getPathsString());
            }

            String mainClass = javaParameters.getMainClass();
            if(mainClass == null) throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
            commandLine.addParameter(mainClass);
            commandLine.addParameters(javaParameters.getProgramParametersList().getList());
            commandLine.setWorkDirectory(javaParameters.getWorkingDirectory());

            final Map<String, String> env = javaParameters.getEnv();
            if (env != null) {
              commandLine.setEnvParams(env);
              commandLine.setPassParentEnvs(javaParameters.isPassParentEnvs());
            }

            return commandLine;
          }
          catch (CantRunException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      if(e.getCause() instanceof CantRunException)
        throw ((CantRunException)e.getCause());
      else
        throw e;
    }
  }
}