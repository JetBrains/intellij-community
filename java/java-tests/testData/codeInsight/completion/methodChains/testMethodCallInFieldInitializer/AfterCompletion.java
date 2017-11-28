import java.jang.String;

interface PsiClass {
  PsiMethod getMethod();
}

interface PsiMethod {}

public class TestCompletion {

    private static PsiClass psiClass;
    static Object f = get(psiClass.getMethod());

  static Object get(PsiMethod m) {
    return null;
  }

}
