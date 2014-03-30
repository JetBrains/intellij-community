/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class LocalFileSystem {
  public LocalFileSystem returnSelf() {
    return this;
  }

  public static LocalFileSystem getInstance() {
    return null;
  }
}

public class TestCompletion {
  public void method(LocalFileSystem lfs) {
    LocalFileSystem anotherLfs = <caret>
  }
}
