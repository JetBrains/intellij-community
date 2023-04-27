import org.jetbrains.annotations.NotNull;

class X {
    void test() {
        Runnable r = getRunnable();
        r.run();
    }

    @NotNull
    private Runnable getRunnable() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(new Y());
            }
        };
        return r;
    }

    class Y {
    }
}