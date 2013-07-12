import java.jang.String;

class TestCompletuin{
}

interface PsiManager {
  Project getProject();
}

interface PsiElement {
  Project getProject1();
}

interface Project {}

public class TestCompletion {
  void m(PsiElement e) {
    Project p = <caret>
  }
}
