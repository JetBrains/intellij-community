package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;


class TwoResources {
  void aVoid(byte[] bytes) throws IOException, ClassNotFoundException {
    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    ObjectInput in = null;
    try<caret> {
      in = new ObjectInputStream(bis);
      System.out.println(in.readObject());
    } finally {
      in.close();
      bis.close();
    }
  }
}