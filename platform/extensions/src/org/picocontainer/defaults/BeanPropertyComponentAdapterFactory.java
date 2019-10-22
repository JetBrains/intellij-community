/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.PicoIntrospectionException;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ComponentAdapterFactory} that creates
 * {@link BeanPropertyComponentAdapter} instances.
 *
 * @author Aslak Helles&oslash;y
 * @version $Revision: 2320 $
 * @since 1.0
 */
public class BeanPropertyComponentAdapterFactory extends DecoratingComponentAdapterFactory {
    private Map adapterCache = new HashMap();

    /**
     * Construct a BeanPropertyComponentAdapterFactory.
     *
     * @param delegate the wrapped factory.
     */
    public BeanPropertyComponentAdapterFactory(ComponentAdapterFactory delegate) {
        super(delegate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComponentAdapter createComponentAdapter(Object componentKey, Class componentImplementation, Parameter[] parameters) throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
        ComponentAdapter decoratedAdapter = super.createComponentAdapter(componentKey, componentImplementation, parameters);
        BeanPropertyComponentAdapter propertyAdapter = new BeanPropertyComponentAdapter(decoratedAdapter);
        adapterCache.put(componentKey, propertyAdapter);
        return propertyAdapter;
    }

    // TODO: What is this method for? It is not used in complete Pico/Nano and caching is normally done by CachingCA ...
    /**
     * @deprecated
     */
    public BeanPropertyComponentAdapter getComponentAdapter(Object key) {
        return (BeanPropertyComponentAdapter) adapterCache.get(key);
    }
}
