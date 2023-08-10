package com.siyeh.igtest.serialization;

import java.io.ObjectOutputStream;
import java.io.IOException;

public class NonSerializableObjectPassedToObjectStream<E> {

  E item;

  public void foo(Object o) throws IOException {
    final ObjectOutputStream stream = new ObjectOutputStream(null);
    stream.writeObject(new Integer(3));
    stream.writeObject(<warning descr="Non-serializable object passed to ObjectOutputStream">new NonSerializableObjectPassedToObjectStream()</warning>);
    stream.writeObject(o);
    stream.writeObject(<warning descr="Non-serializable object passed to ObjectOutputStream">new NonSerializableObjectPassedToObjectStream[] {new NonSerializableObjectPassedToObjectStream()}</warning>);
  }

  private void write(final java.io.ObjectOutputStream s)
    throws java.io.IOException {
    s.writeObject(item);
  }
}
