class Test4 {
    void test() {
        Runnable f = this::yyy;
    }
    
    String myField = "";
    void yy<caret>y() {
        System.out.println(myField);
    }
}