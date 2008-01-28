package com.intellij.openapi.roots.watcher.impl;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Condition;
import org.jdom.Element;

/**
 * @author dsl
 */
public interface OrderEntryPredicate extends Condition<OrderEntry>, Cloneable {
  void writeToElement(Element element);
  Object clone() throws CloneNotSupportedException;
}
