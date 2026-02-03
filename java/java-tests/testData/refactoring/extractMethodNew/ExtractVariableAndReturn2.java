class Test {
    int test(int x){
        <selection>String out = "out";
        int internal = 42;
        if (x > 10) return internal;</selection>
        System.out.println(out);
        return -1;
    }
}
