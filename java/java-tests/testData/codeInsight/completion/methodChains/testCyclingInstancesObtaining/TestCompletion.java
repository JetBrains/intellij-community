import java.jang.String;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */

class Project {
  Object o;

  void setObject(Object o) {
    this.o = o;
  }

  <T> Object getObject(Class<T> tClass) {
    return o;
  }
}

class Manager {

  Project p1;

  Manager(Project p1) {
    this.p1 = p1;
  }

  static Manager getManager(Project project) {
    return (Manager) project.getObject(Manager.class);
  }

  Project getProject() {
    return p1;
  }

}

public class TestCompletion {

  public void method() {
    Manager m = <caret>;
    Project p = m.getProject();
  }
}
