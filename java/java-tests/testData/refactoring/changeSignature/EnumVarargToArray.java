public enum X
{
  A(0, "foo", "bar"),
  B(0, new String[]{"one", "two"});
  
  <caret>X(int x, String... args) { /* ... */ }
}