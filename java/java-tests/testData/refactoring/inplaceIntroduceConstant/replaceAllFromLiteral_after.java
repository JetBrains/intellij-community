class B {

    public static final int NINE = 9;

    public static void foo(boolean f) {
        Object[] objs = new Object[0];
        System.out.println(objs[NINE] != null ? Integer.valueOf(objs[NINE].toString()) : null);
    }
}