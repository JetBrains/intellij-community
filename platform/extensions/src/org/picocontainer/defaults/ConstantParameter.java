/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Idea by Rachel Davies, Original code by Jon Tirsen                        *
 *****************************************************************************/

package org.picocontainer.defaults;

import org.picocontainer.*;

import java.io.Serializable;
import java.lang.reflect.Field;


/**
 * A ConstantParameter should be used to pass in "constant" arguments to constructors. This
 * includes {@link String}s,{@link Integer}s or any other object that is not registered in
 * the container.
 *
 * @author Jon Tirs&eacute;n
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @author Thomas Heller
 * @version $Revision: 1801 $
 */
public class ConstantParameter
        implements Parameter, Serializable {

    private final Object value;

    public ConstantParameter(Object value) {
        this.value = value;
    }

    @Override
    public Object resolveInstance(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
        return value;
    }

    @Override
    public boolean isResolvable(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
        try {
            verify(container, adapter, expectedType);
            return true;
        } catch(final PicoIntrospectionException e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see Parameter#verify(PicoContainer,
     *           ComponentAdapter, Class)
     */
    @Override
    public void verify(PicoContainer container, ComponentAdapter adapter, Class expectedType) throws PicoException {
        if (!checkPrimitive(expectedType) && !expectedType.isInstance(value)) {
            throw new PicoIntrospectionException(expectedType.getClass().getName()
                    + " is not assignable from "
                    + value.getClass().getName());
        }
    }

    private boolean checkPrimitive(Class expectedType) {
        try {
            if (expectedType.isPrimitive()) {
                final Field field = value.getClass().getField("TYPE");
                final Class type = (Class) field.get(value);
                return expectedType.isAssignableFrom(type);
            }
        } catch (NoSuchFieldException e) {
        } catch (IllegalAccessException e) {
        }
        return false;
    }

}
