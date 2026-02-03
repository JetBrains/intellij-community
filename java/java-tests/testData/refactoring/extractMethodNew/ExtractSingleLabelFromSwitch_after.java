class Test {
    void test(int x) {
        switch (x){
            case 1:
                newMethod();
                break;
            case 2:
                System.out.println();
                return;
            default:
                break;
        }
    }

    private void newMethod() {
        System.out.println(1);
        return;
    }
}