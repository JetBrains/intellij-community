public class Test {
  public Test(String s) { }

  public Test foo(String path) {
    Test smth = new Test(path);
    if (path == null) return null;
    return smth;
  }
}
