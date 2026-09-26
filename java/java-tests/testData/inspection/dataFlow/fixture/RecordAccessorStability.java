import org.jetbrains.annotations.Nullable;

record Record(@Nullable String name){}
record Pair(@Nullable String name, int value){}
class Test {
  record Nested(Pair pair){}

  int test(Record r) {
    if (r.name() != null) {
      if (!r.name().isEmpty()) return -1;
      unknown();
      return <warning descr="Result of 'r.name().length()' is always '0'">r.name().length()</warning>;
    }
    return 0;
  }
  
  void testNewRecord() {
    Pair pair = new Pair("", 42);
    if (<warning descr="Condition 'pair.name() == null' is always 'false'">pair.name() == null</warning>) {}
    if (<warning descr="Condition 'pair.value() == 42' is always 'true'">pair.value() == 42</warning>) {}

    Nested n = new Nested(new Pair("", 42));
    if (<warning descr="Condition 'n.pair.value() == 42' is always 'true'">n.pair.value() == 42</warning>) {}
  }

  native void unknown();
}
