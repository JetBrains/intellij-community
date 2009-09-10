class C {
    String method(Object o) {
        System.out.println(o);
        return newMethod(o);
    }

    private String newMethod(Object o) {
        Integer i = new Integer(o.hashCode());
        return i.toString();
    }

    {
        String k = newMethod(Boolean.TRUE);
    }
}