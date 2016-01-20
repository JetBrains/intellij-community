import javax.annotation.concurrent.GuardedBy;

import java.lang.String;

class A {

    @GuardedBy("itself")
    private String _foo;

    public String getFoo() {
        synchronized (_foo) {
            return _foo;
        }
    }

    public void setFoo(String foo) {
        <warning descr="Access to field '_foo' outside of declared guards">_foo</warning> = foo;
    }
}