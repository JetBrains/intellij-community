import org.jetbrains.annotations.Nullable;

class C {
    public Object m() {
        Object o = newMethod();
        if (o != null) return o;
        return null;
    }

    @Nullable
    private Object newMethod() {
        for (Object o : new ArrayList<Object>()) {
            if (o != null) {
                return o;
            }
        }
        return null;
    }
}