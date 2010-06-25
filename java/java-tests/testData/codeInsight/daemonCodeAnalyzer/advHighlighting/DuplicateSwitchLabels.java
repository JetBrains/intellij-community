// duplicate labels
import java.io.*;
import java.net.*;

public class a  {

  final int FI = 4;

  void f(final int i) {
    switch (i) {
      <error descr="Duplicate default label">default:</error> break;
      case 1: break;
      <error descr="Duplicate default label">default:</error> break;
    }

    switch (i) {
      case <error descr="Duplicate label '1'">1</error>: break;
      case <error descr="Duplicate label '1'">1</error>: break;
    }

    switch (i) {
      case <error descr="Duplicate label '1'">FI/2 - 1</error>: break;
      case <error descr="Duplicate label '1'">(1 + 35/16)%2</error>: break;
      case FI - 8: break;
    }

         final byte b = 127;

         switch(i) {
            case <error descr="Duplicate label '127'">b</error>:
               System.out.println("b=" + b + ";");
            case <error descr="Duplicate label '127'">127</error>:
               System.out.println("MySwitch.MySwitch");
         }


    // internalize strings
    switch (0) {
        case 0:
        case "\410" == "!0" ? 1 : 0:
        case ""==""+"" ? 3 : 0:
    }

        switch (0) {
        case 0:
        //case 1./0 == Double.POSITIVE_INFINITY ? 1 : 0:

        //case 1./0 == Float.POSITIVE_INFINITY ? 2 : 0:

        // commented out ref
        // does not work when running under JRE
        //case -1./0 == Double.NEGATIVE_INFINITY ? 3 : 0:
        //case -1./0 == Float.NEGATIVE_INFINITY ? 4 : 0:
        //case Double.POSITIVE_INFINITY == Float.POSITIVE_INFINITY ? 5 : 0:
        //case Double.NEGATIVE_INFINITY == Float.NEGATIVE_INFINITY ? 6 : 0:
        //case Double.NaN != Float.NaN ? 7 : 0:
        //case Integer.MIN_VALUE == -2.147483648e9 ? 8 : 0:
        //case Integer.MIN_VALUE == -2.14748365e9f ? 9 : 0:
        //case Long.MIN_VALUE == -9.223372036854776e18 ? 10 : 0:
        //case Long.MIN_VALUE == -9.223372e18f ? 11 : 0:



        }
  }
}