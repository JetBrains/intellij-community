import java.jang.String;

interface Project {
}

interface PsiFile {
}

interface VirtualFile {
}

interface Document {
}

class PsiManager {
  static PsiManager getInstance(Project p) {
    return null;
  }

  PsiFile findFile(VirtualFile f) {
    return null;
  }
}

class PsiDocumentManager {
  static PsiDocumentManager getInstance(Project p) {
    return null;
  }

  PsiFile getPsiFile(Document d) {
    return null;
  }
}

interface PsiFileFactory {
  PsiFile createFileFromText(String s);
}

interface PsiClass {
  PsiFile getContainingClass();
}

public class TestCompletion {
  void m() {
    PsiFileFactory f = null;
    VirtualFile vf = null;
    PsiFile file = <caret>
  }
}