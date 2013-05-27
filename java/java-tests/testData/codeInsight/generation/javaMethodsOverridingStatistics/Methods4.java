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
  public void method(String s) {
  }

  public void method2() {
  }
}

class ClassEx11 extends ClassEx1 {
  public void method2() {
  }
}

class ClassEx2 extends BaseClass {
  public void method() {
  }
}

class ClassEx3 extends BaseClass {
  public void method() {
  }
}

class MyClass extends Cl<caret>assEx1 {

}