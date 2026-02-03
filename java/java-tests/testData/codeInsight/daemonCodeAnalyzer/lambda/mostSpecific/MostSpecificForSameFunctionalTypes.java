abstract class HighlightTestInfo {
  protected final String[] filePaths;
  public HighlightTestInfo(Disposable <warning descr="Parameter 'buf' is never used">buf</warning>, String... filePaths) {
    this.filePaths = filePaths;
  }

  protected abstract HighlightTestInfo doTest();
}

class StreamMain {
  private Disposable <warning descr="Private field 'testRootDisposable' is never assigned">testRootDisposable</warning>;

  public HighlightTestInfo testFile(String... filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      public HighlightTestInfo doTest() {
        return this;
      }
    };
  }

  public Disposable getTestRootDisposable() {
    return testRootDisposable;
  }
}

interface Disposable {
  void dispose();

  interface Parent extends Disposable {
    void beforeTreeDispose();
  }

}