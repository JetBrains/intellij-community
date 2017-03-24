import java.lang.invoke.*;
import java.util.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findSetter(Test.class, "myInt", int.class);
    l.findSetter(Test.class, "myInts", int[].class);
    l.findSetter(Test.class, "myList", List.class);
    l.findSetter(Test.class, "myLists", List[].class);
    l.findSetter(Test.class, "myString", String.class);

    l.findStaticSetter(Test.class, "ourInt", int.class);
    l.findStaticSetter(Test.class, "ourInts", int[].class);
    l.findStaticSetter(Test.class, "ourList", List.class);
    l.findStaticSetter(Test.class, "ourLists", List[].class);
    l.findStaticSetter(Test.class, "ourString", String.class);

    l.findSetter(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);
    l.findStaticSetter(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);

    l.findSetter(Test.class, "myInt", <warning descr="The type of field 'myInt' is 'int'">void.class</warning>);
    l.findSetter(Test.class, "myInts", <warning descr="The type of field 'myInts' is 'int[]'">int.class</warning>);
    l.findSetter(Test.class, "myString", <warning descr="The type of field 'myString' is 'java.lang.String'">List.class</warning>);

    l.findStaticSetter(Test.class, "ourInt", <warning descr="The type of field 'ourInt' is 'int'">void.class</warning>);
    l.findStaticSetter(Test.class, "ourInts", <warning descr="The type of field 'ourInts' is 'int[]'">int.class</warning>);
    l.findStaticSetter(Test.class, "ourString", <warning descr="The type of field 'ourString' is 'java.lang.String'">List.class</warning>);

    l.<warning descr="Field 'myString' is not static">findStaticSetter</warning>(Test.class, "myString", String.class);
    l.<warning descr="Field 'ourString' is static">findSetter</warning>(Test.class, "ourString", String.class);
  }
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