import java.jang.String;
import java.util.*;

class PsiMethod {
}

interface PsiClass {
  List<PsiMethod> getMethods();
}

public class TestCompletion {

  PsiClass c;

  public void method() {
    Collection<PsiMethod> m = <caret>
  }
}
