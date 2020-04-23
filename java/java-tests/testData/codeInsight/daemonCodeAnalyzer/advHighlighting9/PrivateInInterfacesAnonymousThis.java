interface Foo {
    private void bar() {
        new Runnable() {
            @Override
            public void run() {
                System.out.println(Foo.this);
            }
        };
    }
    default void bar1() {
        new Runnable() {
            @Override
            public void run() {
                System.out.println(Foo.this);
            }
        };
    }
    static void bar2() {
        new Runnable() {
            @Override
            public void run() {
                System.out.println(<error descr="'Foo.this' cannot be referenced from a static context">Foo.this</error>);
            }
        };
    }
    int getX();
    int x = <error descr="'Foo.this' cannot be referenced from a static context">this</error>.getX();
}
