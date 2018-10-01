class Y {
    int size = 4;
}

class Z extends Y {
    int size = 5;

    public static void main(String[] args) {
        Z z = new Z();
        System.out.println("z.size = " + ((Y)z).size);
    }
}