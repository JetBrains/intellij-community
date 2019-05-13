/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(LocalFileSystemAware lfsa) {
    lfsa.returnLFS();
    lfsa.returnLFS();
    lfsa.returnLFS();
    lfsa.returnLFS();
    LocalFileSystem.getInstance();
    LocalFileSystem.getInstance();
  }
}

class LocalFileSystem {
  public static LocalFileSystem getInstance() {
    return null;
  }
}

class LocalFileSystemAware {
  public LocalFileSystem returnLFS() {
    return null;
  }
}
