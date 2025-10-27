class C {
    C<caret>() {
    }
}

class C1 extends C {
    int i;
    C1(int i, int k) {
        System.out.println(i);
        super();
        this.i = i;
    }
}