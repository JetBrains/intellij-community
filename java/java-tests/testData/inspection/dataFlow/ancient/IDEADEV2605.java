public class AndAssign {
  public void foo(boolean result, Object acc) {
    result &= verify(result ? acc : null);
  }

  public boolean verify(Object o) {
    System.out.println(o);
    return true;
  }

  public void positives() {
     boolean t = true;
     boolean f = false;
     boolean r;

     r = t;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'true'. Can be simplified">r</warning> &= t;                      // Always true

     if (<warning descr="Condition 'r' is always 'true'">r</warning>) {                     // Always true
        System.out.println("foo");
     }

     <warning descr="Variable is already assigned to this value">r</warning> = t;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'true'. Can be simplified">r</warning> &= f;                      // Always true

     if (<warning descr="Condition 'r' is always 'false'">r</warning>) {                     // Always false
        System.out.println("foo");
     }

     <warning descr="Variable is already assigned to this value">r</warning> = f;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'false'. Can be simplified">r</warning> &= t;                      // Always false

     if (<warning descr="Condition 'r' is always 'false'">r</warning>) {                     // Always false
        System.out.println("foo");
     }

     <warning descr="Variable is already assigned to this value">r</warning> = f;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'false'. Can be simplified">r</warning> &= f;                      // Always false

     if (<warning descr="Condition 'r' is always 'false'">r</warning>) {                     // Always false
        System.out.println("foo");
     }

     r = t;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'true'. Can be simplified">r</warning> |= t<error descr="';' expected"> </error>                      // Always true

     if (<warning descr="Condition 'r' is always 'true'">r</warning>) {                     // Always true
        System.out.println("foo");
     }

     <warning descr="Variable is already assigned to this value">r</warning> = t;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'true'. Can be simplified">r</warning> |= f<error descr="';' expected"> </error>                      // Always true

     if (<warning descr="Condition 'r' is always 'true'">r</warning>) {                     // Always true
        System.out.println("foo");
     }

     r = f;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'false'. Can be simplified">r</warning> |= t<error descr="';' expected"> </error>                      // Always false

     if (<warning descr="Condition 'r' is always 'true'">r</warning>) {                     // Always true
        System.out.println("foo");
     }

     r = f;
     <warning descr="Condition 'r' at the left side of assignment expression is always 'false'. Can be simplified">r</warning> |= f<error descr="';' expected"> </error>                     // Always false

     if (<warning descr="Condition 'r' is always 'false'">r</warning>) {                     // Always false
        System.out.println("foo");
     }
  }
}