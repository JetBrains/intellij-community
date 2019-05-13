// "Insert 'this();'" "true"
import java.io.*;

class c {
 public c(int i) {
 }
}
class a extends c {
  a(int i) {
    super(i);
  }
 <caret>a() {
 }
}

