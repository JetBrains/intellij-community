package com.intellij.openapi.roots.impl;

import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 *  @author dsl
 */
interface WritableOrderEntry {
  void writeExternal(Element rootElement) throws WriteExternalException;
}
