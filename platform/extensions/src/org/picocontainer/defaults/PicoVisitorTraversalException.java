/*
 * Copyright (C) 2004 Joerg Schaible
 * Created on 06.10.2004 by joehni
 */
package org.picocontainer.defaults;

import org.picocontainer.PicoException;
import org.picocontainer.PicoVisitor;


/**
 * Exception for a PicoVisitor, that is dependent on a defined starting point of the traversal.
 * If the traversal is not initiated with a call of {@link PicoVisitor#traverse}
 * 
 * @author joehni
 * @since 1.1
 */
public class PicoVisitorTraversalException
        extends PicoException {

    /**
     * Construct the PicoVisitorTraversalException.
     * 
     * @param visitor The visitor casing the exception.
     */
    public PicoVisitorTraversalException(PicoVisitor visitor) {
        super("Traversal for PicoVisitor of type " + visitor.getClass().getName() + " must start with the visitor's traverse method");
    }
}
