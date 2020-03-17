/*****************************************************************************
 * Copyright (c) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Joerg Schaible                                           *
 *****************************************************************************/

package org.picocontainer.defaults;

/**
 * Interface for a guard implementation looking after cyclic dependencies.
 * 
 * @author J&ouml;rg Schaible
 * @since 1.1
 */
public interface CyclicDependencyGuard {

    /**
     * Derive from this class and implement this function with the functionality 
     * to observe for a dependency cycle.
     * 
     * @return a value, if the functionality result in an expression, 
     *      otherwise just return <code>null</code>
     */
    public Object run();
    
    /**
     * Call the observing function. The provided guard will hold the {@link Boolean} value.
     * If the guard is already <code>Boolean.TRUE</code> a {@link CyclicDependencyException} 
     * will be  thrown.
     * 
     * @param stackFrame the current stack frame
     * @return the result of the <code>run</code> method
     */
    public Object observe(Class stackFrame);
}