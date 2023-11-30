import java.lang.Object;

public class A {

  Project p;
  MyElement fieldElement;

  static MyElement staticElement;

  public void m(MyElement paramElement) {

    Object o = new Object() {

      private final MyElement nestedField;

      public void targetMethod(MyElement nestedParamElement) {

        final MyElement localElement1 = getElement();

        localElement1.getProject()<caret>;

        MyElement localElement2 = getElement();

      }
    }
  }

  interface Project {

  }

  interface MyElement {
    Project getProject();
  }

}