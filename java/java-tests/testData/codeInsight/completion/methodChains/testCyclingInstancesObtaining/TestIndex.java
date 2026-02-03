public class TestIndex {

  Manager manager = new Manager(new Project());

  public void statMethod() {
    manager.getProject();
    manager.getProject();
    manager.getProject();
    manager.getProject();
    manager.getProject();
    manager.getProject();
    manager.getProject();
  }
}

class TestIndex2 {

  Project p2 = new Project();

  public void statMethod() {
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
    Manager.getManager(p2);
  }

}

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
