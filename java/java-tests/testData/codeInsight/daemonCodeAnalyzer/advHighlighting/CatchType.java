// exception java.lang.Exception has already been caught/ illegal catch type

import java.io.EOFException;
import java.io.IOException;
class Foo {
    void f() {
        try {
        } catch (Throwable t) {
        } catch (<error descr="Exception 'java.lang.Exception' has already been caught">Exception</error> e) {
        }
        try {
        } catch (RuntimeException e) {
        } catch (<error descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</error>  e) {
        }
        try {
            throw new EOFException();
        } catch (IOException e) {
        } catch (<error descr="Exception 'java.io.EOFException' has already been caught">EOFException</error>  e) {
        }

        try {
        }
        catch (Exception e) {
        }
        catch (<error descr="Exception 'java.lang.Exception' has already been caught">Exception</error> e) {
      
        }
    }


}

