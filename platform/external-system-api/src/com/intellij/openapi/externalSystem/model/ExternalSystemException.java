package com.intellij.openapi.externalSystem.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * We talk to external system API from the dedicated process, i.e. all external system classes are loaded at that process
 * in order to avoid memory problems at ide process. That means that if external system api throws an exception,
 * it can't be correctly read at the ide process (NoClassDefFoundError and ClassNotFoundException).
 * <p/>
 * This class allows to extract textual description of the target problem and deliver it for further processing without risking to 
 * get the problems mentioned above. I.e. it doesn't require anything specific can be safely delivered to ide process then.
 * 
 * @author Denis Zhdanov
 * @since 10/21/11 11:42 AM
 */
public class ExternalSystemException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  
  private final String myOriginalReason;

  public ExternalSystemException() {
    this(null, null);
  }

  public ExternalSystemException(@Nullable String message) {
    this(message, null);
  }

  public ExternalSystemException(@Nullable Throwable cause) {
    this("", cause);
  }

  public ExternalSystemException(@Nullable String message, @Nullable Throwable cause) {
    super(extractMessage(message, cause));
    if (cause == null) {
      myOriginalReason = "";
      return;
    }
    
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    try {
      cause.printStackTrace(printWriter);
    }
    finally {
      printWriter.close();
    }
    myOriginalReason = stringWriter.toString();
  }
  
  /**
   * @return    textual description of the wrapped exception (if any); empty string otherwise
   */
  @NotNull
  public String getOriginalReason() {
    return myOriginalReason;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  @Override
  public void printStackTrace(PrintStream s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  @Nullable
  private static String extractMessage(@Nullable String message, @Nullable Throwable cause) {
    StringBuilder buffer = new StringBuilder();
    if (message != null) {
      buffer.append(message);
    }
    
    boolean first = true;
    for (Throwable t = cause; t != null; t = t.getCause()) {
      final String m = t.getLocalizedMessage();
      if (m == null) {
        continue;
      }
      if (first) {
        first = false;
      }
      else if (buffer.length() > 0) {
        buffer.append("\n");
      }
      buffer.append(m);
    }
    
    return buffer.toString(); 
  }
}
