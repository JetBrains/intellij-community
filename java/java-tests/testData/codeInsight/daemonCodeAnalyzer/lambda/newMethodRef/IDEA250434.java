package foo;

import java.util.Optional;
class Boo {
  private static class MyGenericClass<L> {
    public final String field;

    private MyGenericClass(String field) {
      this.field = field;
    }

    static MyGenericClass<?> create(String field) {
      return new MyGenericClass<>(field);
    }
  }

  public static void main(String[] args) {
    Optional<String> value = Optional.of("value");
    MyGenericClass<Integer> myClassValue = value.map(MyGenericClass::<Integer>create).<error descr="Incompatible types. Found: 'foo.Boo.MyGenericClass<capture<?>>', required: 'foo.Boo.MyGenericClass<java.lang.Integer>'">orElse</error>(null);
  }

}

