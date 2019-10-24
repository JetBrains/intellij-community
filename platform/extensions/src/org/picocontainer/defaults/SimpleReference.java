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

import java.io.Serializable;

/**
 * @author Aslak Helles&oslash;y
 * @version $Revision: 940 $
 */
public class SimpleReference implements ObjectReference, Serializable {
    private Object instance;

    @Override
    public Object get() {
        return instance;
    }

    @Override
    public void set(Object item) {
        this.instance = item;
    }
}
