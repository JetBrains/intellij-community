/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(JarFile f) {
    f.getEntry();
    f.getEntry();
    f.getEntry();
    f.getEntry();
    f.getEntry();
    f.getEntry();
    f.getEntry();
    f.getEntry();
  }
}

class JarFile {
  class JarEntry {
  }

  JarEntry getEntry() {
    return null;
  }
}

