public class A {

  Project p;
  MyElement fieldElement;

  static MyElement staticElement;

  public static void targetMethod(MyElement paramElement) {

    MyElement localElement1 = getElement();

    getProje<caret>ct();

    MyElement localElement2 = getElement();

  }

  interface Project {

  }

  interface MyElement {
    Project getProject();
  }

}