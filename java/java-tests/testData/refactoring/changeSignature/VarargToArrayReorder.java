public class X
{
  void doSome<caret>thing(int x, String... args) { /* ... */ }
  
  void use() {
    doSomething(0, "foo", "bar");
    doSomething(0, new String[]{"one", "two"});
  }
}