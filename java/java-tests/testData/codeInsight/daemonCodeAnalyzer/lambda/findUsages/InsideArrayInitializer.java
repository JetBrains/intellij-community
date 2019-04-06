class C {
  {
    Foo[] array = {() -> {}, () -> {}, null};
    Foo[][] array2 = {{() -> {}}};
    Bar[] array3 = {() -> {}};
    Bar[] array4 = new Bar[] {() -> {}};
  }
}