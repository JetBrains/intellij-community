public class X
{
  void doSomething(int x, String... args) { /* ... */ }
  
  void use() {
    doSomething(0, "one", "two");
  }
}