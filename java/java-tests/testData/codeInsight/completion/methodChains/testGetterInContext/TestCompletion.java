import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

interface Project {}

interface PsiElement {
  Project getProject();
}

public class TestCompletion {

  private PsiElement getMyElement() {
    return null;
  }

  public void method() {
    Project p = <caret>
  }
}
