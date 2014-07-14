import org.jetbrains.annotations.Nullable;

class DDD {
  @Nullable
  String field;
  int test() {
    return <warning descr="Method invocation 'new DDD().field.hashCode()' may produce 'java.lang.NullPointerException'">new DDD().field.hashCode()</warning>;
  }
}
