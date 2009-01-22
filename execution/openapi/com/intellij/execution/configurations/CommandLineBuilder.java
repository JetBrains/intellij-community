/*
 * @author max
 */
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Computable;

public class CommandLineBuilder {
  private static final Logger LOG = Logger.getInstance("#" + CommandLineBuilder.class.getName());

  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters) throws CantRunException {
    return createFromJavaParameters(javaParameters, false);
  }

  /**
   * In order to avoid too long cmd problem dynamic classpath can be used
   * @param dynamicClasspath whether system properties and project settings will be able to cause using dynamic classpath. If false,
   * classpath will always be passed through the command line.
   */
  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters, final Project project, final boolean dynamicClasspath) throws CantRunException {
    return createFromJavaParameters(javaParameters, dynamicClasspath && JdkUtil.useDynamicClasspath(project));
  }

  /**
   * @param javaParameters parameters
   * @param forceDynamicClasspath whether dynamic classpath will be used for this execution, to prevent problems caused by too long command line
   * @return command line
   * @throws CantRunException if there are problems with JDK setup
   */
  public static GeneralCommandLine createFromJavaParameters(final SimpleJavaParameters javaParameters, final boolean forceDynamicClasspath) throws CantRunException {
    try {
      return ApplicationManager.getApplication().runReadAction(new Computable<GeneralCommandLine>() {
        public GeneralCommandLine compute() {
          try {
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
            if (javaParameters.getMainClass() == null) {
              throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
            }

            return JdkUtil.setupJVMCommandLine(exePath, javaParameters, forceDynamicClasspath);
          }
          catch (CantRunException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (RuntimeException e) {
      if(e.getCause() instanceof CantRunException)
        throw (CantRunException)e.getCause();
      else
        throw e;
    }
  }

}
