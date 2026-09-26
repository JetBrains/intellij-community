import org.intellij.lang.annotations.MagicConstant;

class MagicIntMethodCall {
  public static final int ONE = 1;
  public static final int TWO = 2;

  void test3() {
    switch<caret> (x()) {
      case ONE:break;
    }
  }

  @MagicConstant(intValues = {ONE, TWO, -1})
  Integer x() {
    return ONE;
  }
}