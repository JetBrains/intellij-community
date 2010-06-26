// "Make 'a' implement 'b'" "true"
class a implements b<String> {
    void f(b<String> r) {
        r.g(this);
    }

    public void g(b<String> t) {
        <caret><selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}
interface b<T> {
  void g(b<T> t);
}

