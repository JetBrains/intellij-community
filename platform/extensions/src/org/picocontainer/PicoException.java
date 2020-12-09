/*
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant
 */
package org.picocontainer;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Superclass for all Exceptions in PicoContainer. You can use this if you want to catch all exceptions thrown by
 * PicoContainer. Be aware that some parts of the PicoContainer API will also throw {@link NullPointerException} when
 * <code>null</code> values are provided for method arguments, and this is not allowed.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @version $Revision: 1812 $
 * @since 1.0
 */
public class PicoException extends RuntimeException {
  /**
   * The exception that caused this one.
   */
  private Throwable cause;

  /**
   * Construct a new exception with no cause and no detail message. Note modern JVMs may still track the exception
   * that caused this one.
   */
  public PicoException() {
  }

  /**
   * Construct a new exception with no cause and the specified detail message.  Note modern JVMs may still track the
   * exception that caused this one.
   *
   * @param message the message detailing the exception.
   */
  public PicoException(@NotNull String message) {
    super(message);
  }

  /**
   * Construct a new exception with the specified cause and no detail message.
   *
   * @param cause the exception that caused this one.
   */
  public PicoException(final Throwable cause) {
    this.cause = cause;
  }

  /**
   * Construct a new exception with the specified cause and the specified detail message.
   *
   * @param message the message detailing the exception.
   * @param cause   the exception that caused this one.
   */
  public PicoException(final String message, final Throwable cause) {
    super(message);
    this.cause = cause;
  }

  /**
   * Retrieve the exception that caused this one.
   *
   * @return the exception that caused this one, or null if it was not set.
   * @see Throwable#getCause() the method available since JDK 1.4 that is overridden by this method.
   */
  @Override
  public Throwable getCause() {
    return cause;
  }

  /**
   * Overridden to provide 1.4 style stack traces on pre-1.4.
   */
  @Override
  public void printStackTrace() {
    printStackTrace(System.err);
  }

  /**
   * Overridden to provide 1.4 style stack traces on pre-1.4.
   */
  @Override
  public void printStackTrace(PrintStream s) {
    super.printStackTrace(s);
    if (cause != null) {
      s.println("Caused by:\n");
      cause.printStackTrace(s);
    }
  }

  /**
   * Overridden to provide 1.4 style stack traces on pre-1.4.
   *
   * @param s the {@link PrintWriter} used to print the stack trace
   */
  @Override
  public void printStackTrace(PrintWriter s) {
    super.printStackTrace(s);
    if (cause != null) {
      s.println("Caused by:\n");
      cause.printStackTrace(s);
    }
  }
}
