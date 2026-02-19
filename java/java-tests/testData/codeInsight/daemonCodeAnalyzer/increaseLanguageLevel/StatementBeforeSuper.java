public class Sample extends Parent {
  public Sample() {
    int a = 1;
    super<caret>(a);
  }
}

class Parent{
  public Parent(int a) {
  }
}