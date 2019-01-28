import a.A;
class ThisClass extends A {
    private void foo(A aa) {
        System.out.println(aa.constanta);
        System.out.println(new Inner());
    }

    public static void main(String[] args) {}
}