// "Fix all 'Using 'instanceof' with patterns' problems in file" "true"
import java.util.Map;
import javax.annotation.processing.Generated;

public class Simple{
    private static void testIfElseIfFalseIf(Object object) {
        if (!(object instanceof final String <caret>txt)) {
            return;
        } else if ((object instanceof Number n)) {
            System.out.println(txt);
            System.out.println(n);
        } else {
            System.out.println(txt);
        }
        System.out.println(txt);
    }

    private static void testIfElseIfTrueIf(Object object) {
        if (object instanceof String txt) {
            System.out.println(txt);
        } else if (!(object instanceof Number n)) {
        } else {
            System.out.println(n);
        }
    }

    private static void testIfElseFalse(Object object) {
        if (!(object instanceof String txt)) {
            return;
        } else {
            System.out.println(txt);
        }
        System.out.println(txt);
    }

    private static void testIfElseTrue(Object object) {
        if (object instanceof String txt && txt.length() ==1) {
            System.out.println(txt);
        } else {
            return;
        }
        System.out.println(txt);
    }

    private static void testIfOutside(Object object) {
        if (!(object instanceof String txt)) {
            return;
        }
        System.out.println(txt);
    }

    private static void testIfInsideEmptyBody(Object object) {
        if (object.hashCode()==1 && object instanceof String txt) {
        }
    }
    private static void testIfInsideWithoutBlock(Object object) {
        if (object.hashCode()==1 && object instanceof String txt) System.out.println(txt);
    }
    private static void testIfInsideEmpty(Object object) {
        if (object.hashCode()==1 && object instanceof String txt);
    }

    private static void testIfInside(Object object) {
        if (object.hashCode()==1 && object instanceof String txt) {
            System.out.println(txt);
        }
    }

    private static void testIfInside2(Object object) {
        if (object.hashCode()==1 || object instanceof String txt) {
        }
    }

    private static void testIfInside3(Object object) {
        if (object.hashCode()==1 && object instanceof String txt && (txt="1").equals("1")) {
            System.out.println(txt);
        }
    }

    private static void testIfInside4(Object object) {
        if (!(object.hashCode() != 1 || !(object instanceof String txt))) {
        }
    }

    private static void testWhileOuterBreak(Object object) {
        while (!(object instanceof /*test*/ String /*test1*/ txt /*test2*/)){
            break;
        }
    }

    private static void testWhileOuterLast(Object object) {
        while (!(object instanceof /*test*/ String /*test1*/ txt /*test2*/)){
        }
    }

    private static void testWhileOuter(Object object) {
        while (!(object instanceof /*test*/ String /*test1*/ txt /*test2*/)){
        }
        System.out.println(txt);
    }

    private static void testWhileInnerBreak(Object object) {
        while (object instanceof /*test*/ String /*test1*/ txt /*test2*/){
            System.out.println(txt);
            break;
        }
    }

    private static void testWhileInnerEmptyBody(Object object) {
        while (object instanceof /*test*/ String /*test1*/ txt /*test2*/){}
    }

    private static void testWhileInnerEmpty(Object object) {
        while (object instanceof /*test*/ String /*test1*/ txt /*test2*/);
    }

    private static void testWhileInner(Object object) {
        while (object instanceof /*test*/ String /*test1*/ txt /*test2*/){
        }
    }

    private static void testDoWhileInnerOuterBreak(Object object) {
        do {
            break;
        } while (!(object instanceof Object ob) || ob.hashCode()==1);
    }
    private static void testDoWhileInnerOuterLast(Object object) {
        do {
        } while (!(object instanceof Object ob) || ob.hashCode()==1);
    }

    private static void testDoWhileInnerOuter(Object object) {
        do {
        } while (!(object instanceof Object ob) || ob.hashCode()==1);
        System.out.println(ob);
    }

    private static void testDoWhileInnerEmptyBody(Object object) {
        do {} while (object instanceof Object ob);
    }

    private static void testDoWhileInner(Object object) {
        do {
            System.out.println(1);
        } while (object instanceof Object ob);
    }

    private static void testForLoopOuterBreak(Object object) {
        for (int i = 0; (!(object instanceof String txt && txt.length()==1) || i<1) || object.hashCode()==1; i++) {
            System.out.println(1);
            break;
        }
    }

    private static void testForLoopOuterLast(Object object) {
        for (int i = 0; (!(object instanceof String txt && txt.length()==1) || i<1) || object.hashCode()==1; i++) {
            System.out.println(1);
        }

    }

    private static void testForLoopOuter(Object object) {
        for (int i = 0; !(object instanceof String txt && txt.length()==1) || i<1; i++) {
            System.out.println(1);
        }
        System.out.println(txt);
    }

    private static void testForLoopInnerBreak(Object object) {
        for (int i = 0; object instanceof String txt && txt.length()==1; i++) {
            System.out.println(txt);
            break;
        }
    }

    private static void testForLoopInnerEmptyBody(Object object) {
        for (int i = 0; (object instanceof String txt); i++){

        }
    }

    private static void testForLoopInnerEmpty(Object object) {
        for (int i = 0; (object instanceof String txt); i++);
    }

    private static void testForLoopInner(Object object) {
        for (int i = 0; (object instanceof String txt) && (i < 10) && (txt.length() > 1); i++) {
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
        return object instanceof String txt? txt : "";
    }

    private static void testTernaryUnderCondition(Object obj) {
        if ("1".startsWith(obj instanceof String txt ? txt : "")) {

        }
    }

    public Object remove(Object o) {
        if (!(o instanceof Map.Entry<?, ?> e) || !(e.getKey() instanceof Integer key)) return false;
        return e.getValue();
    }

    private static boolean assign(Object o){
        return obj instanceof String s && !(s = s.trim()).isEmpty() && s.contains("foo");
    }

    private static void withAnnotation(Object obj){
        if (obj instanceof @Generated("")String s) {
            System.out.println(s);
        }
    }

    private Object getObject() {
        return new Object();
    }

    private void testWithMethods() {
        if (getObject() instanceof String s) {
            System.out.println(s);
        }
    }

  public static void ternary(Object object) {
    String result = object instanceof String text ? text : "1";
    String result2 = object instanceof String text ? text = "2" : "1";
  }
}