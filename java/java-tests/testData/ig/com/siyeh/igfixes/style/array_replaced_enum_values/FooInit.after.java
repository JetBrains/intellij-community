class Foo {
  enum En {A;}
  static En foo() {
    return En.A;
  }

  {
    En[] array = En.values();
  }
}