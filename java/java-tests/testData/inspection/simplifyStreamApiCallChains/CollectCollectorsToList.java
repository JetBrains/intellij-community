import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {

  private List<Integer> list1 = ((((List<Integer>) ((true ? ((Stream.of(1, 2, 3).<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>)) : Arrays.asList(1, 2))))));
  private List<Integer> list2 = ((((List<Integer>) ((true ? ((Stream.of(1, 2, 3).collect(Collectors.toList()))) : Arrays.asList(1, 2))))));
  public List<Integer> list3 = ((((List<Integer>) ((true ? ((Stream.of(1, 2, 3).collect(Collectors.toList()))) : Arrays.asList(1, 2))))));

  void foo1() {
    List<Integer> list = Stream.of(1, 2, 3).<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>;
    int size = list.size();
    System.out.println(size);
    list.forEach(System.out::println);
    System.out.println(list.get(0));
  }

  void foo2(boolean flag) {
    List<Integer> list = ((((List<Integer>) ((flag ? ((Stream.of(1, 2, 3).<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>)) : Arrays.asList(1, 2))))));
    int size = list.size();
    System.out.println(size);
    list.forEach(System.out::println);
    System.out.println(list.get(0));
  }

  void foo3() {
    List<Integer> list = Stream.of(1, 2, 3).collect(Collectors.toList());
    list.add(1);
  }

  void foo4(boolean flag) {
    List<Integer> list = ((((List<Integer>) ((flag ? ((Stream.of(1, 2, 3).collect(Collectors.toList()))) : Arrays.asList(1, 2))))));
    list.add(1);
  }

  void foo5() {
    int size = list1.size();
    System.out.println(size);
    list1.forEach(System.out::println);
    System.out.println(list1.get(0));
  }

  void foo6() {
    list2.add(1);
  }
}

class ForeachStatementTest {
  void test(Stream<String> stream) {
    for (String s : stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>) {
      System.out.println(s);
    }
  }
}

class ComparisonTest {
  void test(Stream<String> stream) {
    assert stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning> != null;
  }
}

class ConcatenationTest {
  String test(Stream<String> stream) {
    return "foo" + stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning> + "bar";
  }
}

class AssertTest {
  void test(int answer, Stream<String> stream) {
    assert answer == 42 : stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>;
  }
}

class SynchronizedTest {
  void test(Stream<String> stream) {
    synchronized (stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>) {
      System.out.println("hello");
    }
  }
}

class InstanceofTest {
  void test(Stream<String> stream) {
    if (stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning> instanceof List) {
      System.out.println("hello");
    }
  }
}

class PositiveReferenceTest {
  boolean test(Stream<String> stream1, Stream<String> stream2) {
    return stream1.anyMatch(stream2.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>::contains);
  }
}

class NegativeReferenceTest {
  boolean test(Stream<String> stream1, Stream<String> stream2) {
    return stream1.anyMatch(stream2.collect(Collectors.toList())::remove);
  }
}

class AddToAnotherCollectionTest {
  void test(List<List<String>> list, Stream<String> stream) {
    // adding list to another collection could cause that list modification
    list.add(stream.collect(Collectors.toList()));
  }
}

class RemoveFromAnotherCollectionTest {
  void test(List<List<String>> list, Stream<String> stream) {
    list.remove(stream.<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>);
  }
}

class FooTest {
  void test() {
    Collections.addAll(Stream.of("foo", "bar").collect(Collectors.toList()), "baz");
  }

  void a(List<String> list) {
    Collections.copy(list, Stream.of("foo", "bar").<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>);
  }
  int b1(List<String> list) {
    return Collections.indexOfSubList(list, Stream.of("foo", "bar").<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>);
  }

  int b2(List<String> list) {
    return Collections.indexOfSubList(Stream.of("foo", "bar").<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>, list);
  }
}

class Test2 {
  public void bar() {
    Stream.of("foo", "bar").collect(Collectors.toList()).add("baz");
    Collections.checkedList(Stream.of("foo", "bar").<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>, String.class);
  }
}

class CollectionsUser {
  Supplier<List<String>> i() {
    return () -> Stream.of("foo", "bar").collect(Collectors.toList());
  }

  Supplier<List<String>> j() {
    return () -> {return Stream.of("foo", "bar").collect(Collectors.toList());};
  }

  interface Supplier<T> {
    T get();
  }
}

class TernaryTest {
  private final List<String> myList =  Stream.of("foo", "bar").collect(Collectors.toList());
  private final List<String> myList2 = Stream.of("foo", "bar").<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>;

  void add() {
    myList.add("foo");
  }

  List<String> get(boolean b) {
    return Collections.unmodifiableList(b ? myList : myList2);
  }
}

// IDEA-356315
class MissingIteratorRemoveAnalysis {
  public void problem() {
    final List<String> strings = Stream.of("anything", "at", "all")
      .collect(Collectors.toList());

    for(Iterator<String> iterator = strings.iterator(); iterator.hasNext(); ) {
      String string = iterator.next();
      iterator.remove();
    }
  }

  public void noRemove() {
    final List<String> strings = Stream.of("anything", "at", "all")
      .<warning descr="'collect(toList())' can be replaced with 'toList()'">collect(Collectors.toList())</warning>;

    for(Iterator<String> iterator = strings.iterator(); iterator.hasNext(); ) {
      String string = iterator.next();
      System.out.println(string);
    }
  }
}

//class TODO() {
//
//    void testMustHave1(Stream<String> stream) {
//        List<String> list1;
//        list1 = stream.collect(Collectors.toList());
//        list1.forEach(System.out::println);
//    }
//
//    void testMustHave2(Stream<String> stream) {
//        List<String> list1;
//        list1 = stream.collect(Collectors.toList());
//        list1.add("foo");
//    }
//
//    void test1(Stream<String> stream) {
//        List<String> list1;
//        List<String> list2;
//        list1 = ((list2 = ((stream.collect(Collectors.toList())))));
//        list1.forEach(System.out::println);
//        list2.forEach(System.out::println);
//    }
//
//    void test2(Stream<String> stream) {
//        List<String> list1;
//        List<String> list2;
//        list1 = ((list2 = ((stream.collect(Collectors.toList())))));
//        list1.add("foo");
//    }
//
//    void test3(Stream<String> stream) {
//        List<String> list1;
//        List<String> list2;
//        list1 = ((list2 = ((stream.collect(Collectors.toList())))));
//        list2.add("foo");
//    }
//
//    void test4(Stream<String> stream) {
//        List<String> list1;
//        list1 = stream.collect(Collectors.toList());
//        list1.add("foo");
//    }
//
//    void test5(Stream<String> stream) {
//        List<String> subList = stream.collect(Collectors.toList()).subList(0, 3);
//        subList.add("foo");
//    }
//
//    void test6(Stream<String> stream) {
//        List<String> subList = stream.collect(Collectors.toList()).subList(0, 3);
//        subList.forEach(System.out::println);
//    }

//    void test7(Stream<String> stream) {
//        List<String> list = stream.collect(Collectors.toList());
//        list.forEach(System.out::println);
//        list = new ArrayList<>();
//        list.add("foo");
//    }
//}