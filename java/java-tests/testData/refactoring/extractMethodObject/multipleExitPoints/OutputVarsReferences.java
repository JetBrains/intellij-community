class Test {
    public void context1() {
        <selection>int i = 1;
        i += 1;
        int k = 2;
        k++;</selection>
        System.out.println(MessageFormat.format("i: {0}", i));
        System.out.println(MessageFormat.format("k: {0}", k));
    }
}