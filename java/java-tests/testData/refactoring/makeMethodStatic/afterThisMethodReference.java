class Test4 {
    void test() {
        Runnable f = Test4::yyy;
    }
    
    String myField = "";
    static void yyy() {
        System.out.println(myField);
    }
}