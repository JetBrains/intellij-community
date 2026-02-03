import java.io.*;

class Foo {
  {
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        try {
                                            throw new IOException();
                                        } catch (IO<caret>) {

                                        }
                                    }
                                };

  }

}