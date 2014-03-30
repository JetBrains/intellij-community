interface I {
	void f();
}
interface II extends I {
    @Override
    void f();
}
class C implements I {
    @Override
    public void f() {

    }
}
