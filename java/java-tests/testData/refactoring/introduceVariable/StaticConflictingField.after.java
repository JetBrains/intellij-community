class XYZ {
    static int name;

    void method() {
        System.out.println(name);
        int name = 27;
        System.out.println(XYZ.name);
    }
}