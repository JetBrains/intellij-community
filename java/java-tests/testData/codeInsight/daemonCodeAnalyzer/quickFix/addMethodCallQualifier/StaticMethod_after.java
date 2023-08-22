public class A {

  Project p;
  MyElement fieldElement;

  static MyElement staticElement;

  public static void targetMethod(MyElement paramElement) {

    MyElement localElement1 = getElement();

    localElement1.getProject()<caret>;

    MyElement localElement2 = getElement();

  }

  interface Project {

  }

  interface MyElement {
    Project getProject();
  }

}