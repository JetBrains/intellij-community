public class A {

  Project p;
  MyElement fieldElement;

  static MyElement staticElement;

  public void m() {
    fieldElement.getProject();
  }

  interface Project {

  }

  interface MyElement {
    Project getProject();
  }

}