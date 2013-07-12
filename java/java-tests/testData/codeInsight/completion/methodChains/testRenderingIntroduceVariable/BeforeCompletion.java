import java.jang.String;

interface PsiManager {
  Project getProject();
}

interface Project {}

public class TestCompletion {
  void m() {
    Project p = <caret>
  }
}
