// "Join declaration and assignment" "true"
class Test {
  {
    int i = 4;
    i *<caret>= 2 + 3;
  }
}