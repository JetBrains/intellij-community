package foo;
class Outer<T> {
  class Inner1 {}
  class Bar extends Outer<String>.Inner1 {}
}