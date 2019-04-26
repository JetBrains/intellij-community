// "Convert to local" "true"
class Test {

  private String <caret>field;

  private Runnable r = () -> {
    field = "foo";
  }
}