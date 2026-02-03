class RedundantCastProblem {
    public abstract static class Top {
        public String f(Object o) {
            return "Top.f(Object)";
        }
    }
 
    public static class Sub extends Top {
        public String f(String s) {
            return "Middle.f(String)";
        }
    }
 
    public static void main(String[] args) {
        Sub sub = new Sub();
        String aString = "";
 
        System.out.println(((Top)sub).f(aString));
        System.out.println(sub.f(aString));
    }
}
