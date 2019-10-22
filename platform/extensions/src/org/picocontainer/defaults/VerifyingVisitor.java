/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Visitor to verify {@link PicoContainer} instances. The visitor walks down the logical container hierarchy.
 *
 * @author J&ouml;rg Schaible
 * @since 1.1
 */
public class VerifyingVisitor extends TraversalCheckingVisitor {

    private final List nestedVerificationExceptions;
    private final Set verifiedComponentAdapters;
    private final PicoVisitor componentAdapterCollector;
    private PicoContainer currentPico;

    /**
     * Construct a VerifyingVisitor.
     */
    public VerifyingVisitor() {
        nestedVerificationExceptions = new ArrayList();
        verifiedComponentAdapters = new HashSet();
        componentAdapterCollector = new ComponentAdapterCollector();
    }

    /**
     * Traverse through all components of the {@link PicoContainer} hierarchy and verify the components.
     *
     * @throws PicoVerificationException if some components could not be verified.
     * @see PicoVisitor#traverse(Object)
     */
    @Override
    public Object traverse(Object node) throws PicoVerificationException {
        nestedVerificationExceptions.clear();
        verifiedComponentAdapters.clear();
        try {
            super.traverse(node);
            if (!nestedVerificationExceptions.isEmpty()) {
                throw new PicoVerificationException(new ArrayList(nestedVerificationExceptions));
            }
        } finally {
            nestedVerificationExceptions.clear();
            verifiedComponentAdapters.clear();
        }
        return Void.TYPE;
    }

    @Override
    public void visitContainer(PicoContainer pico) {
        super.visitContainer(pico);
        currentPico = pico;
    }

    @Override
    public void visitComponentAdapter(ComponentAdapter componentAdapter) {
        super.visitComponentAdapter(componentAdapter);
        if (!verifiedComponentAdapters.contains(componentAdapter)) {
            try {
                componentAdapter.verify(currentPico);
            } catch (RuntimeException e) {
                nestedVerificationExceptions.add(e);
            }
            componentAdapter.accept(componentAdapterCollector);
        }
    }

    private class ComponentAdapterCollector implements PicoVisitor {
        // /CLOVER:OFF
        @Override
        public Object traverse(Object node) {
            return null;
        }

        @Override
        public void visitContainer(PicoContainer pico) {
        }

        // /CLOVER:ON

        @Override
        public void visitComponentAdapter(ComponentAdapter componentAdapter) {
            verifiedComponentAdapters.add(componentAdapter);
        }

        @Override
        public void visitParameter(Parameter parameter) {
        }
    }
}
