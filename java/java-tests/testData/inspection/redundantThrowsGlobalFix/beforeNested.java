// "FileNotFoundException" "true"

import java.io.FileNotFoundException;
import java.io.IOException;

class Main {
  public void f() throws <caret>FileNotFoundException {}

  {
    for (int i = 0; i < 10; i++)
      try {
        f();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }

    try {
      f();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } finally { }

    try () {
      f();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    try {
      try {
        f();
      } catch (UnsupportedOperationException e) {
        e.printStackTrace();
      }
    } catch (FileNotFoundException e) {
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

    try {
      f();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      new Main().f();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    try {
      new Main().f();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IndexOutOfBoundsException e) {
      e.printStackTrace();
    }

    try {
      new Main().f();
    } catch (FileNotFoundException | IndexOutOfBoundsException e) {
      e.printStackTrace();
    }

    try {
      new Main().f();
    } catch (IOException | FileNotFoundException | IndexOutOfBoundsException | NullPointerException e) {
      e.printStackTrace();
    }
  }
}