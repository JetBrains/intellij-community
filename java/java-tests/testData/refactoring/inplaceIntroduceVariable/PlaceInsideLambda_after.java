class Test {
  public void test(final DocumentDataHookUp hookUp) {
    Runnable r = () -> {
        int expr = "".length();
        hookUp.getDocument().replace(expr);
    };
  }
  private class DocumentDataHookUp {
    public String replace(int i1) {
      return null;
    }
  }
}
