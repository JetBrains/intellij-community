import org.jetbrains.annotations.Nullable;

class DDD {
  @Nullable
  String field;
  int test() {
    return new DDD().field.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
  }
}
