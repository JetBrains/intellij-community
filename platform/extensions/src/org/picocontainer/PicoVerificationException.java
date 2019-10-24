/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer;

import java.util.ArrayList;
import java.util.List;


/**
 * Subclass of {@link PicoException} that is thrown when a {@link PicoContainer} hierarchy
 * cannot be verified. A failing verification is caused by ambuigities or missing dependencies
 * between the registered components and their parameters. This exception is designed as a
 * collector for all Exceptions occuring at the verification of the complete container
 * hierarchy. The verification is normally done with the
 *
 * @version $Revision: 1801 $
 * @since 1.0
 */
public class PicoVerificationException
        extends PicoException {
    /**
     * The exceptions that caused this one.
     */
    private final List nestedExceptions = new ArrayList();

    /**
     * Construct a new exception with a list of exceptions that caused this one.
     *
     * @param nestedExceptions the exceptions that caused this one.
     */
    public PicoVerificationException(final List nestedExceptions) {
        this.nestedExceptions.addAll(nestedExceptions);
    }

    /**
     * Retrieve the list of exceptions that caused this one.
     *
     * @return the list of exceptions that caused this one.
     */
    public List getNestedExceptions() {
        return nestedExceptions;
    }

    /**
     * Return a string listing of all the messages associated with the exceptions that caused
     * this one.
     *
     * @return a string listing of all the messages associated with the exceptions that caused
     *               this one.
     */
    @Override
    public String getMessage() {
        return nestedExceptions.toString();
    }
}
