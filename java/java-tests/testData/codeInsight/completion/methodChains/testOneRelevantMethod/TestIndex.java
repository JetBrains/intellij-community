/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod(Element e) {
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
    e.getProject();
  }
}

class Element {
  public Project getProject() {
    return null;
  }
}

class Project {
}
