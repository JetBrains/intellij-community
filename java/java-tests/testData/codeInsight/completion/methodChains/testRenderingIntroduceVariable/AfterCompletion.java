import java.jang.String;

interface PsiManager {
  Project getProject();
}

interface Project {}

public class TestCompletion {
  void m() {
      PsiManager psiManager = <selection><caret>null</selection>;
      Project p = psiManager.getProject()
  }
}
