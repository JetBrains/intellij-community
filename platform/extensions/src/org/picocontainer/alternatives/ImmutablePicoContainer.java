/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Paul Hammant                                             *
 *****************************************************************************/
package org.picocontainer.alternatives;

import org.picocontainer.*;
import org.picocontainer.defaults.ImmutablePicoContainerProxyFactory;
import org.picocontainer.defaults.VerifyingVisitor;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

// TODO: replace this with a proxy? It don't do nothing! (AH)
// Am open to elegant solution. This, at least, is instantiable (PH)

/**
 * @author Paul Hammant
 * @version $Revision: 2286 $
 * @since 1.1
 * @deprecated since 1.2, use the {@link ImmutablePicoContainerProxyFactory}
 */
public class ImmutablePicoContainer implements PicoContainer, Serializable {

    private PicoContainer delegate;

    public ImmutablePicoContainer(PicoContainer delegate) {
        if(delegate == null) throw new NullPointerException("You must pass in a picoContainer instance");
        this.delegate = delegate;
    }

    @Override
    public Object getComponentInstance(Object componentKey) {
        return delegate.getComponentInstance(componentKey);
    }

    @Override
    public Object getComponentInstanceOfType(Class componentType) {
        return delegate.getComponentInstanceOfType(componentType);
    }

    @Override
    public List getComponentInstances() {
        return delegate.getComponentInstances();
    }

    @Override
    public synchronized PicoContainer getParent() {
        return delegate.getParent();
    }

    @Override
    public ComponentAdapter getComponentAdapter(Object componentKey) {
        return delegate.getComponentAdapter(componentKey);
    }

    @Override
    public ComponentAdapter getComponentAdapterOfType(Class componentType) {
        return delegate.getComponentAdapterOfType(componentType);
    }

    @Override
    public Collection getComponentAdapters() {
        return delegate.getComponentAdapters();
    }

    @Override
    public List getComponentAdaptersOfType(Class componentType) {
        return delegate.getComponentAdaptersOfType(componentType);
    }

    /**
     * @deprecated since 1.1 - Use "new VerifyingVisitor().traverse(this)"
     */
    @Override
    public void verify() throws PicoVerificationException {
        new VerifyingVisitor().traverse(this);
    }

    @Override
    public List getComponentInstancesOfType(Class type) throws PicoException {
        return delegate.getComponentInstancesOfType(type);
    }

    @Override
    public void accept(PicoVisitor visitor) {
        delegate.accept(visitor);
    }

    public void start() {
        // This is false security. As long as components can be accessed with getComponentInstance(), they can also be started. (AH).
        throw new UnsupportedOperationException("This container is immutable, start() is not allowed");
    }

    public void stop() {
        // This is false security. As long as components can be accessed with getComponentInstance(), they can also be stopped. (AH).
        throw new UnsupportedOperationException("This container is immutable, stop() is not allowed");
    }

    @Override
    public void dispose() {
        // This is false security. As long as components can be accessed with getComponentInstance(), they can also be disposed. (AH).
        throw new UnsupportedOperationException("This container is immutable, dispose() is not allowed");
    }
}
