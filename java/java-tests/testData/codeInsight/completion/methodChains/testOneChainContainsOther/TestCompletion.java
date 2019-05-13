/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class PsiManager {
  Project getProject() {
    return null;
  }
}

interface Project {
  VirtualFile getBaseDir();
}

class VirtualFile {
}

public class TestCompletion {
  PsiManager m;

  public void method(Project p) {
    VirtualFile projectBaseDir = <caret>
  }
}
