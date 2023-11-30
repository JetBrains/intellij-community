import java.io.File;
import java.util.List;

class Test {
  void foo(List<String> files) {
      /*1*/
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      for (String file : files) {
          new File(file);/*6*/
      }
  }
}

