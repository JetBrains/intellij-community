package com.example.sqlinjection;


import java.io.IOException;
import java.io.PrintWriter;

final class Complete {
  private void simpleView2(java.util.List<String> message6, final HttpServletResponse resp) throws IOException {
    PrintWriter writer = resp.getWriter();
    PrintWriter out = writer;
    try {
      out.print(<warning descr="Unknown string is used as safe parameter">message6</warning>);
    } catch (Exception e) {

    }
  }

  private static class HttpServletResponse {
    public PrintWriter getWriter() {
      return null;
    }
  }
}