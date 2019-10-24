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

import org.picocontainer.PicoRegistrationException;

/**
 * @author Aslak Hellesoy
 * @version $Revision: 1.6 $
 */
public class NotConcreteRegistrationException extends PicoRegistrationException {
    private final Class componentImplementation;

    public NotConcreteRegistrationException(Class componentImplementation) {
        super("Bad Access: '" + componentImplementation.getName() + "' is not instantiable");
        this.componentImplementation = componentImplementation;
    }

    public Class getComponentImplementation() {
        return componentImplementation;
    }
}
