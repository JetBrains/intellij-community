class Test {
    public void test(int x , int y) {
        if (x > y){
            newMethod();
        }
        System.out.println();
    }

    private void newMethod() {
        System.out.println();
        throw new IllegalArgumentException();
    }
}