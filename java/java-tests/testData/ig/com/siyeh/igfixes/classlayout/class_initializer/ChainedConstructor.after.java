class ChainedConstructor {
    String s;
    String t;

    ChainedConstructor(String s) {
        this.s = s;
        System.out.println();
        t = "b";
    }

    ChainedConstructor() {
        this("a");
    }

}