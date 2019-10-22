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

/**
 * @author Aslak Helles&oslash;y
 * @version $Revision: 1272 $
 */
public class SynchronizedComponentAdapterFactory extends DecoratingComponentAdapterFactory {
    public SynchronizedComponentAdapterFactory(ComponentAdapterFactory delegate) {
        super(delegate);
    }

    @Override
    public ComponentAdapter createComponentAdapter(Object componentKey, Class componentImplementation, Parameter[] parameters) {
        return new SynchronizedComponentAdapter(super.createComponentAdapter(componentKey, componentImplementation, parameters));
    }
}
