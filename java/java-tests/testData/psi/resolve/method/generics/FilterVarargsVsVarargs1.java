class XY {
    public static Object getLocalizedObject(Object... objects) {
        System.out.println("in first");
        return null;
    }

    public static Object getLocalizedObject(Integer i, String string, Object... objects) {
        System.out.println("in second");
        return null;
    }

    public static void main(String[] args) {
            <ref>getLocalizedObject(null,   "");
    }
}