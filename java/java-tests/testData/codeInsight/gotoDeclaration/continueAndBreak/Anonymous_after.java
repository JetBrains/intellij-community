class SomeClass {
        public static final Object OBJECT_OVERRIDDEN = new Object() {
                public String <caret>toString() {
                        return "";
                }
        };
        
        public String get() {
                return OBJECT_OVERRIDDEN.toString();
        }
}