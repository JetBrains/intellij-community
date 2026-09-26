class Test {
    public void test(int x , int y) {
        if (x > y){
            <selection>System.out.println();
            throw new IllegalArgumentException();</selection>
        }
        System.out.println();
    }
}