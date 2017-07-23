import java.lang.invoke.*;
import java.util.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findGetter(Test.class, "myInt", int.class);
    l.findGetter(Test.class, "myInts", int[].class);
    l.findGetter(Test.class, "myList", List.class);
    l.findGetter(Test.class, "myLists", List[].class);
    l.findGetter(Test.class, "myString", String.class);

    l.findStaticGetter(Test.class, "ourInt", int.class);
    l.findStaticGetter(Test.class, "ourInts", int[].class);
    l.findStaticGetter(Test.class, "ourList", List.class);
    l.findStaticGetter(Test.class, "ourLists", List[].class);
    l.findStaticGetter(Test.class, "ourString", String.class);

    l.findGetter(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);
    l.findStaticGetter(Test.class, <warning descr="Cannot resolve field 'doesntExist'">"doesntExist"</warning>, String.class);

    l.findGetter(Test.class, "myInt", <warning descr="The type of field 'myInt' is 'int'">void.class</warning>);
    l.findGetter(Test.class, "myInts", <warning descr="The type of field 'myInts' is 'int[]'">int.class</warning>);
    l.findGetter(Test.class, "myString", <warning descr="The type of field 'myString' is 'java.lang.String'">List.class</warning>);

    l.findStaticGetter(Test.class, "ourInt", <warning descr="The type of field 'ourInt' is 'int'">void.class</warning>);
    l.findStaticGetter(Test.class, "ourInts", <warning descr="The type of field 'ourInts' is 'int[]'">int.class</warning>);
    l.findStaticGetter(Test.class, "ourString", <warning descr="The type of field 'ourString' is 'java.lang.String'">List.class</warning>);

    l.<warning descr="Field 'ourString' is static">findGetter</warning>(Test.class, "ourString", String.class);
    l.<warning descr="Field 'myString' is not static">findStaticGetter</warning>(Test.class, "myString", String.class);
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