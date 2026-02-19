import java.lang.Override

class MarkdownInheritDoc {

  /** I am legacy javadoc, I hope no one disturbs my _underlines_ and **astrerisks** */
  void foo() {}
}

class MarkdownInheritedDoc extends MarkdownInheritDoc {

  /// Markdown variant
  /// {@inheritDoc}
  ///
  /// Single line markdown
  @Override
  void foo() {
    super.foo();
  }
}