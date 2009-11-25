class Bar {
    void bar(Object[] components) {
        for (int i = 0; i < components.length; ++i)
            if (<selection>components[i]</selection> != null)
                addChildren(components[i]);
    }
}