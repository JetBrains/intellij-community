import java.util.function.Supplier;

interface FieldOp<FIELD> {
  private static Supplier<String> getSupplier(String type, Object... args) {
    return () -> "placeholder";
  }

  static Supplier<String> outer() {
    return getSupplier("OUTER", 1);
  }

  interface Read<PARENT, FIELD> extends FieldOp<PARENT> {
    static <PARENT> Supplier<String> getSupplier(PARENT parent) {
      return getSupplier("READ", <error descr="Expected 1 argument but found 2">parent</error>);
    }

    static Supplier<String> qualified(Object parent) {
      return FieldOp.getSupplier("READ", parent);
    }
  }

  class Nested {
    Supplier<String> get() {
      return getSupplier("NESTED");
    }
  }
}
