/*
 * @author max
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class PropertyKeyIndex extends StringStubIndexExtension<Property> {
  public static final StubIndexKey<String, Property> KEY = StubIndexKey.createIndexKey("properties.index");

  private static final PropertyKeyIndex ourInstance = new PropertyKeyIndex();

  public static PropertyKeyIndex getInstance() {
    return ourInstance;
  }

  public StubIndexKey<String, Property> getKey() {
    return KEY;
  }
}