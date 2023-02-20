import java.util.*;

public class ExtractMethodRecommender {
  List<List<String>> simpleWithPrecedingComment() {
    <weak_warning descr="Extract method returning 'list'">// Create list
    // Comment
    List<String> list = new ArrayList<>();
    list.add("one");
    list.add("two");
    list.add("three");
    list.add("four");</weak_warning>

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

    <weak_warning descr="Extract method returning 'list2'">List<String> list2 = new ArrayList<>();
    list2.add("v1");
    list2.add("v2");
    list2.add("v3");
    list2.add("v4");</weak_warning>
    list.add("five");
    return List.of(list, list2);
  }

  String grabReturnMaxArgs(int x, boolean a, boolean b, boolean c) {
    if (x > 100) {
      <weak_warning descr="Extract method returning 's'">String s = "Hello! ";
      s += x;
      s += a;
      return s;</weak_warning>
    }
    if (x > 50) {
      <weak_warning descr="Extract method returning 's'">String s = "Hello! ";
      s += x;
      s += a;
      s += b;
      return s;</weak_warning>
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
    <weak_warning descr="Extract method returning 's'">String s = "hello";
    s+=x;</weak_warning>
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
    <weak_warning descr="Extract method returning 's'">String s = "hello";
    s+=x;</weak_warning>
    System.out.println(s);
    System.out.println(x);
  }

  void setSequence() {
    <weak_warning descr="Extract method returning 'd'">Date d = new Date();
    d.setHours(12);
    d.setMinutes(34);
    d.setSeconds(56);</weak_warning>
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
}