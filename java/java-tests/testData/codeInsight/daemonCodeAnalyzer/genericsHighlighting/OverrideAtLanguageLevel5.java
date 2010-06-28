interface I {
	void f();
}
interface II extends I {
    <error descr="@Override is not allowed when implementing interface method">@Override</error>
    void f();
}
class C implements I {
    <error descr="@Override is not allowed when implementing interface method">@Override</error>
    public void f() {
    }

    <error descr="Method does not override method from its superclass">@Override</error>
    public void notoverride() {
    }
}
