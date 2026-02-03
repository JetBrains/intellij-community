class B {
    public static void foo(boolean f) {
        Object[] objs = new Object[0];
        System.out.println(objs[<caret>9] != null ? Integer.valueOf(objs[9].toString()) : null);
    }
}