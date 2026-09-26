// "Change 'implements b' to 'extends b'" "true-preview"
import java.io.*;

class a implements <caret>b<String> {
}

class b<T> {}
