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

import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoVisitor;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * empty pico container serving as recoil damper in situations where you
 * do not like to check whether container reference suplpied to you
 * is null or not
 * @author  Konstantin Pribluda
 * @since 1.1
*/
public class EmptyPicoContainer implements PicoContainer, Serializable {
    @Override
    public Object getComponentInstance(Object componentKey) {
        return null;
    }

    @Override
    public Object getComponentInstanceOfType(Class componentType) {
        return null;
    }
    @Override
    public List getComponentInstances() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public PicoContainer getParent() {
        return null;
    }
    @Override
    public ComponentAdapter getComponentAdapter(Object componentKey) {
        return null;
    }

    @Override
    public ComponentAdapter getComponentAdapterOfType(Class componentType) {
        return null;
    }

    @Override
    public Collection getComponentAdapters() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List getComponentAdaptersOfType(Class componentType) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public void verify()  {}

    @Override
    public void accept(PicoVisitor visitor) { }

    @Override
    public List getComponentInstancesOfType(Class componentType) {
        return Collections.EMPTY_LIST;
    }

    public void start() {}
    public void stop() {}
    @Override
    public void dispose() {}
}
