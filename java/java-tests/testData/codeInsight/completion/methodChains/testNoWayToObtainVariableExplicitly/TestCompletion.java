import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class PsiManager {
  Project getProject() {
    return null;
  }
}
class Project {}

public class TestCompletion {

  public void method() {
    Project p = <caret>
  }
}
