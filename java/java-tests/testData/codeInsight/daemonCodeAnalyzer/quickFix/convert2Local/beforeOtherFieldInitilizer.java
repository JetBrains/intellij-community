// "Convert to local variable" "true-preview"
class Test {

  private String <caret>field;

  private Runnable r = () -> {
    field = "foo";
  }
}