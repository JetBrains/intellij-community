package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

import java.nio.charset.Charset;

/**
 * Utility class for running an external process and capturing its standard output and error streams.
 *
 * @author yole
 */
public class CapturingProcessHandler extends OSProcessHandler {
  private final Charset myCharset;
  private final ProcessOutput myOutput = new ProcessOutput();

  public CapturingProcessHandler(final Process process) {
    this(process, null, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset) {
    this(process, charset, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset, final String commandLine) {
    super(process, commandLine);
    myCharset = charset;
    addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          myOutput.appendStdout(event.getText());
        }
        if (outputType == ProcessOutputTypes.STDERR) {
          myOutput.appendStderr(event.getText());
        }
      }
    });
  }

  public ProcessOutput runProcess() {
    startNotify();
    waitFor();
    myOutput.setExitCode(getProcess().exitValue());
    return myOutput;
  }

  public ProcessOutput runProcess(int timeoutInMilliseconds) {
    if (timeoutInMilliseconds < 0) {
      return runProcess();
    }
    startNotify();
    if (waitFor(timeoutInMilliseconds)) {
      myOutput.setExitCode(getProcess().exitValue());
    }
    else {
      destroyProcess();
      myOutput.setTimeout();
    }
    return myOutput;
  }

  @Override
  public Charset getCharset() {
    if (myCharset != null) {
      return myCharset;
    }
    return super.getCharset();
  }
}
