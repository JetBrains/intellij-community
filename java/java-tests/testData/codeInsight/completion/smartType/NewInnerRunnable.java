interface Runnable {}

class MyClass {
  static class Foo implements Runnable {}
}

class Goo implements Runnable {}

class XXX {
  {
    Runnable r = new MyClass.<caret>
  }
}