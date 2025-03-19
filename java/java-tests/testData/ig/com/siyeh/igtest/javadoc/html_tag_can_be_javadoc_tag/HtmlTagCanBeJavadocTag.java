package com.siyeh.igtest.javadoc.html_tag_can_be_javadoc_tag;

class HtmlTagCanBeJavadocTag {

  /**
   * <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><caret><code></warning>if (something) { this.doSomething(); }</code>
   * <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>
   *     asdf
   * </code>
   * <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning></code><warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning></code>
   */
  void foo() {}

  /**
   * <warning descr="'<CODE>...</code>' can be replaced with '{@code …}'"><CODE></warning>HEAVY CODE</CODE>
   * <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>1</code> + <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>2</code>+<warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>3</code> = <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>6</code>
   *    <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>a</code> + <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>b</code>+<warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>c</code> = <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>abc</code>
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
   * <warning descr="'<code>...</code>' can be replaced with '{@code …}'"><code></warning>{}</code>
   * <code>}{</code>
   */
  private String indubitably = null;
}