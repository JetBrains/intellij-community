class MyClass {
    public static void main(Class<? extends MyClass> clazz){
        clazz = (Class<? extends MyClass>) clazz.getSuperclass();
    }
}
