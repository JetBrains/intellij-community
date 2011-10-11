package org.jetbrains.jps;

import groovy.lang.MissingPropertyException;
import groovy.util.Expando;

/**
 * @author max
 */
class InitializingExpando extends Expando {
  public Object getProperty(String property) {
    Object result = super.getProperty(property);
    if (result == null) throw new MissingPropertyException(property, getClass());
    return result;
  }
}
