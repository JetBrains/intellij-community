/*
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant
 */
package com.intellij.util.pico;

/**
 * Subclass of {@link PicoException} that is thrown when there is a problem creating, providing or locating a component
 * instance or a part of the PicoContainer API, for example, when a request for a component is ambiguous.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @version $Revision: 1.3 $
 * @since 1.0
 */
class PicoIntrospectionException extends PicoException {

  /**
   * Construct a new exception with no cause and the specified detail message.  Note modern JVMs may still track the
   * exception that caused this one.
   *
   * @param message the message detailing the exception.
   */
  PicoIntrospectionException(final String message) {
    super(message);
  }

  /**
   * Construct a new exception with the specified cause and no detail message.
   *
   * @param cause the exception that caused this one.
   */
  protected PicoIntrospectionException(final Throwable cause) {
    super(cause);
  }
}
