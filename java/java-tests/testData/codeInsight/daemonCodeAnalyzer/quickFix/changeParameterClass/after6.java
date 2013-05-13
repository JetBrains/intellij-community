// "Make 'a' implement 'b'" "true"
class a implements b<String> {
    void f(b<String> r) {
        r.g(this);
    }

    public void g(b<String> t) {
        <caret>
    }
}
interface b<T> {
  void g(b<T> t);
}

