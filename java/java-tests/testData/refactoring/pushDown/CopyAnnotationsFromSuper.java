
@interface Ann {}
interface I {
    void <caret>m(@Ann String s);
}

class IImpl implements I {
    @Override
    public void m(String s) { }
}