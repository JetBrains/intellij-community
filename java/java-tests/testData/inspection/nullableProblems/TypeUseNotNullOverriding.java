import typeUse.*;

class Super {
  void m1(byte @NotNull [] p) {}
  void m2(byte @NotNull [] p) {}
  void m3(byte[] p) {}
}

class Sub extends Super {
  void m1(byte @NotNull [] p) {}
  void m2(byte[] <warning descr="Not annotated parameter overrides @NotNull parameter">p</warning>) {}
  void m3(byte <warning descr="Parameter annotated @NotNull should not override non-annotated parameter">@NotNull</warning> [] p) {}
  
}