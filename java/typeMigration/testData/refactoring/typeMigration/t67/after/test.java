class Test {
    private String myForAccess;
    private String forAccess() {
        return myForAccess;
    }
    public void methMemAcc(String p) {
        p = this.forAccess();
    }
}