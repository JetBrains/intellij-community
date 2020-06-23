class Test {
    void test(int x) {
        switch (x){
            <selection>case 1:
                System.out.println(1);
                break;</selection>
            case 2:
                System.out.println();
                return;
            default:
                break;
        }
    }
}