class A {
    private int name;
    public void method() {
        System.out.println(name);
        int name = this.name;
        System.out.println(this.name);
    }
}