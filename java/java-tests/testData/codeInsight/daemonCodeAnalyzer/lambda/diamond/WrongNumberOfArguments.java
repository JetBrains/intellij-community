class A<R>{
    A(R value) {}
    public static void main(String[] args) {
        A<Integer> a = new A<><error descr="Expected 1 argument but found 2" tooltip="Expected 1 argument but found 2">("hi", 1)</error>;
    }
}