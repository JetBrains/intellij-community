package dfa;

public class BoxedFloatLiterals {

  int examineFloat(Float f) {
    final Float PInf = Float.POSITIVE_INFINITY;
    final Float NInf = Float.NEGATIVE_INFINITY;
    final Float NaN = Float.NaN;
    return switch (f) {
      case <error descr="Constant expression required">NInf</error> -> -100;
      case -0.0f -> -1;
      case 0.0f -> +1;
      case <error descr="Constant expression required">PInf</error> -> +100;
      case <error descr="Constant expression required">NaN</error> -> 0;
      default -> 42;
    };
  }

  int examineFloat2(Float f) {
    final float PInf = Float.POSITIVE_INFINITY;
    final float NInf = Float.NEGATIVE_INFINITY;
    final float NaN = Float.NaN;
    return switch (f) {
      case NInf -> -100;
      case -0.0f -> -1;
      case 0.0f -> +1;
      case PInf -> +100;
      case NaN -> 0;
      default -> 42;
    };
  }
}
