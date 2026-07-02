public class SwitchBoxedFloatRepresentation {

  static void main() {
    System.out.println(fNegInf());
    System.out.println(fNegZero());
    System.out.println(fPosZero());
    System.out.println(fPosInf());
    System.out.println(fNaN());
    System.out.println(fNaN2());
    System.out.println(f1());
    System.out.println(fOther());


    System.out.println(dNegInf2());
    System.out.println(dNegZero());
    System.out.println(dPosZero());
    System.out.println(dPosInf());
    System.out.println(dNaN());

    System.out.println(fShort1(Float.NaN));
    System.out.println(fShort2(-0.0f));
  }

  static String fNegInf() {
    Float f = Float.NEGATIVE_INFINITY;
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
    Float f = -0.0f;
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
    Float f = 0.0f;
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
    Float f = Float.POSITIVE_INFINITY;
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
    Float f = Float.NaN;
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
    Float f = Float.intBitsToFloat(0x7fc00000);
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
    Float f = 1.0f;
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
    Float f = 1.0f;
    return switch (f) {
      case <warning descr="Switch label 'Float.NEGATIVE_INFINITY' is unreachable">Float.NEGATIVE_INFINITY</warning> -> "neg inf";
      case <warning descr="Switch label '-0.0f' is unreachable">-0.0f</warning> -> "neg zero";
      case <warning descr="Switch label '0.0f' is unreachable">0.0f</warning> -> "pos zero";
      case <warning descr="Switch label 'Float.POSITIVE_INFINITY' is unreachable">Float.POSITIVE_INFINITY</warning> -> "pos inf";
      case <warning descr="Switch label 'Float.NaN' is unreachable">Float.NaN</warning> -> "nan";
      default -> "other";
    };
  }


  static String dNegInf2() {
    Double d = Double.NEGATIVE_INFINITY;
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
    Double d = -0.0;
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
    Double d = 0.0;
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
    Double d = Double.POSITIVE_INFINITY;
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
    Double d = Double.NaN;
    return switch (d) {
      case Double.NEGATIVE_INFINITY -> "neg inf";
      case -0.0 -> "neg zero";
      case 0.0 -> "pos zero";
      case Double.POSITIVE_INFINITY -> "pos inf";
      case <warning descr="Switch label 'Double.NaN' is the only reachable in the whole switch">Double.NaN</warning> -> "nan";
      default -> "other";
    };
  }

  static String fShort2(Float f) {
    return switch (f) {
      case 0.0f -> "pos zero";
      case -0.0f -> "neg zero";
      default -> "other";
    };
  }

  static String fShort1(Float f) {
    return switch (f) {
      case Float.NaN -> "nan";
      default -> "other";
    };
  }
}
