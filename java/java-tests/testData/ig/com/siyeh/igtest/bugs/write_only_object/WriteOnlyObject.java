import java.util.*;

public class WriteOnlyObject {
  void testRef() {
    Ref<String> <warning descr="Write-only object">ref</warning> = new Ref<>();
    ref.setValue("Hello");
    Ref<String> <warning descr="Write-only object">ref1</warning> = new Ref<>("1");
    ref1.setValue("2");
    ref1.setValue("3");
    Ref<String> ref2 = new Ref<>();
    ref2.setValue("Hello");
    System.out.println(ref2.getValue());
    Ref<Integer> <warning descr="Write-only object">ref3</warning> = Ref.create();
    ref3.setValue(123);
    Ref.<String><warning descr="Write-only object">create</warning>().setValue("foo");
  }

  void testPoint() {
    Point <warning descr="Write-only object">point</warning> = new Point();
    point.setX(1);
    point.setY(2);
  }

  void testMyBuilder() {
    MyBuilder <warning descr="Write-only object">builder</warning> = new MyBuilder();
    builder.firstName("John").lastName("Doe");
    new <warning descr="Write-only object">MyBuilder</warning>().firstName("John").lastName("Doe");
  }

  void testUnmodifiable() {
    getList().add("1");
  }

  void testModifiable() {
    <warning descr="Write-only object">getList2</warning>().add("1");
  }

  private List<String> getList() {
    return Collections.emptyList();
  }

  private List<String> getList2() {
    return new ArrayList<>();
  }
}
final class Ref<T> {
  T value;

  static <T> Ref<T> create() {
    return new Ref<>();
  }

  Ref(T value) {this.value = value;}
  Ref() {}

  void setValue(T value) {this.value = value;}
  T getValue() {return value;}
}
class Point {
  private int x = 0, y = 0;

  void setX(int x) {this.x = x;}
  void setY(int y) {this.y = y;}

  int getX() {return x;}
  int getY() {return y;}
}
final class MyBuilder {
  private String firstName;
  private String lastName;

  MyBuilder firstName(String firstName) {
    this.firstName = firstName;
    return this;
  }

  MyBuilder lastName(String lastName) {
    this.lastName = lastName;
    return this;
  }
}
class MyClass implements Cloneable {
  int x;

  public Object clone() throws CloneNotSupportedException {
    MyClass <warning descr="Write-only object">myClass</warning> = (MyClass) super.clone();
    myClass.x = 10;
    return super.clone();
  }

  public Object clone2() throws CloneNotSupportedException {
    MyClass myClass = (MyClass) super.clone();
    System.out.println(myClass.x = 10);
    return super.clone();
  }
}