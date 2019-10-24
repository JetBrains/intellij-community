/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Aslak Hellesoy and Paul Hammant   *
 *****************************************************************************/

package org.picocontainer.defaults;

import org.picocontainer.PicoInitializationException;

public class PicoInvocationTargetInitializationException extends PicoInitializationException {
    public PicoInvocationTargetInitializationException(Throwable cause) {
        super("InvocationTargetException: "
                + cause.getClass().getName()
                + " " + cause.getMessage()
                , cause);
    }
}
