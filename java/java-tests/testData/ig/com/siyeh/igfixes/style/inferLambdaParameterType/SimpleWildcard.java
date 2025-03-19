class Test1<U> {

    public static void main(String[] args) {
        Comparable<? extends Integer> c = <caret>o->{
            return 0;
        };
    }
}