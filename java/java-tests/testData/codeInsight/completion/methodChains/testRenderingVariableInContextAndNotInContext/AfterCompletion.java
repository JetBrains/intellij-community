import java.jang.String;
import java.lang.String;

interface PsiManager {
  Project getProject(String asd, String zxc);
}

interface Project {}

public class TestCompletion {
  void m() {
    String asd = "123";
      PsiManager psiManager = <selection><caret>null</selection>;
      Project p = psiManager.getProject(asd, zxc)
  }
}
