// "Make 'a' implement 'b'" "true-preview"
class a implements b<String> {
    void f(b<String> r) {
        r.g(this);
    }

    @Override
    public void g(b<String> t) {

    }
}
interface b<T> {
  void g(b<T> t);
}