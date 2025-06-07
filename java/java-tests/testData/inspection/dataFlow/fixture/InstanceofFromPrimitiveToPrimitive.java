package dfa;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InstanceofFromPrimitiveToPrimitive {
    public static void main(String[] args) {
        System.out.println("testBoolean");
        testBoolean();
        System.out.println("testInt");
        testInt();
        System.out.println("testChar");
        testChar();
        System.out.println("testByte");
        testByte();
        System.out.println("testShort");
        testShort();
        System.out.println("testLong");
        testLong();
        System.out.println("testFloat");
        testFloat();
        System.out.println("testDouble");
        testDouble();
        System.out.println("testWithInference");
        testWithInference(new ArrayList<>());
    }

    private static void testWithInference(List<@NotNull Short> list) {
        if(<warning descr="Condition 'list.get(0) instanceof int' is always 'true'">list.get(0) instanceof int</warning>) { //redundant
        }
    }

    private static void testDouble() {
        System.out.println(0);
        double o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Float.MIN_VALUE");
        o = Float.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Float.MAX_VALUE");
        o = Float.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("Double.MIN_VALUE");
        o = Double.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Double.MAX_VALUE");
        o = Double.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p63");
        o = 0x1p63;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p5");
        o = 0x1p5;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p62");
        o = 0x1p62;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }


        System.out.println("nan");
        o = Double.NaN;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("float positive inf");
        o = Float.POSITIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("float negative inf");
        o = Float.NEGATIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("double positive inf");
        o = Double.POSITIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("double negative inf");
        o = Double.NEGATIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("-0.0");
        o = -0.0f;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) {//false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) {//false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) {//false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) {//false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testFloat() {
        System.out.println(0);
        float o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Float.MIN_VALUE");
        o = Float.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Float.MAX_VALUE");
        o = Float.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p63f");
        o = 0x1p63f;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p5f");
        o = 0x1p5f;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x1p62f");
        o = 0x1p62f;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("nan");
        o = Float.NaN;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("positive inf");
        o = Float.POSITIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("negative inf");
        o = Float.NEGATIVE_INFINITY;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("-0.0");
        o = -0.0f;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) {//false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) {//false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) {//false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) {//false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

      System.out.println("0x1p31f");
      o = 0x1p31f;
      if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) {//false
        System.out.println("int" + t);
      }
      if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) {//false
        System.out.println("char" + t);
      }
      if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) {//false
        System.out.println("byte" + t);
      }
      if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) {//false
        System.out.println("short" + t);
      }
      if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
        System.out.println("long" + t);
      }
      if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
        System.out.println("float" + t);
      }
      if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
        System.out.println("double" + t);
      }
    }

    private static void testLong() {
        System.out.println(0);
        long o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Long.MIN_VALUE");
        o = Long.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Long.MAX_VALUE");
        o = Long.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'false'">o instanceof double t</warning>) { //false
            System.out.println("double" + t);
        }

        System.out.println("Long.MAX_VALUE - (long)1e19");
        o = Long.MAX_VALUE - (long) 9.22e18;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("0x001fffffffffffffL");
        o = 0x001fffffffffffffL;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testShort() {
        System.out.println(0);

        short o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Short.MIN_VALUE");
        o = Short.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Short.MAX_VALUE");
        o = Short.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }


        System.out.println("Byte.MIN_VALUE");
        o = Byte.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Byte.MAX_VALUE");
        o = Byte.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testByte() {
        System.out.println(0);
        byte o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Byte.MIN_VALUE");
        o = Byte.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Byte.MAX_VALUE");
        o = Byte.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testChar() {
        System.out.println(0);
        char o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Character.MIN_VALUE");
        <warning descr="Variable is already assigned to this value">o</warning> = Character.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("Character.MAX_VALUE");
        o = Character.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testInt() {
        System.out.println(0);
        int o = 0;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'true'">o instanceof byte t</warning>) { //true
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
        System.out.println("shortMax");
        o = Short.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'true'">o instanceof short t</warning>) { //true
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
        System.out.println("intMax");
        o = Integer.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }

        System.out.println("intMax");
        o = Integer.MIN_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testBoolean() {
        System.out.println("true");
        boolean o = true;
        if (<warning descr="Condition 'o instanceof boolean b' is always 'true'">o instanceof boolean b</warning>) {
            System.out.println("boolean");
            System.out.println(b);
        }

        System.out.println("false");
        o = false;
        if (<warning descr="Condition 'o instanceof boolean' is always 'true'">o instanceof boolean</warning>) {
            System.out.println("boolean");
        }
    }
}
