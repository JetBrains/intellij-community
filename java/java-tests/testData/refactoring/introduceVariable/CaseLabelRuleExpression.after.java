class A {
    private void A() {
        final int i = 2;
        System.out.println(switch (1) {
            case 1 -> 5;
            case i -> 7;
            default -> 9;
        });
    }
}