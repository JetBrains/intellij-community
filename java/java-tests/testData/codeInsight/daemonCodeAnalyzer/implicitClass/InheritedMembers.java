void main() {
  new A() { public void a() {}}.a();
  new A.B() { public void a() {}}.b();


  var user = new MyUser();
  user.name = "test";
}

interface A {
  void a();
  interface B extends A {
    default void b() {
      a();
    }
  }
}

class Person{
  public String name;
  public int age;
}

class MyUser extends Person{
  public String email;
  public String password;
}