public class TestCompletion {
  void m() {
    PsiElement someVar = get(<caret>);
  }

  static PsiElement get(Project project) {
    return null;
  }
}

interface Project {
}

interface PsiElement {
  Project getProject();
}
