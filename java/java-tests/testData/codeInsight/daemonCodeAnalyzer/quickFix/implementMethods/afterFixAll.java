// "Apply all 'Implement methods' fixes in file" "true"
interface I {
    int foo();
}
class A implements I {
    @Override
    public int foo() {
        return 0;
    }
}
class B implements I {
    @Override
    public int foo() {
        return 0;
    }
}
class C {
    I i = new I() {
        @Override
        public int foo() {
            return 0;
        }
    };
}