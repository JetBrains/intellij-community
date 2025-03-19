package com.siyeh.igfixes.javadoc.html_tag_can_be_javadoc_tag;

class Braces {

  /**
   * <co<caret>de>if (something) { this.doSomething(); }</code>
   */
  void foo() {}

}