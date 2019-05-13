/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod() {
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
    ProjectFileIndex.getInstance(null);
  }
}

class FileIndex {
}

class ProjectFileIndex extends FileIndex {
  public static ProjectFileIndex getInstance(Project p) {
    return null;
  }
}

interface Project {}

