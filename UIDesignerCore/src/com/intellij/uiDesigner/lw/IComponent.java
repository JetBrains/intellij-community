package com.intellij.uiDesigner.lw;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface IComponent {
  Object getClientProperty(Object key);

  void putClientProperty(Object key, Object value);

  /**
   * @return name of the field (in bound class). Returns <code>null</code>
   * if the component is not bound to any field.
   */
  String getBinding();

  String getComponentClassName();
}
