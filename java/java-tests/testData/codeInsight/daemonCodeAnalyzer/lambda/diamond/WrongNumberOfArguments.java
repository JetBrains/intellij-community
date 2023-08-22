class A<R>{
    A(R value) {}
    public static void main(String[] args) {
        A<Integer> a = new A<><error descr="'A(R)' in 'A' cannot be applied to '(java.lang.String, int)'" tooltip="Expected 1 arguments but found 2">("hi", 1)</error>;
    }
}