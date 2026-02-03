class Test {
    public void context1() {
        <selection>int i = 0, j = 0;
        int k = i + j;
        </selection>
        System.out.println(MessageFormat.format("i: {0}", i));
        System.out.println(MessageFormat.format("k: {0}", j));
    }
}