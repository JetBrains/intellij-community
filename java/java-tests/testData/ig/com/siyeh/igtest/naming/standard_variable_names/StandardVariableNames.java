package com.siyeh.igtest.naming.standard_variable_names;
import java.io.*;
public class StandardVariableNames {

    void bad() {
        int <warning descr="Variable named 'c' doesn't have type 'char' or 'java.lang.Character'">c</warning> = 1;
        String <warning descr="Variable named 'ch' doesn't have type 'char' or 'java.lang.Character'">ch</warning> = "";
        float <warning descr="Variable named 'd' doesn't have type 'double' or 'java.lang.Double'">d</warning> = 1;
        double <warning descr="Variable named 'f' doesn't have type 'float' or 'java.lang.Float'">f</warning> = 1;
        Object <warning descr="Variable named 'i' doesn't have type 'int' or 'java.lang.Integer'">i</warning>, <warning descr="Variable named 'j' doesn't have type 'int' or 'java.lang.Integer'">j</warning>, <warning descr="Variable named 'k' doesn't have type 'int' or 'java.lang.Integer'">k</warning>, <warning descr="Variable named 'm' doesn't have type 'int' or 'java.lang.Integer'">m</warning>, <warning descr="Variable named 'n' doesn't have type 'int' or 'java.lang.Integer'">n</warning>;
        short <warning descr="Variable named 'l' doesn't have type 'long' or 'java.lang.Long'">l</warning>;
        char <warning descr="Variable named 's' doesn't have type 'java.lang.String'">s</warning>;
        char <warning descr="Variable named 'str' doesn't have type 'java.lang.String'">str</warning>;
    }

    void goo() {
        char c, ch;
        float f;
        double d;
        Integer i, j, k, m, n;
        long l;
        String s, str;

        new MyOutputStream() {
            // same as super
            public void write(int b) throws IOException {}
        };
    }


  public String toString(StandardVariableNames this) {
    return "x";
  }
}
interface MyOutputStream {
    void write(int <warning descr="Variable named 'b' doesn't have type 'byte' or 'java.lang.Byte'">b</warning>) throws IOException;
}