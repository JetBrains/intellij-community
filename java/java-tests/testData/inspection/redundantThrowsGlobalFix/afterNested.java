// "FileNotFoundException" "true"

import java.io.FileNotFoundException;
import java.io.IOException;

class Main {
  public void f() {}

  {
      try {
          f();
      } finally {
      }

      try () {
          f();
      }

      try {
        f();
      } catch (UnsupportedOperationException e) {
        e.printStackTrace();
      }

      try {
      try {
        f();
      } catch (UnsupportedOperationException e) {
        e.printStackTrace();
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

      f();

      new Main().f();

      try {
          new Main().f();
      } catch (IndexOutOfBoundsException e) {
          e.printStackTrace();
      }

      try {
          new Main().f();
      } catch (IndexOutOfBoundsException e) {
          e.printStackTrace();
      }

      try {
          new Main().f();
      } catch (IndexOutOfBoundsException | NullPointerException e) {
          e.printStackTrace();
      }
  }
}