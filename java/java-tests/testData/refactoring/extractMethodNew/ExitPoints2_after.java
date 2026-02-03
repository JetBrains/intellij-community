class Test{
    public void foo() {
        if (cond1){
            if (newMethod()) return;
        }
        else if (cond3){
        }
        x();
    }

    private boolean newMethod() {
        if (cond2) {
            System.out.println();
            return true;
        }
        return false;
    }
}
