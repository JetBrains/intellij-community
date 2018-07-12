
@interface Ann {}
interface I {
}

class IImpl implements I {
    public void m(@Ann String s) { }
}