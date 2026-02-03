
interface X<T> { void m(T arg); }
interface Y<T> { void m(T arg); }
interface Z<T> extends X<T>, Y<T> {}

class App {
  public static void main(String[] args) {
    Z<String> z = (String s) -> System.out.println(s);
    z.m("Hello, world");
  }
}