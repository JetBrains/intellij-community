class MyClass {
    public <O> Class<O> getValueClass() {
        return null;
    }

    public boolean isBooleanClass() {
        return   (Class<?>) getValueClass() == Boolean.class;
    }
}