package com.siyeh.igtest.performance.string_concatenation_inside_string_buffer_append;

public class StringConcatenationInsideStringBufferAppend {

  private String s;

  void foo(StringBuffer buffer) {
    buffer.<warning descr="String concatenation as argument to 'StringBuffer.append()' call">append</warning>("asdf" + s + "asdf");
    buffer.<warning descr="String concatenation as argument to 'StringBuffer.append()' call">append</warning>("asdf" + s);
    buffer.append("asdf" + "asdf");
  }

  void bar(StringBuilder builder) {
    builder.<warning descr="String concatenation as argument to 'StringBuilder.append()' call">append</warning>("asdf" + s + "asdf");
    builder.<warning descr="String concatenation as argument to 'StringBuilder.append()' call">append</warning>("asdf" + s);
    builder.append("asdf" + "asdf");
  }

  /*
  // java.lang.Appendable not in mock jdk
  void appendable(Appendable appendable) throws IOException {
    appendable.append("asdf" + s);
    appendable.append((s + "asdf"));
  }
  */
}
