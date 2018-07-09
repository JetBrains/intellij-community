class ARM {
    void f() {
        System.out.println("before");
        {
            int i = 0;
            System.out.println("inside");
        }
        int i = 0;
    }
}