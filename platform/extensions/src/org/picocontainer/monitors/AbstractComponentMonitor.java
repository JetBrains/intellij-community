/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Paul Hammaant                                            *
 *****************************************************************************/

package org.picocontainer.monitors;

import org.picocontainer.ComponentMonitor;

import java.text.MessageFormat;

/**
 * An abstract {@link ComponentMonitor} which supports all the message formats.
 *
 * @author Mauro Talevi
 * @version $Revision: $
 */
public abstract class AbstractComponentMonitor implements ComponentMonitor {

    public final static String INSTANTIATING = "PicoContainer: instantiating {0}";
    public final static String INSTANTIATED = "PicoContainer: instantiated {0} [{1} ms]";
    public final static String INSTANTIATION_FAILED = "PicoContainer: instantiation failed: {0}, reason: {1}";
    public final static String INVOKING = "PicoContainer: invoking {0} on {1}";
    public final static String INVOKED = "PicoContainer: invoked {0} on {1} [{2} ms]";
    public final static String INVOCATION_FAILED = "PicoContainer: invocation failed: {0} on {1}, reason: {2}";
    public final static String LIFECYCLE_INVOCATION_FAILED = "PicoContainer: lifecycle invocation failed: {0} on {1}, reason: {2}";

    public static String format(String template, Object[] arguments) {
        return MessageFormat.format(template, arguments);
    }


}
