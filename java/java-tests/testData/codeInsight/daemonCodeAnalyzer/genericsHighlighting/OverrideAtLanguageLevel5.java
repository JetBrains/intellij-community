interface I {
	void f();
}
interface II extends I {
    <error descr="@Override in interfaces are not supported at language level '5'">@Override</error>
    void f();
}
class C implements I {
    <error descr="@Override in interfaces are not supported at language level '5'">@Override</error>
    public void f() {
    }

    <error descr="Method does not override method from its superclass">@Override</error>
    public void notoverride() {
    }
}
