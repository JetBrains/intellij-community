package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Stream {
  private DataInputStream myIs;
  private DataOutputStream myOs;

  public Stream(InputStream is) {
    myIs = new DataInputStream(is);
  }

  public Stream(OutputStream os) {
    myOs = new DataOutputStream(os);
  }

  public void flush() throws IOException {
    myOs.flush();
  }

  public Path readPath() throws IOException {
    return new Path(this);
  }

  public void writePath(Path p) throws IOException {
    p.write(this);
  }

  public Entry readEntry() throws IOException {
    return (Entry)readSubclass(readString());
  }

  public void writeEntry(Entry e) throws IOException {
    writeString(e.getClass().getName());
    e.write(this);
  }

  public Entry readNullableEntry() throws IOException {
    if (!readBoolean()) return null;
    return readEntry();
  }

  public void writeNullableEntry(Entry e) throws IOException {
    writeBoolean(e != null);
    if (e != null) writeEntry(e);
  }

  // todo get rid of these two methods
  public Entry readRootEntry() throws IOException {
    return new RootEntry(this);
  }

  public void writeRootEntry(Entry e) throws IOException {
    e.write(this);
  }

  public Change readChange() throws IOException {
    return (Change)readSubclass(readString());
  }

  public void writeChange(Change c) throws IOException {
    writeString(c.getClass().getName());
    c.write(this);
  }

  public String readString() throws IOException {
    return myIs.readUTF();
  }

  public void writeString(String s) throws IOException {
    myOs.writeUTF(s);
  }

  public String readNullableString() throws IOException {
    if (!readBoolean()) return null;
    return readString();
  }

  public void writeNullableString(String s) throws IOException {
    writeBoolean(s != null);
    if (s != null) writeString(s);
  }

  public Boolean readBoolean() throws IOException {
    return myIs.readBoolean();
  }

  public void writeBoolean(Boolean b) throws IOException {
    myOs.writeBoolean(b);
  }

  public Integer readInteger() throws IOException {
    return myIs.readInt();
  }

  public void writeInteger(Integer i) throws IOException {
    myOs.writeInt(i);
  }

  private Object readSubclass(String className) throws IOException {
    try {
      Class clazz = Class.forName(className);
      Constructor constructor = clazz.getConstructor(getClass());
      return constructor.newInstance(this);
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
}
