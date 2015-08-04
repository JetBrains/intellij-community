class Main {
  public void test() {
      Path path = Paths.get("/tmp");
      foo(getf<caret>path, 5);
  }

  private String getFileName(Path path) { return path.toString(); }
  private void foo(String text, int count) {}
}
