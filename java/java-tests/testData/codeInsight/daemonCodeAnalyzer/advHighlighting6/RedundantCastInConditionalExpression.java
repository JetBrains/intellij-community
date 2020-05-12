class Foo {
    public static void main(String[] args) {
        final boolean b = false;
        final Integer i = (<warning descr="Casting '3' to 'Integer' is redundant">Integer</warning>)3;
        final short s = (short)(b ? i : (int)Integer.valueOf(3));
        System.out.println(s);
    }
}


class NonPrimitiveType {
    @SuppressWarnings("unchecked")
    public <T> void apply(Fun<Class<?>,  ?> defaultGetter, 
                          Class<T> configType, 
                          Fun<Class<T>, T> getter) {
        (getter == null ? (Fun)defaultGetter : getter).apply(configType);
    }
    
    interface Fun<T, R> {
        R apply(T t);
    }
}
