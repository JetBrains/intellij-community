/*
 * @author max
 */
package com.intellij.psi.stubs;

public class SerializationManager {
  private static SerializationManager INSTANCE = new SerializationManager();

  public static SerializationManager getInstance() {
    return INSTANCE;
  }

  public StubSerializer getSerializer(Class<? extends StubElement> stubClass) {
    return new DefaultStubSerializer();
  }
}