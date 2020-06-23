class C {
    String method(Object o) {
        System.out.println(o);
        <selection>Integer i = new Integer(o.hashCode());
        return i.toString();</selection>
    }

    {
        Integer j = new Integer(Boolean.TRUE.hashCode());
        String k = j.toString();
    }
}