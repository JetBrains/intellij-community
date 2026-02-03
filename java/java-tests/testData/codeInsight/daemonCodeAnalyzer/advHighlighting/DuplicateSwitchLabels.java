import java.io.*;

class DuplicateSwitchLabels {
  final int FI = 4;

  void f(final int i) {
    switch (i) {
      <error descr="Duplicate default label">default</error>: break;
      case 1: break;
      <error descr="Duplicate default label">default</error>: break;
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
    switch (i) {
      case <error descr="Duplicate label '127'">b</error>:
        System.out.println("b=" + b + ";");
      case <error descr="Duplicate label '127'">127</error>:
        System.out.println("MySwitch.MySwitch");
    }

    switch (0) {
      case 0:
      case "\410" == "!0" ? 1 : 0:
      case ""==""+"" ? 3 : 0:
    }
  }
}