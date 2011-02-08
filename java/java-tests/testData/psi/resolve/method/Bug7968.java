
/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.01.2003
 * Time: 14:46:09
 * To change this template use Options | File Templates.
 */
public class Bug7968 {
    public static void foo (String s, String s1, String s2, Object[] oa)
    {
        Bar.<ref>bar (s, s1, s2, oa);
    }
    class Bar
    {
        public static void bar (Object o, String s1, String s2, Object[] ao) {}
        public static void bar (String s, String s1, String s2, Object   o)  {}
        public static void bar (String s, String s1, String s2, Object[] ao) {}
    }

}
