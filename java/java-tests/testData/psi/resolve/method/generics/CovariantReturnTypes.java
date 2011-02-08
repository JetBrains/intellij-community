interface F {
    Object o();
}
interface E extends F {
    String o();
}
interface B extends E,F { }

class User {
    void x(B a) {
        String o  = a.<ref>o();
    }
}