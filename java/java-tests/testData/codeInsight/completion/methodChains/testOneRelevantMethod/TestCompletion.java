/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class Element {
  public Project getProject() {
    return null;
  }
}

class Project {
}

public class TestCompletion {
  public void method(Element e) {
   Project project = <caret>
  }
}
