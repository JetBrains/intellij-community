class BBB {
    public BBB(String x) {
    }

    void foo(String s) {}
}

class AAA extends BBB {
    public AAA(String x) {
        super(test(x));
    }

    private static String t<caret>est(String x) {
        String y = x.trim();
        return y.length() > 0 ? y : "aaaa";
    }

    @Override
    void foo(String s) {
        super.foo(test(s));    //To change body of overridden methods use File | Settings | File Templates.
    }
}