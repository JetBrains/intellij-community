/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer;

/**
 * Interface realizing a visitor pattern for {@link PicoContainer} as described in the GoF.
 * The visitor should visit the container, its children, all registered {@link ComponentAdapter}
 * instances and all instantiated components.
 *
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @version $Revision: 1753 $
 * @since 1.1
 */
public interface PicoVisitor {
    /**
     * Entry point for the PicoVisitor traversal. The given node is the first object, that is
     * asked for acceptance. Only objects of type {@link PicoContainer}, {@link ComponentAdapter},
     * or {@link Parameter} are valid.
     *
     * @param node the start node of the traversal.
     * @return a visitor-specific value.
     * @throws IllegalArgumentException in case of an argument of invalid type.
     * @since 1.1
     */
    Object traverse(Object node);

    /**
     * Visit a {@link PicoContainer} that has to accept the visitor.
     *
     * @param pico the visited container.
     * @since 1.1
     */

    void visitContainer(PicoContainer pico);
    /**
     * Visit a {@link ComponentAdapter} that has to accept the visitor.
     *
     * @param componentAdapter the visited ComponentAdapter.
     * @since 1.1
     */

    void visitComponentAdapter(ComponentAdapter componentAdapter);
    /**
     * Visit a {@link Parameter} that has to accept the visitor.
     *
     * @param parameter the visited Parameter.
     * @since 1.1
     */
    void visitParameter(Parameter parameter);
}
