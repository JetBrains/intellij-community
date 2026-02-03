import java.lang.invoke.*;
import java.util.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findVarHandle(Test.class, "myInt", int.class);
    l.findVarHandle(Test.class, "myInts", int[].class);
    l.findVarHandle(Test.class, "myList", List.class);
    l.findVarHandle(Test.class, "myLists", List[].class);
    l.findVarHandle(Test.class, "myString", String.class);

    l.findStaticVarHandle(Test.class, "ourInt", int.class);
    l.findStaticVarHandle(Test.class, "ourInts", int[].class);
    l.findStaticVarHandle(Test.class, "ourList", List.class);
    l.findStaticVarHandle(Test.class, "ourLists", List[].class);
    l.findStaticVarHandle(Test.class, "ourString", String.class);

    l.findVarHandle(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);
    l.findStaticVarHandle(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);

    l.findVarHandle(Test.class, "myInt", <warning descr="The type of field 'myInt' is 'int'">void.class</warning>);
    l.findVarHandle(Test.class, "myInts", <warning descr="The type of field 'myInts' is 'int[]'">int.class</warning>);
    l.findVarHandle(Test.class, "myString", <warning descr="The type of field 'myString' is 'java.lang.String'">List.class</warning>);

    l.findStaticVarHandle(Test.class, "ourInt", <warning descr="The type of field 'ourInt' is 'int'">void.class</warning>);
    l.findStaticVarHandle(Test.class, "ourInts", <warning descr="The type of field 'ourInts' is 'int[]'">int.class</warning>);
    l.findStaticVarHandle(Test.class, "ourString", <warning descr="The type of field 'ourString' is 'java.lang.String'">List.class</warning>);

    l.<warning descr="Field 'myString' is not static">findStaticVarHandle</warning>(Test.class, "myString", String.class);
    l.<warning descr="Field 'ourString' is static">findVarHandle</warning>(Test.class, "ourString", String.class);

    MethodHandles.arrayElementVarHandle(Test[].class);
    MethodHandles.arrayElementVarHandle(int[].class);
    MethodHandles.arrayElementVarHandle(cloneable().getClass());

    MethodHandles.arrayElementVarHandle(<warning descr="Argument is not an array type">Test.class</warning>);
    MethodHandles.arrayElementVarHandle(<warning descr="Argument is not an array type">int.class</warning>);
  }

  static Cloneable cloneable() {return null;}
}

class Test {
  public int myInt;
  public String myString;
  public int[] myInts;
  public List<String> myList;
  @SuppressWarnings("unchecked")
  public List<String>[] myLists;

  public static int ourInt;
  public static String ourString;
  public static int[] ourInts;
  public static List<String> ourList;
  @SuppressWarnings("unchecked")
  public static List<String>[] ourLists;
}