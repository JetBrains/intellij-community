// "Wrap using 'Boolean.parseBoolean()'" "true-preview"
public class TestGuard {
  public void ahem(Object obj) {
    switch(obj) {
      case String s when Boolean.parseBoolean(foo(s)) -> {}
      default -> {}
    }
  }

  private String foo(String s) {
    return "false";
  }
}