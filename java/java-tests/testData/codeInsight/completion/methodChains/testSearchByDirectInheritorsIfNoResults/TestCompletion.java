import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */


class FileIndex {
}

class ProjectFileIndex extends FileIndex {
  public static ProjectFileIndex getInstance(Project p) {
    return null;
  }
}

interface Project {}

public class TestCompletion {

  public void method() {
    FileIndex fi = <caret>
  }
}
