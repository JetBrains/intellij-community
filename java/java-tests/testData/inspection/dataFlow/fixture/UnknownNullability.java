import org.jetbrains.annotations.*;

class UnknownNullabilityTest {
  static final String getString() {
    return "foo";
  }

  static final @UnknownNullability String getString2() {
    return "foo";
  }

  static final String getStringNullable() {
    return Math.random() > 0.5 ? "foo" : null;
  }

  static final @UnknownNullability String getStringNullable2() {
    return Math.random() > 0.5 ? "foo" : null;
  }

  void check() {
    if (<warning descr="Condition 'getString() == null' is always 'false'">getString() == null</warning>) {}
    if (getString2() == null) {}
    if (getStringNullable() == null) {}
    if (getStringNullable2() == null) {}
  }

  void deref() {
    if (Math.random() > 0.5) getString().trim();
    if (Math.random() > 0.5) getString2().trim();
    if (Math.random() > 0.5) getStringNullable().<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>();
    if (Math.random() > 0.5) getStringNullable2().trim();
  }
}