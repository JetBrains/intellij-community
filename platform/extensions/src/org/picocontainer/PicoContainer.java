/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer;

import org.picocontainer.defaults.DefaultPicoContainer;

import java.util.Collection;
import java.util.List;


/**
 * This is the core interface for PicoContainer. It is used to retrieve component instances from the container; it only
 * has accessor methods (in addition to the {@link #accept(PicoVisitor)} method). In order to register components in a
 * PicoContainer, use a {@link MutablePicoContainer}, such as {@link DefaultPicoContainer}.
 *
 * @author Paul Hammant
 * @author Aslak Helles&oslash;y
 * @author Jon Tirs&eacute;n
 * @version $Revision: 2285 $
 * @see <a href="package-summary.html#package_description">See package description for basic overview how to use
 *      PicoContainer.</a>
 * @since 1.0
 */
public interface PicoContainer extends Disposable {

    /**
     * Retrieve a component instance registered with a specific key. If a component cannot be found in this container,
     * the parent container (if one exists) will be searched.
     *
     * @param componentKey the key that the component was registered with.
     * @return an instantiated component, or <code>null</code> if no component has been registered for the specified
     *         key.
     */
    Object getComponentInstance(Object componentKey);

    /**
     * Find a component instance matching the specified type.
     *
     * @param componentType the type of the component
     * @return an instantiated component matching the class, or <code>null</code> if no component has been registered
     *         with a matching type
     * @throws PicoException if the instantiation of the component fails
     */
    Object getComponentInstanceOfType(Class componentType);

    /**
     * Retrieve all the registered component instances in the container, (not including those in the parent container).
     * The components are returned in their order of instantiation, which depends on the dependency order between them.
     *
     * @return all the components.
     * @throws PicoException if the instantiation of the component fails
     */
    List getComponentInstances();

    /**
     * Retrieve the parent container of this container.
     *
     * @return a {@link PicoContainer} instance, or <code>null</code> if this container does not have a parent.
     */
    PicoContainer getParent();

    /**
     * Find a component adapter associated with the specified key. If a component adapter cannot be found in this
     * container, the parent container (if one exists) will be searched.
     *
     * @param componentKey the key that the component was registered with.
     * @return the component adapter associated with this key, or <code>null</code> if no component has been
     *         registered for the specified key.
     */
    ComponentAdapter getComponentAdapter(Object componentKey);

    /**
     * Find a component adapter associated with the specified type. If a component adapter cannot be found in this
     * container, the parent container (if one exists) will be searched.
     *
     * @param componentType the type of the component.
     * @return the component adapter associated with this class, or <code>null</code> if no component has been
     *         registered for the specified key.
     */
    ComponentAdapter getComponentAdapterOfType(Class componentType);

    /**
     * Retrieve all the component adapters inside this container. The component adapters from the parent container are
     * not returned.
     *
     * @return a collection containing all the {@link ComponentAdapter}s inside this container. The collection will not
     *         be modifiable.
     * @see #getComponentAdaptersOfType(Class) a variant of this method which returns the component adapters inside this
     *      container that are associated with the specified type.
     */
    Collection getComponentAdapters();

    /**
     * Retrieve all component adapters inside this container that are associated with the specified type. The component
     * adapters from the parent container are not returned.
     *
     * @param componentType the type of the components.
     * @return a collection containing all the {@link ComponentAdapter}s inside this container that are associated with
     *         the specified type. Changes to this collection will not be reflected in the container itself.
     */
    List getComponentAdaptersOfType(Class componentType);

    /**
     * Verify that the dependencies for all the registered components can be satisfied. No components are instantiated
     * during the verification process.
     *
     * @throws PicoVerificationException if there are unsatisifiable dependencies.
     * @deprecated since 1.1 - Use "new VerifyingVisitor().traverse(this)"
     */
    void verify() throws PicoVerificationException;

    /**
     * Returns a List of components of a certain componentType. The list is ordered by instantiation order, starting
     * with the components instantiated first at the beginning.
     *
     * @param componentType the searched type.
     * @return a List of components.
     * @throws PicoException if the instantiation of a component fails
     * @since 1.1
     */
    List getComponentInstancesOfType(Class componentType);

    /**
     * Accepts a visitor that should visit the child containers, component adapters and component instances.
     *
     * @param visitor the visitor
     * @since 1.1
     */
    void accept(PicoVisitor visitor);
}
