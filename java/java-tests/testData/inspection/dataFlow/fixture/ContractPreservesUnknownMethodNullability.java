import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class TestCase {

  private void countWordsWarning() {
    // no warnings should be here
    String s = normalizeSpace(unknown()).trim();
    String s3 = normalizeSpaceInverted(unknown()).trim();

    String s4 = normalizeSpace(null).<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>();
  }

  public static native String unknown();

  @Contract("null->null")
  public static native String normalizeSpace(@Nullable String str);

  @Contract("!null->!null; _->null")
  public static native String normalizeSpaceInverted(String str);

}