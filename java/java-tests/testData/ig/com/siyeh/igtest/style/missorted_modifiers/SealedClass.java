class Foo {
  private <warning descr="Missorted modifiers 'sealed static'">sea<caret>led</warning> static class Bar {}
  private final class FooBar extends Bar {}
}