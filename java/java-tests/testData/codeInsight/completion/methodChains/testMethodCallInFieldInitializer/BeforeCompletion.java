import java.jang.String;

interface PsiClass {
  PsiMethod getMethod();
}

interface PsiMethod {}

public class TestCompletion {

  static Object f = get(<caret>);

  static Object get(PsiMethod m) {
    return null;
  }

}
