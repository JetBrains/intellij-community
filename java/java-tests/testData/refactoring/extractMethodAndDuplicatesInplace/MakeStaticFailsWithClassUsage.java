class X {
    void test() {
        <selection>Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(new Y());
            }
        };</selection>
        r.run();
    }

    class Y {
    }
}