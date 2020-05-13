class C {
    public Object m() {
        <selection>for (Object o : new ArrayList<Object>()) {
            if (o != null) {
                return o;
            }
        }</selection>
        return null;
    }
}