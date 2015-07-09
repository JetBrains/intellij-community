class Main {
  public void test() {
      Path path = Paths.get("/tmp");
      foo(getFileName(path), 5);<caret>
  }

  private String getFileName(Path path) { return path.toString(); }
  private void foo(String text, int count) {}
}
