/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant   *
 *****************************************************************************/

package org.picocontainer;

/**
 * Subclass of {@link PicoException} that is thrown when there is a problem initializing the container or some other
 * part of the PicoContainer api, for example, when a cyclic dependency between components occurs.
 * 
 * @version $Revision$
 * @since 1.0
 */
public class PicoInitializationException extends PicoException {
    /**
     * Construct a new exception with no cause and no detail message. Note modern JVMs may still track the exception
     * that caused this one.
     * @deprecated Use public constructors
     */
    protected PicoInitializationException() {
    }

    /**
     * Construct a new exception with no cause and the specified detail message.  Note modern JVMs may still track the
     * exception that caused this one.
     *
     * @param message the message detailing the exception.
     */
    public PicoInitializationException(final String message) {
        super(message);
    }

    /**
     * Construct a new exception with the specified cause and no detail message.
     * 
     * @param cause the exception that caused this one.
     */
    public PicoInitializationException(final Throwable cause) {
        super(cause);
    }

    /**
     * Construct a new exception with the specified cause and the specified detail message.
     *
     * @param message the message detailing the exception.
     * @param cause   the exception that caused this one.
     */
    public PicoInitializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
