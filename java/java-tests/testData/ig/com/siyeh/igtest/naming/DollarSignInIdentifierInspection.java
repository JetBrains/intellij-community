package com.siyeh.igtest.naming;

public class DollarSignInIdentifierInspection {
    private int foo$;

    public int getFoo$() {
        return foo$;
    }

    public void setFoo$(int foo$) {
        this.foo$ = foo$;
    }
}
