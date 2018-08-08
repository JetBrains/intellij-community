import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

public class AssertAll {
  native @Nullable String readNullable();

  void test() {
    String s1 = readNullable();
    String s2 = readNullable();
    String s3 = readNullable();
    String s4 = readNullable();
    Assertions.assertAll(
      () -> Assertions.assertNotNull(<warning descr="Argument 's1' might be null but passed to non-annotated parameter">s1</warning>),
      () -> Assertions.assertTrue(s1.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>().isEmpty()),
      <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>
    );
    Assertions.assertNotNull(<warning descr="Argument 's2' might be null but passed to non-annotated parameter">s2</warning>);
    Assertions.assertAll(
      readNullable(), // message
      () -> Assertions.assertTrue(s1.trim().isEmpty()),
      () -> Assertions.assertTrue(s1.trim().isEmpty()),
      () -> Assertions.assertTrue(s2.trim().isEmpty()),
      () -> Assertions.assertTrue(s3.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>().isEmpty()),
      () -> Assertions.assertTrue(s4.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>().isEmpty())
    );
    Assertions.assertAll(
      () -> Assertions.assertTrue(<warning descr="Result of 's1.trim().isEmpty()' is always 'true'">s1.trim().isEmpty()</warning>),
      () -> Assertions.assertTrue(<warning descr="Result of 's2.trim().isEmpty()' is always 'true'">s2.trim().isEmpty()</warning>),
      () -> Assertions.assertTrue(<warning descr="Result of 's3.trim().isEmpty()' is always 'true'">s3.trim().isEmpty()</warning>),
      () -> Assertions.assertTrue(<warning descr="Result of 's4.trim().isEmpty()' is always 'true'">s4.trim().isEmpty()</warning>)
    );
  }
}