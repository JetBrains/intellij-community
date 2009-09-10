class Test {
    int method() {
        return newMethod();
        return 12;
    }

    private int newMethod() {
        try {
            if(cond1) return 0;
            else if(cond2) return 1;
            return 27;
        } finally {           
            doSomething();
        }
    }
}