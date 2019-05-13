// "Cast parameter to 'long'" "true"
class a {
    void f(Long l) {}
    void g() {
        f((long) 0);
    }
}

