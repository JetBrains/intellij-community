package foo;
class Outer<T> {
  class Inner {}
  class Bar extends Outer<String>.Inner {}
}