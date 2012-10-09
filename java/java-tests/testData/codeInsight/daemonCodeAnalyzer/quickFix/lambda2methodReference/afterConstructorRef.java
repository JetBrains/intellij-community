// "Replace lambda with method reference" "true"
class MyTest2<X> {
    MyTest2(X x) {
    }

    interface I<Z> {
        MyTest2<Z> m(Z z);
    }

    public static void main(String[] args) {
        I<String> s = MyTest2::new;
    }
}
