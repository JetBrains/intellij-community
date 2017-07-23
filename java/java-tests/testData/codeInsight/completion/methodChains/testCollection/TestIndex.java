import java.util.*;


public class TestIndex {

  public void statMethod(PsiClass c) {
    c.getMethods();
    c.getMethods();
    c.getMethods();
    c.getMethods();
    c.getMethods();
    c.getMethods();
    c.getMethods();
    c.getMethods();
  }
}

class PsiMethod {
}

interface PsiClass {
  Collection<PsiMethod> getMethods();
}
