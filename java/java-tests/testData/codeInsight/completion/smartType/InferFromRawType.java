class A<T extends String> {
  {
    A a = new A<<caret>>();
  }
}