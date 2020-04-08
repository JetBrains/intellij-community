import org.jetbrains.annotations.NotNull;

class C {
    String method(Object o) {
        System.out.println(o);
        return newMethod(o);
    }

    @NotNull
    private String newMethod(Object o) {
        Integer i = new Integer(o.hashCode());
        return i.toString();
    }

    {
        String k;
        k = newMethod(Boolean.TRUE);
    }
}