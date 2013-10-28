class AnonArray {
    void x() {
        final int[] params = <flown11>{<flown111>-1};

        calcIt(new Runnable() {
            public void run() {
                params[0] = <flown12>z(<flown12111>2);
            }
        });
        y(<flown1>params[0]);
    }

    void y(int <caret>arg) {
    }

    void calcIt(Runnable r) {
        r.run();
    }

    int z(int <flown1211>arg) {
        return <flown121>arg;
    }
}
