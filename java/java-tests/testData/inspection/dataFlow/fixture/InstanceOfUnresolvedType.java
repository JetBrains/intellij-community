public class InstanceOfUnresolvedType {
  void test(Object o) {
    int i = 0;
    if (o instanceof <error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error>) {
      i = 1;
    }
    else if (o instanceof <error descr="Cannot resolve symbol 'Unresolved2'">Unresolved2</error>) {
      i = 2;
    }
    else if (o instanceof <error descr="Cannot resolve symbol 'Unresolved2'">Unresolved2</error>[]) {
      i = 3;
    }
    else {
      i = 4;
    }
    if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {

    }
    else if (i == 1) {

    }
    else if (i == 2) {

    }
    else if (i == 3) {

    }
    else if (<warning descr="Condition 'i == 4' is always 'true'">i == 4</warning>) {

    }
  }

  void test2(<error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error> u) {
    int i = 0;
    if (<error descr="Inconvertible types; cannot cast 'Unresolved' to 'java.lang.CharSequence'">u instanceof CharSequence cs</error>) {
      i = 1;
    }
    else if (<error descr="Inconvertible types; cannot cast 'Unresolved' to 'java.lang.Number'">u instanceof Number n</error>) {
      i = 2;
    }
    else {
      i = 3;
    }
    if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {

    }
    else if (i == 1) {

    }
    else if (i == 2) {

    }
    else if (<warning descr="Condition 'i == 3' is always 'true'">i == 3</warning>) {

    }
  }

  void test3(<error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error>[] u) {
    int i = 0;
    if (<error descr="Inconvertible types; cannot cast 'Unresolved[]' to 'java.lang.CharSequence'">u instanceof CharSequence cs</error>) {
      i = 1;
    }
    else if (<error descr="Inconvertible types; cannot cast 'Unresolved[]' to 'java.lang.Number'">u instanceof Number n</error>) {
      i = 2;
    }
    else {
      i = 3;
    }
    if (<warning descr="Condition 'i == 0' is always 'false'">i == 0</warning>) {

    }
    else if (i == 1) {

    }
    else if (i == 2) {

    }
    else if (<warning descr="Condition 'i == 3' is always 'true'">i == 3</warning>) {

    }
  }
}