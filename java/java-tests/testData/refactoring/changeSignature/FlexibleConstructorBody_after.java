class C {
    C(int i) {
    }
}

class C1 extends C {
    int i;
    C1(int i, int k) {
        System.out.println(i);
        super(0);
        this.i = i;
    }
}