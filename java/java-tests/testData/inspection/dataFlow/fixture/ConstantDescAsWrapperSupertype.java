import java.lang.constant.ConstantDesc;

public class ConstantDescAsWrapperSupertype {
  public static void test(ConstantDesc value) {
    switch (<error descr="Inconvertible types; cannot cast 'java.lang.constant.ConstantDesc' to 'int'">(int) value</error>) {
      case -1:
        break;
      case 0:
        break;
      default:
        throw new IllegalArgumentException("Invalid value: " + value);
    }
  }
}
