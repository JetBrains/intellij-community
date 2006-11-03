package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class Change {
  public static Change read(DataInputStream s) throws IOException {
    return createChange(s.readUTF(), s);
  }

  private static Change createChange(String className, DataInputStream s)
      throws IOException {
    // todo move it to MyStream class
    try {
      Class clazz = Class.forName(className);
      Constructor constructor = clazz.getConstructor(DataInputStream.class);

      return (Change)constructor.newInstance(s);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  public void write(DataOutputStream s) throws IOException {
    s.writeUTF(getClass().getName());
  }

  public abstract void applyTo(Snapshot snapshot);

  public abstract void revertOn(Snapshot snapshot);
}
