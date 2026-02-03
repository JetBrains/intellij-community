package com.siyeh.igfixes.javadoc.html_tag_can_be_javadoc_tag;

class Second {

  /**
   * <code></code><c<caret>ode></code>
   */
  void foo2() {}
}