public class X
{
  void doSomething(int x, String[] args) { /* ... */ }
  
  void use() {
    doSomething(0, new String[]{"foo", "bar"});
    doSomething(0, new String[]{"one", "two"});
  }
}