class A {
    private void A() {
        final int i = 2;
        switch (1) {
            case 1 -> System.out.println(1);
            case i -> System.out.println(3);
        }
    }
}