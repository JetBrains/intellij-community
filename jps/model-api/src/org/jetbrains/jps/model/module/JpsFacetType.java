package org.jetbrains.jps.model.module;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementType;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

/**
 * @author nik
 */
public abstract class JpsFacetType<P extends JpsElementProperties> extends JpsElementType<P> implements JpsElementTypeWithDefaultProperties<P> {
}
