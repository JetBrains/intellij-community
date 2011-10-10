package org.jetbrains.jps.idea

import org.apache.tools.ant.BuildException
import org.codehaus.gant.GantBinding

/**
 * @author nik
 */
public class AntErrorReporter implements ProjectLoadingErrorReporter {
  private GantBinding binding

  public AntErrorReporter(GantBinding binding) {
    this.binding = binding;
  }

  public void error(String message) {
    throw new BuildException(message)
  }

  public void warning(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_WARN)
  }

  public void info(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_INFO)
  }
}
