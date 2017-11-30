class SomeClientClass {
  {
    new Abstract<String>() {
      void foo() {
        if (<warning descr="Condition 'field instanceof String' is redundant and can be replaced with a null check">field instanceof String</warning>) {

        }
      }
    };
    new Abstract<Integer>() {
      void foo() {
        if (<warning descr="Condition 'field instanceof Integer' is redundant and can be replaced with a null check">field instanceof Integer</warning>) {

        }
      }
    };
  }
}

class Abstract<T> {
  T field;
}