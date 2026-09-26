class A {
    private void A() {
        System.out.println(switch (1) {
            case 1 -> 5;
            case <selection>2</selection> -> 7;
            default -> 9;
        });
    }
}