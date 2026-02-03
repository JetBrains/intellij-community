/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(PsiManager m) {
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
   m.getProject().getBaseDir();
  }
}

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
