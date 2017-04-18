// "Create local variable 'xyz'" "true"
interface Other<T> {
  void add(int x, T y);
  void add(T y);
}
class A {
    public void foo(Other<String> other) {
        String xyz;
        other.add(xyz);
    }
}