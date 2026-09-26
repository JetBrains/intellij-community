class C {
    void foo() {
        for(int i = 0; i < 10; i++){
            if (newMethod(i)) continue;
            System.out.println("");
        }
    }

    private boolean newMethod(int i) {
        if (i < 10) {
            return true;
        }
        return false;
    }

    {
        for(int i = 0; i < 10; i++){
            if (newMethod(i)) continue;
        }
    }
}