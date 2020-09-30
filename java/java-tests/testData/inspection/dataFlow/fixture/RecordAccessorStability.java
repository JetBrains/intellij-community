import org.jetbrains.annotations.Nullable;

record Record(@Nullable String name){}

class Test {
  int test(Record r) {
    if (r.name() != null) {
      if (!r.name().isEmpty()) return -1;
      unknown();
      return <warning descr="Result of 'r.name().length()' is always '0'">r.name().length()</warning>;
    }
    return 0;
  }
  
  native void unknown();
}