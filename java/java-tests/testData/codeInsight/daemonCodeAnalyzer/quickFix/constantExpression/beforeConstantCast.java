// "Fix all 'Constant expression can be evaluated' problems in file" "false"

class ConstantCast {
  static void doSomething(String param1, byte param2) {
  }

  public static void main(String[] args) {
    doSomething(args[0], (byte)<caret> 0);
  }
}