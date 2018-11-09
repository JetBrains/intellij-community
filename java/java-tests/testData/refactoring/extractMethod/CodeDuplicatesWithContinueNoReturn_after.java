class C {
    void foo() {
        for(int i = 0; i < 10; i++){
            newMethod(i);
        }
    }

    private void newMethod(int i) {
        if (i < 10) {
            return;
        }
    }

    {
        for(int i = 0; i < 10; i++){
            newMethod(i);
        }
    }
}