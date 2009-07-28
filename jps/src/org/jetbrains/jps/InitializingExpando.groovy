package org.jetbrains.jps

/**
 * @author max
 */
class InitializingExpando extends Expando {
  def Object getProperty(String property) {
    def result = super.getProperty(property)
    if (result == null) throw new MissingPropertyException(property, getClass())
    return result;
  }
}
