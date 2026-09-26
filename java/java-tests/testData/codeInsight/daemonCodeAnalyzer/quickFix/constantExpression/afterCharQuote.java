// "Compute constant value of 'SINGLE_QUOTE'" "true-preview"

class ConstantCast {
  public static final char SINGLE_QUOTE = '\'';

  static void doSomething(Object o) {
  }

  public static void main(String[] args) {
    doSomething('\'');
  }
}