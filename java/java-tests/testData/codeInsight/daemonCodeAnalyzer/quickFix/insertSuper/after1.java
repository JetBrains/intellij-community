// "Insert 'super();'" "true-preview"
import java.io.*;

class c {
 public c(int i) {
 }
}
class a extends c {
 a() {
     super(<caret>);
     int i = 9;
 }
}

