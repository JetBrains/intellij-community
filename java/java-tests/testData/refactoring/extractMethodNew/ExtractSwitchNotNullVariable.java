class Test {
    void test(int y){
        <selection>String x;
        switch (y){
            case 3:
                x = "1";
                break;
            case 5:
                x = "2";
                break;
            default:
                throw new IllegalArgumentException();
        }</selection>
        System.out.println(x);
    }
}