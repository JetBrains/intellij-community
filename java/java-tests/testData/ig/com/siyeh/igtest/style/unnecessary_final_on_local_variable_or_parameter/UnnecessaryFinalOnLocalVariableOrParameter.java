package com.siyeh.igtest.style.unnecessary_final_on_local_variable_or_parameter;

import java.io.*;

public class UnnecessaryFinalOnLocalVariableOrParameter {
  class XX {
    XX(Object o) {}

    void foo(<warning descr="Unnecessary 'final' on parameter 'o'">final</warning> Object o) {
      new XX(o) {};
    }

    void m(final Object o) {
      new XX(null) {
        Object b = o;
      };
    }
    
    void fx(final Object o) {
      new XX(new XX(null) {
        Object b = o;
      });
    }

    void tryWithResources() throws IOException {
      try (<warning descr="Unnecessary 'final' on variable 'in'">final</warning> InputStream in = new FileInputStream("")){
        new Object() {{
          System.out.println(in);
        }};
      } catch (<warning descr="Unnecessary 'final' on parameter 'e'">final</warning> RuntimeException | AssertionError e) {
        class X {
          void m() {
            System.out.println(e);
          }
        }
      }
    }
  }
}
class Sample {

  public static void main(String[] args) {
    final int ALPHA_OPAQUE = (short) 0xFFFF; // IDEA suggests to remove final
    final int ALPHA_TRANSLUCENT = (short) 0; // IDEA suggests to remove final

    int size = 5;
    short[][] data = {
      new short[size],
      new short[size]
    };

    data[1][1] = ALPHA_TRANSLUCENT;
    data[1][1] = ALPHA_OPAQUE;
  }
}