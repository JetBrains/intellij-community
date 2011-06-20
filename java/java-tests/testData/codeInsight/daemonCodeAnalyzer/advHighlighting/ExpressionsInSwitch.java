// constant expressions in switch

import java.util.Date;

class a {
 final int f = -3;

 void f1() {
        switch (0) {
        case <error descr="Constant expression required">new Integer(0).MAX_VALUE</error>:
        }
        int k=0;
        switch (0) {
        case <error descr="Constant expression required">false ? k : 0</error>:
        case <error descr="Constant expression required">true ? 1 : k</error>:
        }
        boolean b=true;
        switch (0) {
        case <error descr="Constant expression required">false && b ? 0 : 1</error>:
        case <error descr="Constant expression required">true || b ? 2 : 0</error>:
        }
        final Object obj="";
        switch (0) {
        case <error descr="Constant expression required">obj=="" ? 0 : 0</error>:
        case <error descr="Constant expression required">this.f</error>:
        }

    int i = 0;
    final Integer I = null;
    switch (0) {
      case <error descr="Constant expression required">i</error>:
      case <error descr="Constant expression required">I.MAX_VALUE</error>:
      case Integer.MAX_VALUE:
    }

 }

 static class b {
   static final int c = 8;
 }
 void cf1() {
    final int i = 9;
    switch (0) {
      case i:
      case 2+4:
      case f:
      case a.b.c:
    }
        switch (0) {
        case true ^ true ? 0 : 0:
        }

 }
}