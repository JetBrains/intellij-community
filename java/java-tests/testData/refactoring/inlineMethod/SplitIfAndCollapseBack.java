// IDEA-305008
class X {
    public static String tr(String value) {
        if (hide() && non<caret>Null(value) && !value.isEmpty()) {
            if (value.length() >= 2) {
                return value.substring(0, 2) + "...";
            }
        }
        return value;
    }
    
    boolean nonNull(Object obj) {
        return obj != null;
    }
  
    private static boolean hide() {
        return Math.random() > 0.5;
    }
}