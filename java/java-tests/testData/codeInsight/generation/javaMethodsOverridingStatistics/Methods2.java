import java.lang.Override;
import java.lang.String;

class BaseClass {
  public void method() {
    //do nothing
  }

  public void method(String s) {
    //do nothing
  }
}

class ClassEx1 extends BaseClass {
  @Override
  public void method() {
  }

  @Override
  public void method(String s) {
  }
}

class ClassEx2 extends BaseClass {
  public void method() {
  }
}

class MyClass extends B<caret>aseClass {

}