// "Insert 'this();'" "true-preview"
import java.io.*;

class c {
 public c(int i) {
 }
}
class a extends c {
  a(int i) {
    super(i);
  }
 a() {
     this(<caret>);
 }
}

