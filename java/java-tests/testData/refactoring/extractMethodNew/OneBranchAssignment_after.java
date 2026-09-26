class A{
    public static Object test(Object a) {
        boolean value;

        if (a == null){
            value = newMethod();
        }
        else{
        }

        return Boolean.valueOf(value);
    }

    private static boolean newMethod() {
        boolean value;
        value = true;
        return value;
    }
}
