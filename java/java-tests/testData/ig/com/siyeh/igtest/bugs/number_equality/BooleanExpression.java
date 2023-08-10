public class BooleanExpression {
  boolean or1(Double v1, Double v2) {
    return (v1 == null || v2 == null) && v1 == v2;
  }

  boolean or2(Double v1, Double v2) {
    return v1 == v2 && (v1 == null || v2 == null);
  }

  boolean nand1(Double v1, Double v2) {
    return !(v2 != null && v1 != null) && v1 == v2;
  }

  boolean nand2(Double v1, Double v2) {
    return v1 == v2 && !(v2 != null && v1 != null);
  }

  Number n1, n2;

  boolean differentQualifiers(BooleanExpression other) {
    return  (other.n1 == null || other.n2 == null) && n1 <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> n2;
  }
}
