class C {
    String method(Object o) {
        System.out.println(o);
        <selection>Integer i = new Integer(o.hashCode());
        return i.toString();</selection>
    }

    {
        String k;
        Integer j = new Integer(Boolean.TRUE.hashCode());
        k = j.toString();
    }
}