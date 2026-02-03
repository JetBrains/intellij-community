class Super {
    boolean add(Integer key, String value) {
        System.out.println("0");
        return true;
    }
}


class Test extends Super {
    @Override
    boolean add(Integer key, String value) {
        System.out.println("1");
        return super.add(key, value);
    }

    void assign(Integer key, String value) {
        System.out.println("2");
        super.add(key, value);
    }
}
