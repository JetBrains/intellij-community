// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class Test {
  {
    int a[];
    <caret>a = new int[0];
  }
}