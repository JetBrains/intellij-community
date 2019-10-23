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

import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.PicoIntrospectionException;

import java.io.Serializable;

/**
 * @author Jon Tirs&eacute;n
 * @version $Revision: 2654 $
 */
public class ConstructorInjectionComponentAdapterFactory implements ComponentAdapterFactory, Serializable {
    private final boolean allowNonPublicClasses;
    private final LifecycleStrategy lifecycleStrategy;

    public ConstructorInjectionComponentAdapterFactory(boolean allowNonPublicClasses, LifecycleStrategy lifecycleStrategy) {
        this.allowNonPublicClasses = allowNonPublicClasses;
        this.lifecycleStrategy = lifecycleStrategy;
    }


    public ConstructorInjectionComponentAdapterFactory(boolean allowNonPublicClasses) {
        this(allowNonPublicClasses, new DefaultLifecycleStrategy());
    }

    public ConstructorInjectionComponentAdapterFactory() {
        this(false);
    }

    @Override
    public ComponentAdapter createComponentAdapter(Object componentKey,
                                                   Class componentImplementation,
                                                   Parameter[] parameters)
            throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
        return new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters,
                allowNonPublicClasses, lifecycleStrategy);
    }
}
