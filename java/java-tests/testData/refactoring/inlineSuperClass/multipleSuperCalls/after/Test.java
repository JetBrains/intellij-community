class Super {
    boolean add(Integer key, String value) {
        System.out.println("0");
        return true;
    }
}


class Test {
    boolean add(Integer key, String value) {
        System.out.println("1");
        System.out.println("0");
        return true;
    }

    void assign(Integer key, String value) {
        System.out.println("2");
        System.out.println("0");
    }
}
