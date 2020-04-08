// "Annotate parameter 'sb' as @NonNls" "true"
class Foo {
  java.util.function.Consumer<StringBuilder> consumerTest() {
    return sb -> sb.append("<caret>foo");
  }
}
