// "Remove redundant 'intern()' call" "false"
class Foo {
  private static final String x = ("Hello "+"World".trim()+'!').inte<caret>rn();
}