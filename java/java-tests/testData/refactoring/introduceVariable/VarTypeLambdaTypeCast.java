interface I {
  String get();
}
class MyTest {
  {
    var stringSupplier = <selection>(I) () -> "abc"</selection>;
  }
}