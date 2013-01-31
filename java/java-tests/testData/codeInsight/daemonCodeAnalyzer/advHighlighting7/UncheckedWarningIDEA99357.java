class Test {
    public static void main(String[] args) {
        Foo f = new Foo();
        System.out.println(f.get());
    }
}

class Foo<<warning descr="Type parameter 'T' is never used">T</warning>> {
    public <T1> T1 get() {
        return null;
    }
}