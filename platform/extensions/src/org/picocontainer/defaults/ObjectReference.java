/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 *****************************************************************************/
package org.picocontainer.defaults;

/**
 * A way to refer to objects that are stored in "awkward" places (for example inside a
 * <code>HttpSession</code> or {@link ThreadLocal}).
 * <p/>
 * This interface is typically implemented by someone integrating Pico into an existing container.
 *
 * @author <a href="mailto:joe@thoughtworks.net">Joe Walnes</a>
 */
public interface ObjectReference {
    /**
     * Retrieve an actual reference to the object. Returns null if the reference is not available
     * or has not been populated yet.
     * 
     * @return an actual reference to the object.
     */
    Object get();

    /**
     * Assign an object to the reference.
     * 
     * @param item the object to assign to the reference. May be <code>null</code>.
     */
    void set(Object item);
}
