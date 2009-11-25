class A {
    static {
        Runnable runnable = new Runnable() {
            public void run() {
            }
        };
        System.out.println(runnable);
    }
}