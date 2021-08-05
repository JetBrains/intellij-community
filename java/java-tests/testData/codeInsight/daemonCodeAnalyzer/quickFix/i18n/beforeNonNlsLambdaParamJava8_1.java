// "Annotate parameter 's' as @Nls" "true"
class Foo {
  String foo(@org.jetbrains.annotations.Nls String s) { return s;}
  {
    java.util.function.Function<String, String> f = s -> foo(<caret>s);
  }
}
