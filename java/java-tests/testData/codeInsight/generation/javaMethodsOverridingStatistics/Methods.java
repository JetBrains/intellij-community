import java.lang.Override;
import java.lang.String;

class BaseClass {
  public void method() {
    //do nothing
  }

  public void method2() {

  }
}

class ClassEx1 extends BaseClass {
  @Override
  public void method() {
  }
}

class ClassEx2 extends BaseClass {
  public void method() {
  }

  public void method(String aString) {
  }
}

class MyClass extends B<caret>aseClass {

}