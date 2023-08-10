package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

class MyCloseable implements AutoCloseable {
  private void f() throws IOException {
    Properties versionMapping = new Properties();
    InputStream in = getClass().getClassLoader().getResourceAsStream("name");
    if (in != null) {
      tr<caret>y {
        versionMapping.load(in);
      } finally {
        in.close();
      }
    }
  }
}