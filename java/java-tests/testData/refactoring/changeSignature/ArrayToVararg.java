public class X
{
  void doSo<caret>mething(int x, String[] args) { /* ... */ }
  
  void use() {
    doSomething(0, new String[]{"one", "two"});
  }
}