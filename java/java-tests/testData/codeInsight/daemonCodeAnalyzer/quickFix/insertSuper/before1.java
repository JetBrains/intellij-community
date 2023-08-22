// "Insert 'super();'" "true-preview"
import java.io.*;

class c {
 public c(int i) {
 }
}
class a extends c {
 <caret>a() {
     int i = 9;
 }
}

