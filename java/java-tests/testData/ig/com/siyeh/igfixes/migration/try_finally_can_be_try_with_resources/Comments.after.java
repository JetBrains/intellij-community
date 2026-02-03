package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;

class Comments {

  void m(OutputStream out) throws IOException {
      try (out; InputStream in = new FileInputStream("filename")) {
      }
      // stop
      // now
  }
}