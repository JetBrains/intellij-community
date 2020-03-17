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
 * A subclass of {@link PicoRegistrationException} that is thrown during component registration if the
 * component's key is a type and the implementation is not assignable to.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 * @since 1.0
 */
public class AssignabilityRegistrationException extends PicoRegistrationException {
    /**
     * Construct an exception with the type and the unassignable class.
     *
     * @param type  the type used as component key
     * @param clazz the unassignable implementation class
     */
    public AssignabilityRegistrationException(Class type, Class clazz) {
        super("The type:" + type.getName() + "  was not assignable from the class " + clazz.getName());
    }
}
