class A {
    private void A() {
        switch (1) {
            case 1 -> System.out.println(1);
            case <selection>2</selection> -> System.out.println(3);
        }
    }
}