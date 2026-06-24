public class SwitchFloatRepresentation {

  static void main() {
    System.out.println(fNegInf());
    System.out.println(fNegZero());
    System.out.println(fPosZero());
    System.out.println(fPosInf());
    System.out.println(fNaN());
    System.out.println(fNaN2());
    System.out.println(f1());
    System.out.println(fOther());


    System.out.println(dNegInf());
    System.out.println(dNegZero());
    System.out.println(dPosZero());
    System.out.println(dPosInf());
    System.out.println(dNaN());

    System.out.println(fShort1(Float.NaN));
    System.out.println(fShort2(-0.0f));
  }

  static String fNegInf() {
    float f = Float.NEGATIVE_INFINITY;
    return switch (f) {
      case <warning descr="Switch label 'Float.NEGATIVE_INFINITY' is the only reachable in the whole switch">Float.NEGATIVE_INFINITY</warning> -> "neg inf";
      case -0.0f -> "neg zero";
      case 0.0f -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String fNegZero() {
    float f = -0.0f;
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case <warning descr="Switch label '-0.0f' is the only reachable in the whole switch">-0.0f</warning> -> "neg zero";
      case 0.0f -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String fPosZero() {
    float f = 0.0f;
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case -0.0f -> "neg zero";
      case <warning descr="Switch label '0.0f' is the only reachable in the whole switch">0.0f</warning> -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String fPosInf() {
    float f = Float.POSITIVE_INFINITY;
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case -0.0f -> "neg zero";
      case 0.0f -> "pos zero";
      case <warning descr="Switch label 'Float.POSITIVE_INFINITY' is the only reachable in the whole switch">Float.POSITIVE_INFINITY</warning> -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String fNaN() {
    float f = Float.NaN;
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case -0.0f -> "neg zero";
      case 0.0f -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case <warning descr="Switch label 'Float.NaN' is the only reachable in the whole switch">Float.NaN</warning> -> "nan";
      default -> "other";
    };
  }

  static String fNaN2() {
    float f = Float.intBitsToFloat(0x7fc00000);
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case -0.0f -> "neg zero";
      case 0.0f -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String f1() {
    float f = 1;
    return switch (f) {
      case Float.NEGATIVE_INFINITY -> "neg inf";
      case -0.0f -> "neg zero";
      case <warning descr="Switch label '1.0f' is the only reachable in the whole switch">1.0f</warning> -> "1";
      case 0.0f -> "pos zero";
      case Float.POSITIVE_INFINITY -> "pos inf";
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  static String fOther() {
    float f = 1;
    return switch (f) {
      case <warning descr="Switch label 'Float.NEGATIVE_INFINITY' is unreachable">Float.NEGATIVE_INFINITY</warning> -> "neg inf";
      case <warning descr="Switch label '-0.0f' is unreachable">-0.0f</warning> -> "neg zero";
      case <warning descr="Switch label '0.0f' is unreachable">0.0f</warning> -> "pos zero";
      case <warning descr="Switch label 'Float.POSITIVE_INFINITY' is unreachable">Float.POSITIVE_INFINITY</warning> -> "pos inf";
      case <warning descr="Switch label 'Float.NaN' is unreachable">Float.NaN</warning> -> "nan";
      default -> "other";
    };
  }


  static String dNegInf() {
    double d = Double.NEGATIVE_INFINITY;
    return switch (d) {
      case <warning descr="Switch label 'Double.NEGATIVE_INFINITY' is the only reachable in the whole switch">Double.NEGATIVE_INFINITY</warning> -> "neg inf";
      case -0.0 -> "neg zero";
      case 0.0 -> "pos zero";
      case Double.POSITIVE_INFINITY -> "pos inf";
      case Double.NaN -> "nan";
      default -> "other";
    };
  }

  static String dNegZero() {
    double d = -0.0;
    return switch (d) {
      case Double.NEGATIVE_INFINITY -> "neg inf";
      case <warning descr="Switch label '-0.0' is the only reachable in the whole switch">-0.0</warning> -> "neg zero";
      case 0.0 -> "pos zero";
      case Double.POSITIVE_INFINITY -> "pos inf";
      case Double.NaN -> "nan";
      default -> "other";
    };
  }

  static String dPosZero() {
    double d = 0.0;
    return switch (d) {
      case Double.NEGATIVE_INFINITY -> "neg inf";
      case -0.0 -> "neg zero";
      case <warning descr="Switch label '0.0' is the only reachable in the whole switch">0.0</warning> -> "pos zero";
      case Double.POSITIVE_INFINITY -> "pos inf";
      case Double.NaN -> "nan";
      default -> "other";
    };
  }

  static String dPosInf() {
    double d = Double.POSITIVE_INFINITY;
    return switch (d) {
      case Double.NEGATIVE_INFINITY -> "neg inf";
      case -0.0 -> "neg zero";
      case 0.0 -> "pos zero";
      case <warning descr="Switch label 'Double.POSITIVE_INFINITY' is the only reachable in the whole switch">Double.POSITIVE_INFINITY</warning> -> "pos inf";
      case Double.NaN -> "nan";
      default -> "other";
    };
  }

  static String dNaN() {
    double d = Double.NaN;
    return switch (d) {
      case Double.NEGATIVE_INFINITY -> "neg inf";
      case -0.0 -> "neg zero";
      case 0.0 -> "pos zero";
      case Double.POSITIVE_INFINITY -> "pos inf";
      case <warning descr="Switch label 'Double.NaN' is the only reachable in the whole switch">Double.NaN</warning> -> "nan";
      default -> "other";
    };
  }

  static String fShort2(float f) {
    return switch (f) {
      case 0.0f -> "pos zero";
      case -0.0f -> "neg zero";
      default -> "other";
    };
  }

  static String fShort1(float f) {
    return switch (f) {
      case Float.NaN -> "nan";
      default -> "other";
    };
  }

  int examineFloat(float f) {
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
