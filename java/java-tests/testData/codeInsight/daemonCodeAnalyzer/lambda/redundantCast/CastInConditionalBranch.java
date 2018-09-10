import java.util.function.Supplier;

class Test {
  {
    Object o = true ? ((Supplier<String>) () -> "") : null;
    Supplier<String> s1 = true ? ((<warning descr="Casting '() -> {...}' to 'Supplier<String>' is redundant">Supplier<String></warning>) () -> "") : null;
    Supplier<String> s2 = true ? ((A) () -> "") : null;
  }

  interface A extends Supplier<String> {}
}