// "Replace lambda with method reference" "false"

class Test {
  {
    Test[] t = new Test[1];
    Runnable r = () -> t[0].to<caret>String();
  }
}