class Test {
    void test(){
        int x = 55;
        if (newMethod(x)) return;
        System.out.println();
    }

    private boolean newMethod(int x) {
        switch (x){
            case 3:
                System.out.println();
                break;
            case 5:
                System.out.println("atata");
                break;
            default:
                return true;
        }
        return false;
    }
}