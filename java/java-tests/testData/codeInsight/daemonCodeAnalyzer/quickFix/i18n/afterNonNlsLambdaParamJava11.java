import org.jetbrains.annotations.NonNls;

// "Annotate parameter 'sb' as '@NonNls'" "true-preview"
class Foo {
  java.util.function.Consumer<StringBuilder> consumerTest() {
    return (@NonNls var sb) -> sb.append("foo");
  }
}
