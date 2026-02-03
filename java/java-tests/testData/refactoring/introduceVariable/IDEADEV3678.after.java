class Bar {
    void bar(Object[] components) {
        for (int i = 0; i < components.length; ++i) {
            final Object component = components[i];
            if (component != null)
                addChildren(component);
        }
    }
}