public class Test {
  public String foo(String[] path) {
    if (path != null) return null;
    for (String p: path) {}

    return "";
  }
}
