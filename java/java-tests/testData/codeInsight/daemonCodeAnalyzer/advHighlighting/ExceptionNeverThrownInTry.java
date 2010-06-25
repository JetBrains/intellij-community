import java.io.*;
import java.sql.*;

////////////
class x {
  void f() {
    try {
      int i = 0;
    }
    catch (<error descr="Exception 'java.io.IOException' is never thrown in the corresponding try block">IOException e</error>) {
    }
  }
}