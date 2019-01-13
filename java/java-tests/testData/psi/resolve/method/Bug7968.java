
public class Bug7968 {
    public static void foo (String s, String s1, String s2, Object[] oa)
    {
        Bar.<caret>bar (s, s1, s2, oa);
    }
    class Bar
    {
        public static void bar (Object o, String s1, String s2, Object[] ao) {}
        public static void bar (String s, String s1, String s2, Object   o)  {}
        public static void bar (String s, String s1, String s2, Object[] ao) {}
    }

}
