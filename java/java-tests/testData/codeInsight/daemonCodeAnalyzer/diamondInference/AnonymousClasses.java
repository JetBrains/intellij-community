import java.io.IOException;
import java.util.*;

class Box<T> {
  Box() { }
  Box(T t) { }
  T get() { return null; }
  void accept(T t) { }
}

abstract class Shape<T> {
  abstract T area();
}

interface Visitor<R> {
  R visit(String s);
}

class Throwing<T> {
  Throwing(T t) throws IOException { }
}

class Hidden {
  private static class Secret { }
  static Secret make() { return new Secret(); }
}

class AnonymousClasses {
  private static class OwnSecret { }

  void inferredSupertypes() {
    Comparator<String> c1 = new Comparator<>() {
      public int compare(String a, String b) { return a.length() - b.length(); }
    };
    Comparator<? super Integer> c2 = new Comparator<>() {
      public int compare(Integer a, Integer b) { return a - b; }
    };
    var c3 = new Comparator<>() {
      public int compare(Object a, Object b) { return 0; }
    };
    Box<String> b1 = new Box<>("a") {
      @Override
      String get() { return "x"; }
      void accept(String s) { }
    };
    var b2 = new Box<>(1) { };
    Integer i = b2.get();
    Box<?> b3 = new Box<>() { };
    Box<? extends Number> b4 = new Box<>() {
      Number get() { return 1; }
    };
    Shape<Double> sh = new Shape<>() {
      Double area() { return 1.0; }
    };
    Shape<Double> shapeBad = new <error descr="Class 'Anonymous class derived from Shape' must implement abstract method 'area()' in 'Shape'">Shape<></error>() { };
    Visitor<Integer> v = new Visitor<>() {
      public Integer visit(String s) { return 0; }
    };
    Visitor<Integer> visitorBad = new Visitor<>() {
      public <error descr="'visit(String)' in 'Anonymous class derived from Visitor' clashes with 'visit(String)' in 'Visitor'; incompatible return type">String</error> visit(String s) { return ""; }
    };
  }

  void implicitOverride() {
    Box<String> overriding = new Box<>() {
      private void helper() { }
      private void accept(Integer i) { }
      @Override
      public String toString() { return ""; }
      String get() { return ""; }
      void accept(String s) { }
    };
    Box<String> extraMethod = new Box<error descr="Cannot use '<>' due to a non-private method which doesn't override or implement a method from a supertype"><></error>() {
      void extra() { }
    };
    Box<String> overloadedMethod = new Box<error descr="Cannot use '<>' due to a non-private method which doesn't override or implement a method from a supertype"><></error>() {
      void accept(Integer i) { }
    };
    Box<Integer> inferredFromTarget = new Box<error descr="Cannot use '<>' due to a non-private method which doesn't override or implement a method from a supertype"><></error>() {
      void accept(String s) { }
    };
    Box<String> staticMethod = new Box<error descr="Cannot use '<>' due to a non-private method which doesn't override or implement a method from a supertype"><></error>() {
      static void util() { }
    };
    Box<String> nonDiamond = new Box<String>() {
      void extra() { }
      void accept(Integer i) { }
    };
  }

  <T> void inferredTypeRestrictions(List<?> wildcard, T typeVariable, boolean cond) {
    new Box<>(wildcard.get(0)) { };
    new Box<error descr="Cannot use '<>' with anonymous inner classes"><></error>(wildcard) { };
    new Box<>(typeVariable) { };
    new Box<error descr="Cannot use '<>' with anonymous inner classes"><></error>(cond ? 1 : "a") { };
    new Box<>(new Object() { }) { };
    new Box<error descr="Cannot use '<>' with anonymous inner classes"><></error>(Hidden.make()) { };
    new Box<>(new OwnSecret()) { };
    new Box<>(new int[0]) { };
    new Box<>(wildcard.get(0));
    new Box<>(cond ? 1 : "a");
    new Box<>(Hidden.make());
  }
}
