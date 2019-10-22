/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.alternatives;

import org.picocontainer.ComponentAdapter;


/**
 * This component adapter makes it possible to hide the implementation of a real subject (behind a proxy) provided the
 * key is an interface. <p/> This class exists here, because a) it has no deps on external jars, b) dynamic proxy is
 * quite easy. The user is prompted to look at picocontainer-gems for alternate and bigger implementations.
 * 
 * @author Aslak Helles&oslash;y
 * @author Paul Hammant
 * @see org.picocontainer.gems.HotSwappingComponentAdapter for a more feature-rich version of this class.
 * @since 1.1
 * @deprecated since 1.2, moved to package {@link org.picocontainer.defaults}
 */
public class ImplementationHidingComponentAdapter extends
        org.picocontainer.defaults.ImplementationHidingComponentAdapter {

    /**
     * Creates an ImplementationHidingComponentAdapter with a delegate
     * 
     * @param delegate the component adapter to which this adapter delegates
     * @param strict the scrict mode boolean
     * @deprecated since 1.2, moved to package {@link org.picocontainer.defaults}
     */
    public ImplementationHidingComponentAdapter(ComponentAdapter delegate, boolean strict) {
        super(delegate, strict);
    }

}
