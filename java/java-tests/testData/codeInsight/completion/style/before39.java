class A{
 public static final A ONE = new A();
 public static final A TWO = new A();
 public static final A THREE = new A();
}

class B{

 static A foo(){
  return <caret>;
 }
}
