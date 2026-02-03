class A {
    private int name;
    public void method() {
        System.out.println(name);
        String name = "xyz";
        System.out.println(this.name);
    }
}