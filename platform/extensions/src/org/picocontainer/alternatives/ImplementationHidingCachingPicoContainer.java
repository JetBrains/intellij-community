/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by the committers                                           *
 *****************************************************************************/
package org.picocontainer.alternatives;

import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.CachingComponentAdapterFactory;
import org.picocontainer.defaults.ComponentAdapterFactory;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapterFactory;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.Serializable;

/**
 * This special MutablePicoContainer hides implementations of components if the key is an interface.
 * It's very simple. Instances that are registered directly and components registered without key
 * are not hidden. Hiding is achieved with dynamic proxies from Java's reflection api. It also exhibits caching
 * functionality.
 *
 * @see CachingPicoContainer
 * @see ImplementationHidingPicoContainer
 * @author Paul Hammant
 * @version $Revision: 2424 $
 * @since 1.1
 */
public class ImplementationHidingCachingPicoContainer extends AbstractDelegatingMutablePicoContainer implements Serializable {
    private final ComponentAdapterFactory caf;

    /**
     * Creates a new container with a parent container.
     */
    public ImplementationHidingCachingPicoContainer(ComponentAdapterFactory caf, PicoContainer parent) {
        super(new DefaultPicoContainer(makeComponentAdapterFactory(caf), parent));
        this.caf = caf;
    }

    private static CachingComponentAdapterFactory makeComponentAdapterFactory(ComponentAdapterFactory caf) {
        if (caf instanceof CachingComponentAdapterFactory) {
            // assume that implementation hiding  CAF inside Caching one.
            return (CachingComponentAdapterFactory) caf;
        }
        if (caf instanceof ImplementationHidingComponentAdapterFactory) {
            return new CachingComponentAdapterFactory(caf);
        }
        return new CachingComponentAdapterFactory(new ImplementationHidingComponentAdapterFactory(caf, false));
    }

    /**
     * Creates a new container with a parent container.
     */
    public ImplementationHidingCachingPicoContainer(PicoContainer parent) {
        this(makeComponentAdapterFactory(new ConstructorInjectionComponentAdapterFactory()), parent);
    }

    /**
     * Creates a new container with a parent container.
     */
    public ImplementationHidingCachingPicoContainer(ComponentAdapterFactory caf) {
        this(makeComponentAdapterFactory(caf), null);
    }


    /**
     * Creates a new container with no parent container.
     */
    public ImplementationHidingCachingPicoContainer() {
        this((PicoContainer) null);
    }


    @Override
    public MutablePicoContainer makeChildContainer() {
        ImplementationHidingCachingPicoContainer pc = new ImplementationHidingCachingPicoContainer(caf, this);
        getDelegate().addChildContainer(pc);
        return pc;
    }

}
