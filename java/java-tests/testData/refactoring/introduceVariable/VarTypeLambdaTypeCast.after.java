interface I {
  String get();
}
class MyTest {
  {
      var temp = (I) () -> "abc";
      var stringSupplier = temp;
  }
}