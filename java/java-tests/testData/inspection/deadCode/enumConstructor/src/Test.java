enum SomeEnum {
    A(1);

    int i;

    SomeEnum(int i) {
        this.i = i;
    }

    public static void main(String[] args) {
        System.out.println(A);
    }
}