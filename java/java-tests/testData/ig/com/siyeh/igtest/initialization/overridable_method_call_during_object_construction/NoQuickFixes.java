public abstract class NoQuickFixes {

  NoQuickFixes() {
    <warning descr="Call to overridable method 'foo()' during object construction"><caret>foo</warning>();
  }

  public abstract void foo();
}