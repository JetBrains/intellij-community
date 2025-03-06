import java.util.*;

public class ExtractMethodRecommender {
  List<List<String>> simpleWithPrecedingComment() {
    <weak_warning descr="It's possible to extract method returning 'list' from a long surrounding method">// Create list</weak_warning>
    // Comment
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    list.add("three");
    list.add("four");

    List<String> list2 = new ArrayList<>();
    list2.add("v1");
    list2.add("v2");
    list2.add("v3");
    list2.add("v4");
    return List.of(list, list2);
  }

  List<List<String>> avoidSplitting() {
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    list.add("three");
    list.add("four");

    <weak_warning descr="It's possible to extract method returning 'list2' from a long surrounding method">List<String> list2 = new ArrayList<>();</weak_warning>
    list2.add("v1");
    list2.add("v2");
    list2.add("v3");
    list2.add("v4");
    list.add("five");
    return List.of(list, list2);
  }

  String grabReturnMaxArgs(int x, boolean a, boolean b, boolean c) {
    if (x > 100) {
      <weak_warning descr="It's possible to extract method returning 's' from a long surrounding method">String s = "Hello! ";</weak_warning>
      s += x;
      s += a;
      return s;
    }
    if (x > 50) {
      <weak_warning descr="It's possible to extract method returning 's' from a long surrounding method">String s = "Hello! ";</weak_warning>
      s += x;
      s += a;
      s += b;
      return s;
    }
    if (x > 20) {
      String s = "Hello! ";
      s += x;
      s += a;
      s += b;
      s += c;
      return s;
    }
    return "Negative";
  }

  void dontGrabUnrelated(int x) {
    <weak_warning descr="It's possible to extract method returning 's' from a long surrounding method">String s = "hello";</weak_warning>
    s+=x;
    if (x < 0) {
      throw new IllegalArgumentException();
    }
    System.out.println(s);
    System.out.println(x);
  }

  void dontGrabUnrelated2(int x) {
    if (x < 0) {
      throw new IllegalArgumentException();
    }
    <weak_warning descr="It's possible to extract method returning 's' from a long surrounding method">String s = "hello";</weak_warning>
    s+=x;
    System.out.println(s);
    System.out.println(x);
  }

  void setSequence() {
    <weak_warning descr="It's possible to extract method returning 'd' from a long surrounding method">Date d = new Date();</weak_warning>
    d.setHours(12);
    d.setMinutes(34);
    d.setSeconds(56);
    System.out.println("Date is: ");
    System.out.println(d);
  }

  void setSequence2() {
    Date d = new Date();
    d.setHours(12);
    d.setMinutes(34);
    d.setSeconds(56);
    System.out.println(d);
  }

  String varAnonymousClass() {
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    var anon = new Runnable() {

      String result;

      public void run() {
        result = "Hello";
      }
    };
    anon.run();
    anon.run();
    return anon.result;
  }

  String varLocalClass() {
    class Local implements Runnable {
      String result;

      public void run() {
        result = "Hello";
      }
    }
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    var anon = new Local();
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    return anon.result;
  }

  static class Nested implements Runnable {
    String result;

    public void run() {
      result = "Hello";
    }
  }

  String varNestedClass() {
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    <weak_warning descr="It's possible to extract method returning 'anon' from a long surrounding method">var anon = new Nested();</weak_warning>
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    anon.run();
    return anon.result;
  }
}