class Test {
    int test(int x){
        int y = 42;
        <selection>String out = "out";
        if (x > 10) return y;
        y = 55;
        if (x < 0) return y;</selection>
        System.out.println(out);
        return -1;
    }
}
