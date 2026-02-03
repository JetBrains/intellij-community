package com.siyeh.igtest.javadoc.html_tag_can_be_javadoc_tag;

class HtmlTagCanBeJavadocTag {

  /**
   * {@code if (something) { this.doSomething(); }}
   * {@code
   *     asdf
   * }
   * {@code}{@code}
   */
  void foo() {}

  /**
   * {@code HEAVY CODE}
   * {@code 1} + {@code 2}+{@code 3} = {@code 6}
   *    {@code a} + {@code b}+{@code c} = {@code abc}
   */
  void bar() {}

  /**
   * <code>if (foo) {<br>  System.out.println();<br>}</code>
   */
  void extremeFormatting() {}

  /**
   * Demo value. Use <code>{</code> or <code>}</code>.
   */
  public int x;

  /**
   * Another demo value.
   *
   * <code>x2 = {@link #x x} * 2</code>
   */
  public int x2 = x*2;

  /**
   * {@code {}}
   * <code>}{</code>
   */
  private String indubitably = null;
}