// "Remove redundant 'intern()' call" "true"
class Foo {
  private static final String x = ("Hello "+"World"+'!');
}