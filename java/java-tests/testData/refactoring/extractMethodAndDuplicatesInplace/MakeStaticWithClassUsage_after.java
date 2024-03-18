import org.jetbrains.annotations.NotNull;

class X {
    void test() {
        Runnable r = getRunnable();
        r.run();
    }

    private static @NotNull Runnable getRunnable() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(new Y());
            }
        };
        return r;
    }

    static class Y {
    }
}