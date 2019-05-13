import java.lang.String;

public class TestIndex {

  PsiManager m;

  void m() {
    m.getProject(null, null);
    m.getProject(null, null);
    m.getProject(null, null);
    m.getProject(null, null);
    m.getProject(null, null);
    m.getProject(null, null);
  }
}


interface PsiManager {
  Project getProject(String asd, String zxc);
}

interface Project {}
