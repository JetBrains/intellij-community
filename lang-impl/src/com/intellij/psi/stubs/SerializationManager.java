/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;

public class SerializationManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManager");

  private static final SerializationManager INSTANCE = new SerializationManager();

  public static SerializationManager getInstance() {
    return INSTANCE;
  }

  public StubSerializer getSerializer(Class<? extends StubElement> stubClass) {
    final SerializerClass annotation = stubClass.getAnnotation(SerializerClass.class);
    if (annotation != null) {
      try {
        final Class<? extends StubSerializer> serClass = annotation.value();
        return serClass.getConstructor().newInstance();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    return new DefaultStubSerializer();
  }
}