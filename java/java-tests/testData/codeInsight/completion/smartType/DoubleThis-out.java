interface Aaa {}
interface Bbb {}

class Ccc implements Aaa, Bbb {
    void foo(Aaa a);
    void foo(Bbb b);

    {
        foo(this);<caret>
    }
}