class Test {
    void method(Object x) {
        String s = null;
        s = (String) x;
	toInline(s.length());
    }
    void toIn<caret>line(final int i) {
        Runnable r = new Runnable() {
            public void run() {
                System.out.println(i);
            }
        };
        System.out.println(i);
    }
}
