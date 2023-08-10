// "Fix all 'Using 'instanceof' with patterns' problems in file" "true"
import java.util.Map;
import javax.annotation.processing.Generated;

public class Simple{
    private static void testIfElseIfFalseIf(Object object) {
        if (!(object instanceof String)) {
            return;
        } else if ((object instanceof Number)) {
            final String txt = (String) object;
            Number n = (Number) object;
            System.out.println(txt);
            System.out.println(n);
        } else {
            final String txt = (String) object;
            System.out.println(txt);
        }
        final String txt = (String) object;
        System.out.println(txt);
    }

    private static void testIfElseIfTrueIf(Object object) {
        if (object instanceof String) {
            String txt = (String) object;
            System.out.println(txt);
        } else if (!(object instanceof Number)) {
        } else {
            Number n = (Number) object;
            System.out.println(n);
        }
    }

    private static void testIfElseFalse(Object object) {
        if (!(object instanceof String)) {
            return;
        } else {
            String txt = (String) object;
            System.out.println(txt);
        }
        String txt = (String) object;
        System.out.println(txt);
    }

    private static void testIfElseTrue(Object object) {
        if (object instanceof String && ((String) object).length() ==1) {
            String txt = (String) object;
            System.out.println(txt);
        } else {
            return;
        }
        String txt = (String) object;
        System.out.println(txt);
    }

    private static void testIfOutside(Object object) {
        if (!(object instanceof String)) {
            return;
        }
        String txt = (String) object;
        System.out.println(txt);
    }

    private static void testIfInsideEmptyBody(Object object) {
        if (object.hashCode()==1 && object instanceof String) {
            String txt = (String) object;
        }
    }
    private static void testIfInsideWithoutBlock(Object object) {
        if (object.hashCode()==1 && object instanceof String) {
            String txt = (String) object;
            System.out.println(txt);
        }
    }
    private static void testIfInsideEmpty(Object object) {
        if (object.hashCode()==1 && object instanceof String);
    }

    private static void testIfInside(Object object) {
        if (object.hashCode()==1 && object instanceof String) {
            String txt = (String) object;
            System.out.println(txt);
        }
    }

    private static void testIfInside2(Object object) {
        if (object.hashCode()==1 || object instanceof String) {
        }
    }

    private static void testIfInside3(Object object) {
        if (object.hashCode()==1 && object instanceof String txt && (txt="1").equals("1")) {
            System.out.println(txt);
        }
    }

    private static void testIfInside4(Object object) {
        if (!(object.hashCode() != 1 || !(object instanceof String))) {
            String txt = (String) object;
        }
    }

    private static void testWhileOuterBreak(Object object) {
        /*test1*/
        while (!(object instanceof /*test*/ String /*test2*/)){
            break;
        }
    }

    private static void testWhileOuterLast(Object object) {
        /*test1*/
        while (!(object instanceof /*test*/ String /*test2*/)){
        }
        String txt = (String) object;
    }

    private static void testWhileOuter(Object object) {
        /*test1*/
        while (!(object instanceof /*test*/ String /*test2*/)){
        }
        String txt = (String) object;
        System.out.println(txt);
    }

    private static void testWhileInnerBreak(Object object) {
        /*test1*/
        while (object instanceof /*test*/ String /*test2*/){
            String txt = (String) object;
            System.out.println(txt);
            break;
        }
    }

    private static void testWhileInnerEmptyBody(Object object) {
        /*test1*/
        while (object instanceof /*test*/ String /*test2*/){
            String txt = (String) object;
        }
    }

    private static void testWhileInnerEmpty(Object object) {
        /*test1*/
        while (object instanceof /*test*/ String /*test2*/);
    }

    private static void testWhileInner(Object object) {
        /*test1*/
        while (object instanceof /*test*/ String /*test2*/){
            String txt = (String) object;
        }
    }

    private static void testDoWhileInnerOuterBreak(Object object) {
        do {
            break;
        } while (!(object instanceof Object) || object.hashCode()==1);
    }
    private static void testDoWhileInnerOuterLast(Object object) {
        do {
        } while (!(object instanceof Object) || object.hashCode()==1);
        Object ob = object;
    }

    private static void testDoWhileInnerOuter(Object object) {
        do {
        } while (!(object instanceof Object) || object.hashCode()==1);
        Object ob = object;
        System.out.println(ob);
    }

    private static void testDoWhileInnerEmptyBody(Object object) {
        do {} while (object instanceof Object);
    }

    private static void testDoWhileInner(Object object) {
        do {
            System.out.println(1);
        } while (object instanceof Object);
    }

    private static void testForLoopOuterBreak(Object object) {
        for (int i = 0; (!(object instanceof String && ((String) object).length()==1) || i<1) || object.hashCode()==1; i++) {
            System.out.println(1);
            break;
        }
    }

    private static void testForLoopOuterLast(Object object) {
        for (int i = 0; (!(object instanceof String && ((String) object).length()==1) || i<1) || object.hashCode()==1; i++) {
            System.out.println(1);
        }
        String txt = (String) object;

    }

    private static void testForLoopOuter(Object object) {
        for (int i = 0; !(object instanceof String && ((String) object).length()==1) || i<1; i++) {
            System.out.println(1);
        }
        String txt = (String) object;
        System.out.println(txt);
    }

    private static void testForLoopInnerBreak(Object object) {
        for (int i = 0; object instanceof String && ((String) object).length()==1; i++) {
            String txt = (String) object;
            System.out.println(txt);
            break;
        }
    }

    private static void testForLoopInnerEmptyBody(Object object) {
        for (int i = 0; (object instanceof String); i++){
            String txt = (String) object;

        }
    }

    private static void testForLoopInnerEmpty(Object object) {
        for (int i = 0; (object instanceof String); i++);
    }

    private static void testForLoopInner(Object object) {
        for (int i = 0; (object instanceof String) && (i < 10) && (((String) object).length() > 1); i++) {
            String txt = (String) object;
            System.out.println(txt);
        }
    }

    private static void testForLoopInner1(Object object) {
        for (int i = 0; (object instanceof String txt) && (i < 10) && (txt.length() > 1) && (txt="1").equals("1"); i++) {
        }
    }

    private static void testForLoopInner2(Object object) {
        for (int i = 0; (object instanceof String txt) && (i < 10) && (txt.length() > 1); txt="1") {
            System.out.println(txt);
        }
    }

    private static Object testTernary(Object object) {
        return object instanceof String ? (String) object : "";
    }

    private static void testTernaryUnderCondition(Object obj) {
        if ("1".startsWith(obj instanceof String ? (String) obj : "")) {

        }
    }

    public Object remove(Object o) {
        if (!(o instanceof Map.Entry<?, ?>) || !(((Map.Entry<?, ?>) o).getKey() instanceof Integer)) return false;
        Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
        Integer key = (Integer) e.getKey();
        return e.getValue();
    }

    private static boolean assign(Object o){
        return obj instanceof String s && !(s = s.trim()).isEmpty() && s.contains("foo");
    }

    private static void withAnnotation(Object obj){
        if (obj instanceof String) {
            @Generated("") String s = (String) obj;
            System.out.println(s);
        }
    }

    private Object getObject() {
        return new Object();
    }

    private void testWithMethods() {
        if (getObject() instanceof String) {
            String s = (String) getObject();
            System.out.println(s);
        }
    }

  public static void ternary(Object object) {
    String result = object instanceof String ? (String) object : "1";
    String result2 = object instanceof String text ? text = "2" : "1";
  }
}