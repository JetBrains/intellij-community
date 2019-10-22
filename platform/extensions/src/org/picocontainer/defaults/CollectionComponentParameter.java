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

import org.picocontainer.*;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;


/**
 * A CollectionComponentParameter should be used to support inject an {@link Array}, a
 * {@link Collection}or {@link Map}of components automatically. The collection will contain
 * all components of a special type and additionally the type of the key may be specified. In
 * case of a map, the map's keys are the one of the component adapter.
 *
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @since 1.1
 */
public class CollectionComponentParameter
        implements Parameter, Serializable {
    private static final MapFactory mapFactory = new MapFactory();

    /**
     * Use <code>ARRAY</code> as {@link Parameter}for an Array that must have elements.
     */
    public static final CollectionComponentParameter ARRAY = new CollectionComponentParameter();
    /**
     * Use <code>ARRAY_ALLOW_EMPTY</code> as {@link Parameter}for an Array that may have no
     * elements.
     */
    public static final CollectionComponentParameter ARRAY_ALLOW_EMPTY = new CollectionComponentParameter(true);

    private final boolean emptyCollection;
    private final Class componentKeyType;
    private final Class componentValueType;

    /**
     * Expect an {@link Array}of an appropriate type as parameter. At least one component of
     * the array's component type must exist.
     */
    public CollectionComponentParameter() {
        this(false);
    }

    /**
     * Expect an {@link Array}of an appropriate type as parameter.
     *
     * @param emptyCollection <code>true</code> if an empty array also is a valid dependency
     *                   resolution.
     */
    public CollectionComponentParameter(boolean emptyCollection) {
        this(Void.TYPE, emptyCollection);
    }

    /**
     * Expect any of the collection types {@link Array},{@link Collection}or {@link Map}as
     * parameter.
     *
     * @param componentValueType the type of the components (ignored in case of an Array)
     * @param emptyCollection <code>true</code> if an empty collection resolves the
     *                   dependency.
     */
    public CollectionComponentParameter(Class componentValueType, boolean emptyCollection) {
        this(Object.class, componentValueType, emptyCollection);
    }

    /**
     * Expect any of the collection types {@link Array},{@link Collection}or {@link Map}as
     * parameter.
     *
     * @param componentKeyType the type of the component's key
     * @param componentValueType the type of the components (ignored in case of an Array)
     * @param emptyCollection <code>true</code> if an empty collection resolves the
     *                   dependency.
     */
    public CollectionComponentParameter(Class componentKeyType, Class componentValueType, boolean emptyCollection) {
        this.emptyCollection = emptyCollection;
        this.componentKeyType = componentKeyType;
        this.componentValueType = componentValueType;
    }

    /**
     * Resolve the parameter for the expected type. The method will return <code>null</code>
     * If the expected type is not one of the collection types {@link Array},
     * {@link Collection}or {@link Map}. An empty collection is only a valid resolution, if
     * the <code>emptyCollection</code> flag was set.
     *
     * @param container {@inheritDoc}
     * @param adapter {@inheritDoc}
     * @param expectedType {@inheritDoc}
     * @return the instance of the collection type or <code>null</code>
     * @throws PicoInitializationException {@inheritDoc}
     */
    @Override
    public Object resolveInstance(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
        // type check is done in isResolvable
        Object result = null;
        final Class collectionType = getCollectionType(expectedType);
        if (collectionType != null) {
            final Map adapterMap = getMatchingComponentAdapters(container, adapter, componentKeyType, getValueType(expectedType));
            if (Array.class.isAssignableFrom(collectionType)) {
                result = getArrayInstance(container, expectedType, adapterMap);
            } else if (Map.class.isAssignableFrom(collectionType)) {
                result = getMapInstance(container, expectedType, adapterMap);
            } else if (Collection.class.isAssignableFrom(collectionType)) {
                result = getCollectionInstance(container, expectedType, adapterMap);
            } else {
                throw new PicoIntrospectionException(expectedType.getName() + " is not a collective type");
            }
        }
        return result;
    }

    /**
     * Check for a successful dependency resolution of the parameter for the expected type. The
     * dependency can only be satisfied if the expected type is one of the collection types
     * {@link Array},{@link Collection}or {@link Map}. An empty collection is only a valid
     * resolution, if the <code>emptyCollection</code> flag was set.
     *
     * @param container {@inheritDoc}
     * @param adapter {@inheritDoc}
     * @param expectedType {@inheritDoc}
     * @return <code>true</code> if matching components were found or an empty collective type
     *               is allowed
     */
    @Override
    public boolean isResolvable(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
        final Class collectionType = getCollectionType(expectedType);
        final Class valueType = getValueType(expectedType);
        return collectionType != null && (emptyCollection || getMatchingComponentAdapters(container, adapter, componentKeyType, valueType).size() > 0);
    }

    /**
     * Verify a successful dependency resolution of the parameter for the expected type. The
     * method will only return if the expected type is one of the collection types {@link Array},
     * {@link Collection}or {@link Map}. An empty collection is only a valid resolution, if
     * the <code>emptyCollection</code> flag was set.
     *
     * @param container {@inheritDoc}
     * @param adapter {@inheritDoc}
     * @param expectedType {@inheritDoc}
     * @throws PicoIntrospectionException {@inheritDoc}
     */
    @Override
    public void verify(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
        final Class collectionType = getCollectionType(expectedType);
        if (collectionType != null) {
            final Class valueType = getValueType(expectedType);
            final Collection componentAdapters = getMatchingComponentAdapters(container, adapter, componentKeyType, valueType).values();
            if (componentAdapters.isEmpty()) {
                if (!emptyCollection) {
                    throw new PicoIntrospectionException(expectedType.getName()
                            + " not resolvable, no components of type "
                            + getValueType(expectedType).getName()
                            + " available");
                }
            } else {
                for (final Iterator iter = componentAdapters.iterator(); iter.hasNext();) {
                    final ComponentAdapter componentAdapter = (ComponentAdapter) iter.next();
                    componentAdapter.verify(container);
                }
            }
        } else {
            throw new PicoIntrospectionException(expectedType.getName() + " is not a collective type");
        }
    }

    /**
     * Visit the current {@link Parameter}.
     *
     * @see Parameter#accept(PicoVisitor)
     */
    @Override
    public void accept(final PicoVisitor visitor) {
        visitor.visitParameter(this);
    }

    /**
     * Evaluate whether the given component adapter will be part of the collective type.
     *
     * @param adapter a <code>ComponentAdapter</code> value
     * @return <code>true</code> if the adapter takes part
     */
    protected boolean evaluate(final ComponentAdapter adapter) {
        return adapter != null; // use parameter, prevent compiler warning
    }

    /**
     * Collect the matching ComponentAdapter instances.
     * @param container container to use for dependency resolution
     * @param adapter {@link ComponentAdapter} to exclude
     * @param keyType the compatible type of the key
     * @param valueType the compatible type of the component
     * @return a {@link Map} with the ComponentAdapter instances and their component keys as map key.
     */
    protected Map getMatchingComponentAdapters(PicoContainer container, ComponentAdapter adapter, Class keyType, Class valueType) {
        final Map adapterMap = mapFactory.newInstance();
        final PicoContainer parent = container.getParent();
        if (parent != null) {
            adapterMap.putAll(getMatchingComponentAdapters(parent, adapter, keyType, valueType));
        }
        final Collection allAdapters = container.getComponentAdapters();
        for (final Iterator iter = allAdapters.iterator(); iter.hasNext();) {
            final ComponentAdapter componentAdapter = (ComponentAdapter) iter.next();
            adapterMap.remove(componentAdapter.getComponentKey());
        }
        final List adapterList = container.getComponentAdaptersOfType(valueType);
        for (final Iterator iter = adapterList.iterator(); iter.hasNext();) {
            final ComponentAdapter componentAdapter = (ComponentAdapter) iter.next();
            final Object key = componentAdapter.getComponentKey();
            if (adapter != null && key.equals(adapter.getComponentKey())) {
                continue;
            }
            if (keyType.isAssignableFrom(key.getClass()) && evaluate(componentAdapter)) {
                adapterMap.put(key, componentAdapter);
            }
        }
        return adapterMap;
    }

    private static Class getCollectionType(final Class collectionType) {
        Class collectionClass = null;
        if (collectionType.isArray()) {
            collectionClass = Array.class;
        } else if (Map.class.isAssignableFrom(collectionType)) {
            collectionClass = Map.class;
        } else if (Collection.class.isAssignableFrom(collectionType)) {
            collectionClass = Collection.class;
        }
        return collectionClass;
    }

    private Class getValueType(final Class collectionType) {
        Class valueType = componentValueType;
        if (collectionType.isArray()) {
            valueType = collectionType.getComponentType();
        }
        return valueType;
    }

    private Object[] getArrayInstance(final PicoContainer container, final Class expectedType, final Map adapterList) {
        final Object[] result = (Object[]) Array.newInstance(expectedType.getComponentType(), adapterList.size());
        int i = 0;
        for (final Iterator iterator = adapterList.values().iterator(); iterator.hasNext();) {
            final ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
            result[i] = container.getComponentInstance(componentAdapter.getComponentKey());
            i++;
        }
        return result;
    }

    private static Collection getCollectionInstance(final PicoContainer container, final Class expectedType, final Map adapterList) {
        Class collectionType = expectedType;
        if (collectionType.isInterface()) {
            // The order of tests are significant. The least generic types last.
            if (List.class.isAssignableFrom(collectionType)) {
                collectionType = ArrayList.class;
//            } else if (BlockingQueue.class.isAssignableFrom(collectionType)) {
//                collectionType = ArrayBlockingQueue.class;
//            } else if (Queue.class.isAssignableFrom(collectionType)) {
//                collectionType = LinkedList.class;
            } else if (SortedSet.class.isAssignableFrom(collectionType)) {
                collectionType = TreeSet.class;
            } else if (Set.class.isAssignableFrom(collectionType)) {
                collectionType = HashSet.class;
            } else if (Collection.class.isAssignableFrom(collectionType)) {
                collectionType = ArrayList.class;
            }
        }
        try {
            Collection result = (Collection) collectionType.newInstance();
            for (final Iterator iterator = adapterList.values().iterator(); iterator.hasNext();) {
                final ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
                result.add(container.getComponentInstance(componentAdapter.getComponentKey()));
            }
            return result;
        } catch (InstantiationException e) {
            ///CLOVER:OFF
            throw new PicoInitializationException(e);
            ///CLOVER:ON
        } catch (IllegalAccessException e) {
            ///CLOVER:OFF
            throw new PicoInitializationException(e);
            ///CLOVER:ON
        }
    }

    private static Map getMapInstance(final PicoContainer container, final Class expectedType, final Map adapterList) {
        Class collectionType = expectedType;
        if (collectionType.isInterface()) {
            // The order of tests are significant. The least generic types last.
            if (SortedMap.class.isAssignableFrom(collectionType)) {
                collectionType = TreeMap.class;
//            } else if (ConcurrentMap.class.isAssignableFrom(collectionType)) {
//                collectionType = ConcurrentHashMap.class;
            } else if (Map.class.isAssignableFrom(collectionType)) {
                collectionType = HashMap.class;
            }
        }
        try {
            Map result = (Map) collectionType.newInstance();
            for (final Iterator iterator = adapterList.entrySet().iterator(); iterator.hasNext();) {
                final Map.Entry entry = (Map.Entry) iterator.next();
                final Object key = entry.getKey();
                result.put(key, container.getComponentInstance(key));
            }
            return result;
        } catch (InstantiationException e) {
            ///CLOVER:OFF
            throw new PicoInitializationException(e);
            ///CLOVER:ON
        } catch (IllegalAccessException e) {
            ///CLOVER:OFF
            throw new PicoInitializationException(e);
            ///CLOVER:ON
        }
    }

}
