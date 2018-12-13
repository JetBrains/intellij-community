class A {
    private void A(X x) {
        switch (x) {
            case A:
                System.out.println(1);
                break;
            case <selection>B</selection>:
                System.out.println(2);
                break;
        }

    }
}
enum X {A,B,C}