class MyClass {
    public static void main(Class<? extends MyClass> clazz){
        clazz = (Class<? extends MyClass>) clazz.getSuperclass();
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? super capture<? extends MyClass>>>', required: 'java.lang.Class<? extends MyClass>'">clazz = clazz.getSuperclass()</error>;
    }
}
