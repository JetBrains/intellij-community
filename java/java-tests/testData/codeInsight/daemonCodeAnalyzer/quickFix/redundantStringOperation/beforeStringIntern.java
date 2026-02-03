// "Remove redundant 'intern()' call" "true-preview"
class Foo {
  private static final String x = ("Hello "+"World"+'!').inte<caret>rn();
}