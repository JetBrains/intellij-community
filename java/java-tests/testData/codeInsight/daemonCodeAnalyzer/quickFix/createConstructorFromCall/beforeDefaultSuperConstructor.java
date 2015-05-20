// "Create constructor" "true"
class Test extends A{

    public void t() {
        new Test(<caret>"a"){};
    }
}

class A {
  A(String s){}
  A(){}
}