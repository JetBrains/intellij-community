import java.jang.String;

class PsiManager {
  public static PsiManager getInstance() {
    return null;
  }
}

interface PsiClass {
  PsiManager getManager();
}

interface PsiMethod {
  PsiClass getContainingClass();
}

interface PsiMethodCallExpression {
  PsiMethod resolveMethod();
}

public class TestCompletion {

  public void method() {
    PsiManager m = <caret>
  }
}
