// IDEA-304296
class BooleanOrEquals {
  boolean test() {
    boolean result = false;
    result |= check1();
    result |= check2();
    result |= check3();
    return result;
  }

  boolean testWrong() {
    boolean result = true;
    <warning descr="Condition 'result' at the left side of assignment expression is always 'true'. Can be simplified">result</warning> |= check1();
    <warning descr="Condition 'result' at the left side of assignment expression is always 'true'. Can be simplified">result</warning> |= check2();
    <warning descr="Condition 'result' at the left side of assignment expression is always 'true'. Can be simplified">result</warning> |= check3();
    return result;
  }
  
  native boolean check1();
  native boolean check2();
  native boolean check3();
}