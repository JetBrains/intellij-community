class A<T>{

    public void setClazz(Class<T> clazz) {
    }

    public void m(final A<?> a, Class<?> clazz) {
        a.setClazz<error descr="'setClazz(java.lang.Class<capture<?>>)' in 'A' cannot be applied to '(java.lang.Class<capture<?>>)'">(clazz)</error>;
    }
}