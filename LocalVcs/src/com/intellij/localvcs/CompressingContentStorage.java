package com.intellij.localvcs;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class CompressingContentStorage implements IContentStorage {
  private IContentStorage mySubject;

  public CompressingContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public void close() {
    mySubject.close();
  }

  public void save() {
    mySubject.save();
  }

  public int store(byte[] content) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    OutputStream s = createDeflaterOutputStream(output);
    s.write(content);
    s.close();

    return mySubject.store(output.toByteArray());
  }

  public byte[] load(int id) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    InputStream s = createInflaterOutputStream(mySubject.load(id));
    FileUtil.copy(s, output);
    s.close();

    return output.toByteArray();
  }

  protected OutputStream createDeflaterOutputStream(OutputStream output) {
    return new DeflaterOutputStream(output);
  }

  protected InputStream createInflaterOutputStream(byte[] content) {
    return new InflaterInputStream(new ByteArrayInputStream(content));
  }

  public void remove(int id) {
    mySubject.remove(id);
  }

  public boolean isRemoved(int id) {
    return mySubject.isRemoved(id);
  }
}
