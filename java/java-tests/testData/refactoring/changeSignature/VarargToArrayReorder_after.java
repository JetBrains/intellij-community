public class X
{
  void doSomething(String[] args, int x) { /* ... */ }
  
  void use() {
    doSomething(new String[]{"foo", "bar"}, 0);
    doSomething(new String[]{"one", "two"}, 0);
  }
}