// "Annotate parameter 'sb' as '@NonNls'" "true-preview"
class Foo {
  java.util.function.Consumer<StringBuilder> consumerTest() {
    return sb -> sb.append("<caret>foo");
  }
}
