class Test {
  public void test(final DocumentDataHookUp hookUp) {
    Runnable r = () -> hookUp.getDocument().replace("".leng<caret>th());
  }
  private class DocumentDataHookUp {
    public String replace(int i1) {
      return null;
    }
  }
}
