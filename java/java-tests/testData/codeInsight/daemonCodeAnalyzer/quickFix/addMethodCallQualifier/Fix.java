public class A {

  Project p;
  MyElement fieldElement;

  static MyElement staticElement;

  public void m() {
    getProje<caret>ct();
  }

  interface Project {

  }

  interface MyElement {
    Project getProject();
  }

}