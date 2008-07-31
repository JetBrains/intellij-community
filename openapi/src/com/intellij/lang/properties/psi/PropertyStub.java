/*
 * @author max
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.stubs.StubElement;

public interface PropertyStub extends StubElement<Property> {
  String getKey();
}