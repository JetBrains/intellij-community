package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;

class Comments {

  void m(OutputStream out) throws IOException {
    InputStream in = new FileInputStream("filename");
    try<caret> {
    } finally {
      // stop
      // now
      in.close();
      out.close();
    }
  }
}