/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
import java.lang.String;

public class TestIndex {

  PsiElement e;

  PsiManager m;

  void m() {
    m.getProject();
    m.getProject();
    m.getProject();
    m.getProject();
    m.getProject();
    m.getProject();
    e.getProject1();
    e.getProject1();
    e.getProject1();
    e.getProject1();
  }
}


interface PsiManager {
  Project getProject();
}

interface PsiElement {
  Project getProject1();
}

interface Project {}
