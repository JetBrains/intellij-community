import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */


class JarFile {
  class JarEntry {
  }

  JarEntry getEntry() {
    return null;
  }
}


public class TestCompletion {

  public void method(JarFile j) {
    JarFile.JarEntry e = <caret>
  }
}
