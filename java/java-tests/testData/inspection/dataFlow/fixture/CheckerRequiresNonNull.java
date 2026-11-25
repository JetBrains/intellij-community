import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

class Holder {
  private @Nullable String foo;
  private @Nullable String bar;
  private static @Nullable String sFoo;

  @RequiresNonNull("foo")
  int simpleName() {
    return foo.length();
  }

  @RequiresNonNull("this.foo")
  int thisQualifier() {
    return foo.length();
  }

  @RequiresNonNull({"bar", "foo"})
  int arrayValue() {
    return foo.length() + bar.length();
  }

  @RequiresNonNull("foo")
  int otherField() {
    return bar.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
  }

  @RequiresNonNull("foo")
  int otherQualifier(Holder other) {
    return other.foo.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
  }

  @RequiresNonNull("foo")
  int assignedNull() {
    foo = null;
    return foo.<warning descr="Method invocation 'length' will produce 'NullPointerException'">length</warning>();
  }

  @RequiresNonNull("Holder.sFoo")
  static int staticField() {
    return sFoo.length();
  }

  @RequiresNonNull("sFoo")
  static int staticFieldSimpleName() {
    return sFoo.length();
  }

  @custom.RequiresNonNull("foo")
  int customAnnotation() {
    return foo.length();
  }

  int noAnnotation() {
    return foo.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>();
  }

  int callSiteIsNotChecked() {
    return simpleName();
  }
}
