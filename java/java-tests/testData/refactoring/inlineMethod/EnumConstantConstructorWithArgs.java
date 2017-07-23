public class TestInlining {

  static String inlineMe(String arg) {
    return arg.trim();
  }

  public static final String FP = inlineMe("fa");

  enum MyEnum {
    A(inlin<caret>eMe("foo"));

    MyEnum(String s) {
    }
  }

}