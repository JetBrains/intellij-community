import org.jetbrains.annotations.Nullable;
import org.junit.*;

public class AssumeNotNull {

  @Nullable
  private static String someString;

  public static void initialize() {
    someString = System.getProperty("some.property.name");
  }

  public void testSomeStringStartsWithAbc() {
    Assume.assumeNotNull(someString);
    Assert.assertTrue(someString.startsWith("abc"));
  }

  public void testSomeStringStartsWithAbc2() {
    Assume.assumeNotNull("foo", someString, "bar");
    Assert.assertTrue(someString.startsWith("abc"));
  }

  public void testNoAssumeSomeStringStartsWithAbc() {
    Assert.assertTrue(someString.<warning descr="Method invocation 'startsWith' may produce 'java.lang.NullPointerException'">startsWith</warning>("abc"));
  }
}