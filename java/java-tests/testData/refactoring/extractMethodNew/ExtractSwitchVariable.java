class Test {
    void test(int y){
        <selection>int x;
        switch (y){
            case 3:
                x = 1;
                break;
            case 5:
                x = 2;
                break;
            default:
                return;
        }</selection>
        System.out.println(x);
    }
}